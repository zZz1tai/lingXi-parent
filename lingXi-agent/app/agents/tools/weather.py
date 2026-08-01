"""固定 Open-Meteo 目的地、无需密钥的天气查询工具。"""

from __future__ import annotations

import asyncio
import json
import math
from typing import Any

import httpx
from langchain.tools import ToolRuntime, tool
from langchain_core.tools import BaseTool
from pydantic import BaseModel, ConfigDict, Field, field_validator

from app.agents.state import AgentContext
from app.config.settings import settings
from app.security.outbound import validate_outbound_http_url
from app.services.http_client import get_http_client
from app.utils.exceptions import ToolExecutionError

_WEATHER_HOSTS = {"geocoding-api.open-meteo.com", "api.open-meteo.com"}
_GEOCODING_URL = validate_outbound_http_url(
    "https://geocoding-api.open-meteo.com/v1/search",
    allowed_hosts=_WEATHER_HOSTS,
)
_FORECAST_URL = validate_outbound_http_url(
    "https://api.open-meteo.com/v1/forecast",
    allowed_hosts=_WEATHER_HOSTS,
)

_WEATHER_CONDITIONS = {
    0: "晴",
    1: "大部晴朗",
    2: "多云",
    3: "阴",
    45: "雾",
    48: "雾凇",
    51: "小毛毛雨",
    53: "毛毛雨",
    55: "强毛毛雨",
    56: "轻微冻毛毛雨",
    57: "冻毛毛雨",
    61: "小雨",
    63: "中雨",
    65: "大雨",
    66: "轻微冻雨",
    67: "冻雨",
    71: "小雪",
    73: "中雪",
    75: "大雪",
    77: "米雪",
    80: "小阵雨",
    81: "阵雨",
    82: "强阵雨",
    85: "小阵雪",
    86: "强阵雪",
    95: "雷暴",
    96: "雷暴伴小冰雹",
    99: "雷暴伴强冰雹",
}


class WeatherInput(BaseModel):
    model_config = ConfigDict(
        arbitrary_types_allowed=True,
        extra="forbid",
        str_strip_whitespace=True,
    )

    location: str = Field(
        ...,
        min_length=1,
        max_length=100,
        description=(
            "City or place name, preferably with province/state and country when ambiguous"
        ),
    )
    forecast_days: int = Field(
        default=3,
        ge=1,
        le=7,
        description="Number of forecast days including today",
    )
    runtime: ToolRuntime[AgentContext]

    @field_validator("location")
    @classmethod
    def validate_location(cls, value: str) -> str:
        if any(char in value for char in ("\r", "\n", "\x00")):
            raise ValueError("location must be single-line text")
        return value


def create_weather_tool() -> BaseTool:
    """创建只访问固定 Open-Meteo 主机的天气工具。"""

    @tool(
        "get_weather",
        args_schema=WeatherInput,
        response_format="content_and_artifact",
        description=(
            "Get current weather and a 1-7 day forecast for a public place using Open-Meteo. "
            "Use for current or forecast weather; include province/state and country when a "
            "place name is ambiguous. Never use it for internal device or customer locations."
        ),
    )
    async def get_weather(
        location: str,
        runtime: ToolRuntime[AgentContext],
        forecast_days: int = 3,
    ) -> tuple[str, dict[str, Any]]:
        _progress(runtime, "started")
        try:
            geocoding = await _fetch_json(
                _GEOCODING_URL,
                {
                    "name": location,
                    "count": 5,
                    "language": "zh",
                    "format": "json",
                },
            )
            place = _first_place(geocoding)
            forecast = await _fetch_json(
                _FORECAST_URL,
                {
                    "latitude": place["latitude"],
                    "longitude": place["longitude"],
                    "current": (
                        "temperature_2m,apparent_temperature,relative_humidity_2m,"
                        "precipitation,weather_code,wind_speed_10m"
                    ),
                    "daily": (
                        "weather_code,temperature_2m_max,temperature_2m_min,"
                        "precipitation_probability_max,sunrise,sunset"
                    ),
                    "temperature_unit": "celsius",
                    "wind_speed_unit": "kmh",
                    "precipitation_unit": "mm",
                    "timezone": "auto",
                    "forecast_days": forecast_days,
                },
            )
            data = _weather_payload(place, forecast, forecast_days)
            provider = "open-meteo"
            result_count = len(data["daily"])
        except ToolExecutionError as exc:
            if exc.code == "TOOL_LOCATION_NOT_FOUND":
                raise
            data = await _weather_search_fallback(location, forecast_days)
            provider = "tavily-weather-fallback"
            result_count = len(data["results"])
        except (TypeError, ValueError) as exc:
            raise ToolExecutionError(
                "Weather provider returned invalid forecast data",
                code="TOOL_WEATHER_UNAVAILABLE",
                public_message="天气服务返回内容不完整，请稍后再试",
            ) from exc
        _progress(runtime, "completed", result_count=result_count)
        content = json.dumps(data, ensure_ascii=False, separators=(",", ":"))
        return content, {
            "provider": provider,
            "tool": "get_weather",
            "query": location,
            "data": data,
        }

    return get_weather


async def _weather_search_fallback(
    location: str,
    forecast_days: int,
) -> dict[str, Any]:
    if not settings.tavily_api_key:
        raise ToolExecutionError(
            "No weather fallback provider is configured",
            code="TOOL_WEATHER_UNAVAILABLE",
            public_message="天气服务暂时不可用，请稍后再试",
        )

    from tavily import AsyncTavilyClient

    client = AsyncTavilyClient(api_key=settings.tavily_api_key)
    query = f"{location} 当前天气 未来{forecast_days}天天气预报"
    try:
        async with asyncio.timeout(settings.tool_timeout):
            payload = await client.search(
                query=query,
                topic="general",
                time_range="day",
                search_depth="basic",
                max_results=settings.search_max_results,
                include_answer=False,
                include_raw_content=False,
                timeout=float(settings.tool_timeout),
            )
    except (httpx.HTTPError, TimeoutError, TypeError, ValueError) as exc:
        raise ToolExecutionError(
            "Weather fallback search failed",
            code="TOOL_WEATHER_UNAVAILABLE",
            public_message="天气服务暂时不可用，请稍后再试",
        ) from exc
    finally:
        await client.close()

    raw_results = payload.get("results") if isinstance(payload, dict) else None
    results: list[dict[str, str]] = []
    if isinstance(raw_results, list):
        for item in raw_results[: settings.search_max_results]:
            if not isinstance(item, dict):
                continue
            results.append(
                {
                    "title": _safe_text(item.get("title"), 300),
                    "url": _safe_text(item.get("url"), 2_000),
                    "content": _safe_text(item.get("content"), 1_500),
                }
            )
    if not results:
        raise ToolExecutionError(
            "Weather fallback returned no results",
            code="TOOL_WEATHER_UNAVAILABLE",
            public_message="没有查到可核验的天气信息，请补充地点后重试",
        )
    return {
        "location_query": location,
        "forecast_days": forecast_days,
        "source_mode": "public_web_fallback",
        "notice": "结构化天气服务不可达，以下为公开网页的最新天气搜索结果",
        "results": results,
    }


async def _fetch_json(url: str, params: dict[str, Any]) -> dict[str, Any]:
    raw = bytearray()
    try:
        async with asyncio.timeout(settings.tool_timeout):
            async with get_http_client().stream(
                "GET",
                url,
                params=params,
                headers={"Accept": "application/json"},
            ) as response:
                response.raise_for_status()
                declared = response.headers.get("content-length")
                if declared and int(declared) > settings.weather_max_response_bytes:
                    raise ToolExecutionError(
                        "Weather response exceeds size limit",
                        code="TOOL_WEATHER_UNAVAILABLE",
                        public_message="天气服务返回内容异常，请稍后再试",
                    )
                async for chunk in response.aiter_bytes():
                    if len(raw) + len(chunk) > settings.weather_max_response_bytes:
                        raise ToolExecutionError(
                            "Weather response exceeds size limit",
                            code="TOOL_WEATHER_UNAVAILABLE",
                            public_message="天气服务返回内容异常，请稍后再试",
                        )
                    raw.extend(chunk)
        payload = json.loads(raw.decode("utf-8"))
        if not isinstance(payload, dict):
            raise ValueError(  # noqa: TRY004 - 上游 JSON 结构校验
                "weather response is not an object"
            )
        return payload
    except ToolExecutionError:
        raise
    except (httpx.HTTPError, TimeoutError, UnicodeError, ValueError) as exc:
        raise ToolExecutionError(
            "Weather provider request failed",
            code="TOOL_WEATHER_UNAVAILABLE",
            public_message="天气服务暂时不可用，请稍后再试",
        ) from exc


def _first_place(payload: dict[str, Any]) -> dict[str, Any]:
    results = payload.get("results")
    if not isinstance(results, list) or not results or not isinstance(results[0], dict):
        raise ToolExecutionError(
            "Weather location was not found",
            code="TOOL_LOCATION_NOT_FOUND",
            public_message="没有找到这个地点，请补充城市、地区或国家后重试",
            status_code=404,
        )
    place = results[0]
    return {
        "name": _safe_text(place.get("name"), 100),
        "admin1": _safe_text(place.get("admin1"), 100),
        "country": _safe_text(place.get("country"), 100),
        "latitude": _finite_number(place.get("latitude")),
        "longitude": _finite_number(place.get("longitude")),
    }


def _weather_payload(
    place: dict[str, Any],
    payload: dict[str, Any],
    forecast_days: int,
) -> dict[str, Any]:
    current = payload.get("current")
    daily = payload.get("daily")
    if not isinstance(current, dict) or not isinstance(daily, dict):
        raise ToolExecutionError(
            "Weather response is missing current or daily data",
            code="TOOL_WEATHER_UNAVAILABLE",
            public_message="天气服务返回内容不完整，请稍后再试",
        )

    current_code = _weather_code(current.get("weather_code"))
    dates = _list_value(daily, "time")
    count = min(forecast_days, len(dates))
    daily_rows: list[dict[str, Any]] = []
    for index in range(count):
        code = _weather_code(_list_item(daily, "weather_code", index))
        daily_rows.append(
            {
                "date": _safe_text(dates[index], 32),
                "condition": _condition(code),
                "weather_code": code,
                "temperature_max_c": _finite_number(
                    _list_item(daily, "temperature_2m_max", index)
                ),
                "temperature_min_c": _finite_number(
                    _list_item(daily, "temperature_2m_min", index)
                ),
                "precipitation_probability_percent": _finite_number(
                    _list_item(daily, "precipitation_probability_max", index)
                ),
                "sunrise": _safe_text(_list_item(daily, "sunrise", index), 40),
                "sunset": _safe_text(_list_item(daily, "sunset", index), 40),
            }
        )
    if not daily_rows:
        raise ToolExecutionError(
            "Weather response contains no forecast days",
            code="TOOL_WEATHER_UNAVAILABLE",
            public_message="天气服务暂时没有可用预报，请稍后再试",
        )

    location_parts = []
    for key in ("name", "admin1", "country"):
        value = str(place.get(key) or "")
        if value and value not in location_parts:
            location_parts.append(value)
    return {
        "location": "，".join(location_parts),
        "timezone": _safe_text(payload.get("timezone"), 64),
        "current": {
            "observed_at": _safe_text(current.get("time"), 40),
            "condition": _condition(current_code),
            "weather_code": current_code,
            "temperature_c": _finite_number(current.get("temperature_2m")),
            "apparent_temperature_c": _finite_number(
                current.get("apparent_temperature")
            ),
            "relative_humidity_percent": _finite_number(
                current.get("relative_humidity_2m")
            ),
            "precipitation_mm": _finite_number(current.get("precipitation")),
            "wind_speed_kmh": _finite_number(current.get("wind_speed_10m")),
        },
        "daily": daily_rows,
    }


def _progress(
    status_runtime: ToolRuntime[AgentContext], status: str, **data: Any
) -> None:
    status_runtime.stream_writer(
        {
            "type": "tool_progress",
            "tool": "get_weather",
            "status": status,
            **data,
        }
    )


def _condition(code: int) -> str:
    return _WEATHER_CONDITIONS.get(code, "未知天气")


def _weather_code(value: Any) -> int:
    number = _finite_number(value)
    code = int(number)
    if number != code or code < 0 or code > 999:
        raise ValueError("invalid weather code")
    return code


def _finite_number(value: Any) -> int | float:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ValueError("weather value is not numeric")  # noqa: TRY004 - 上游数据校验
    number = float(value)
    if not math.isfinite(number):
        raise ValueError("weather value is not finite")
    return int(number) if number.is_integer() else round(number, 4)


def _list_value(payload: dict[str, Any], key: str) -> list[Any]:
    value = payload.get(key)
    if not isinstance(value, list):
        raise ValueError(  # noqa: TRY004 - 上游 JSON 结构校验
            f"weather field {key} is not a list"
        )
    return value


def _list_item(payload: dict[str, Any], key: str, index: int) -> Any:
    values = _list_value(payload, key)
    if index >= len(values):
        raise ValueError(f"weather field {key} is shorter than expected")
    return values[index]


def _safe_text(value: Any, max_length: int) -> str:
    text = str(value or "").replace("\r", " ").replace("\n", " ").replace("\x00", " ")
    return " ".join(text.split())[:max_length]
