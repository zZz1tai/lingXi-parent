"""不依赖外部服务的安全通用助手工具。"""

from __future__ import annotations

import ast
import json
from datetime import date, datetime, timedelta
from decimal import ROUND_FLOOR, Decimal, DecimalException, localcontext
from typing import Any, Literal
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

from langchain.tools import ToolRuntime, tool
from langchain_core.tools import BaseTool
from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator

from app.agents.state import AgentContext

_MAX_ABSOLUTE_VALUE = Decimal("1e100")
_MAX_EXPRESSION_NODES = 64
_MAX_EXPRESSION_DEPTH = 16
_MAX_POWER = 100
_WEEKDAYS_ZH = ("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")

_TIMEZONE_ALIASES = {
    "北京时间": "Asia/Shanghai",
    "上海时间": "Asia/Shanghai",
    "中国标准时间": "Asia/Shanghai",
    "中国时间": "Asia/Shanghai",
    "协调世界时": "UTC",
}


class _GeneralToolInput(BaseModel):
    model_config = ConfigDict(
        arbitrary_types_allowed=True,
        extra="forbid",
        str_strip_whitespace=True,
    )


class CurrentDateTimeInput(_GeneralToolInput):
    timezone: str = Field(
        default="Asia/Shanghai",
        min_length=1,
        max_length=64,
        description=(
            "IANA timezone such as Asia/Shanghai, UTC, Asia/Tokyo, "
            "Europe/London, or America/New_York"
        ),
    )
    runtime: ToolRuntime[AgentContext]

    @field_validator("timezone")
    @classmethod
    def validate_timezone(cls, value: str) -> str:
        canonical = _TIMEZONE_ALIASES.get(value, value)
        if any(char in canonical for char in ("\r", "\n", "\x00")):
            raise ValueError("timezone must be single-line text")
        try:
            ZoneInfo(canonical)
        except (ZoneInfoNotFoundError, ValueError) as exc:
            raise ValueError("timezone must be a valid IANA timezone") from exc
        return canonical


class CalculatorInput(_GeneralToolInput):
    expression: str = Field(
        ...,
        min_length=1,
        max_length=200,
        description=(
            "Arithmetic expression using only numbers, parentheses, +, -, *, /, //, %, and **"
        ),
    )
    runtime: ToolRuntime[AgentContext]

    @field_validator("expression")
    @classmethod
    def validate_expression(cls, value: str) -> str:
        if any(char in value for char in ("\r", "\n", "\x00")):
            raise ValueError("expression must be single-line text")
        return value


class UnitConversionInput(_GeneralToolInput):
    value: Decimal = Field(
        ...,
        ge=-_MAX_ABSOLUTE_VALUE,
        le=_MAX_ABSOLUTE_VALUE,
        allow_inf_nan=False,
        description="Finite numeric value to convert",
    )
    from_unit: str = Field(..., min_length=1, max_length=32)
    to_unit: str = Field(..., min_length=1, max_length=32)
    runtime: ToolRuntime[AgentContext]

    @field_validator("from_unit", "to_unit")
    @classmethod
    def validate_unit(cls, value: str) -> str:
        if any(char in value for char in ("\r", "\n", "\x00")):
            raise ValueError("unit must be single-line text")
        return value


class DateCalculatorInput(_GeneralToolInput):
    operation: Literal["add_days", "days_between"] = Field(
        ...,
        description=(
            "add_days adds a signed number of days to start_date; "
            "days_between compares start_date and end_date"
        ),
    )
    start_date: date = Field(..., description="ISO date in YYYY-MM-DD format")
    days: int | None = Field(default=None, ge=-36_500, le=36_500)
    end_date: date | None = Field(
        default=None,
        description="Required for days_between, in YYYY-MM-DD format",
    )
    include_end: bool = Field(
        default=False,
        description="For days_between, include both boundary dates in the absolute count",
    )
    runtime: ToolRuntime[AgentContext]

    @model_validator(mode="after")
    def validate_operation_arguments(self) -> DateCalculatorInput:
        if self.operation == "add_days" and self.days is None:
            raise ValueError("days is required for add_days")
        if self.operation == "days_between" and self.end_date is None:
            raise ValueError("end_date is required for days_between")
        return self


# 所有非温度单位都换算到各自维度的基准单位。别名只用于解析，返回值始终使用规范符号。
_UNIT_DEFINITIONS: dict[str, tuple[str, Decimal, tuple[str, ...]]] = {
    "mm": ("length", Decimal("0.001"), ("millimeter", "millimeters", "毫米")),
    "cm": ("length", Decimal("0.01"), ("centimeter", "centimeters", "厘米")),
    "m": ("length", Decimal(1), ("meter", "meters", "metre", "metres", "米")),
    "km": (
        "length",
        Decimal(1000),
        ("kilometer", "kilometers", "kilometre", "kilometres", "千米", "公里"),
    ),
    "in": ("length", Decimal("0.0254"), ("inch", "inches", "英寸")),
    "ft": ("length", Decimal("0.3048"), ("foot", "feet", "英尺")),
    "yd": ("length", Decimal("0.9144"), ("yard", "yards", "码")),
    "mi": ("length", Decimal("1609.344"), ("mile", "miles", "英里")),
    "mg": ("mass", Decimal("0.001"), ("milligram", "milligrams", "毫克")),
    "g": ("mass", Decimal(1), ("gram", "grams", "克")),
    "kg": ("mass", Decimal(1000), ("kilogram", "kilograms", "千克", "公斤")),
    "t": ("mass", Decimal(1000000), ("tonne", "tonnes", "metricton", "吨")),
    "oz": ("mass", Decimal("28.349523125"), ("ounce", "ounces", "盎司")),
    "lb": ("mass", Decimal("453.59237"), ("pound", "pounds", "磅")),
    "ml": (
        "volume",
        Decimal("0.001"),
        ("milliliter", "milliliters", "millilitre", "millilitres", "毫升"),
    ),
    "l": ("volume", Decimal(1), ("liter", "liters", "litre", "litres", "升", "公升")),
    "m3": ("volume", Decimal(1000), ("m³", "cubicmeter", "cubicmeters", "立方米")),
    "tsp": ("volume", Decimal("0.00492892159375"), ("teaspoon", "teaspoons", "茶匙")),
    "tbsp": (
        "volume",
        Decimal("0.01478676478125"),
        ("tablespoon", "tablespoons", "汤匙"),
    ),
    "cup": ("volume", Decimal("0.2365882365"), ("cups", "美制杯")),
    "floz": (
        "volume",
        Decimal("0.0295735295625"),
        ("fl oz", "fluidounce", "fluidounces", "液量盎司"),
    ),
    "pt": ("volume", Decimal("0.473176473"), ("pint", "pints", "美制品脱")),
    "gal": ("volume", Decimal("3.785411784"), ("gallon", "gallons", "美制加仑")),
    "ms": ("time", Decimal("0.001"), ("millisecond", "milliseconds", "毫秒")),
    "s": ("time", Decimal(1), ("second", "seconds", "sec", "秒")),
    "min": ("time", Decimal(60), ("minute", "minutes", "分钟")),
    "h": ("time", Decimal(3600), ("hour", "hours", "hr", "小时")),
    "day": ("time", Decimal(86400), ("days", "天", "日")),
    "m2": ("area", Decimal(1), ("m²", "squaremeter", "squaremeters", "平方米", "平米")),
    "km2": (
        "area",
        Decimal(1000000),
        ("km²", "squarekilometer", "squarekilometers", "平方千米", "平方公里"),
    ),
    "ha": ("area", Decimal(10000), ("hectare", "hectares", "公顷")),
    "ft2": (
        "area",
        Decimal("0.09290304"),
        ("ft²", "squarefoot", "squarefeet", "平方英尺"),
    ),
    "acre": ("area", Decimal("4046.8564224"), ("acres", "英亩")),
    "m/s": (
        "speed",
        Decimal(1),
        ("mps", "meterpersecond", "meterspersecond", "米/秒", "米每秒"),
    ),
    "km/h": (
        "speed",
        Decimal("0.2777777777777777777777777778"),
        ("kph", "kmph", "千米/时", "公里/小时", "公里每小时"),
    ),
    "mph": ("speed", Decimal("0.44704"), ("mileperhour", "milesperhour", "英里/小时")),
    "kn": ("speed", Decimal("0.5144444444444444444444444444"), ("knot", "knots", "节")),
}

_TEMPERATURE_UNITS = {
    "c": ("celsius", "°c", "℃", "摄氏", "摄氏度"),
    "f": ("fahrenheit", "°f", "℉", "华氏", "华氏度"),
    "k": ("kelvin", "开尔文"),
}

_UNIT_ALIASES: dict[str, str] = {}
for _symbol, (_dimension, _factor, _aliases) in _UNIT_DEFINITIONS.items():
    for _alias in (_symbol, *_aliases):
        _UNIT_ALIASES[_alias.casefold().replace(" ", "")] = _symbol
for _symbol, _aliases in _TEMPERATURE_UNITS.items():
    for _alias in (_symbol, *_aliases):
        _UNIT_ALIASES[_alias.casefold().replace(" ", "")] = _symbol


def create_general_tools() -> list[BaseTool]:
    """创建始终可用、无外部副作用的通用工具。"""

    @tool(
        "get_current_datetime",
        args_schema=CurrentDateTimeInput,
        response_format="content_and_artifact",
        description=(
            "Get the exact current date, time, weekday, UTC offset, and timezone. "
            "Use when the user asks for the current time or another timezone's current time."
        ),
    )
    async def get_current_datetime(
        runtime: ToolRuntime[AgentContext],
        timezone: str = "Asia/Shanghai",
    ) -> tuple[str, dict[str, Any]]:
        _progress(runtime, "get_current_datetime", "started")
        zone = ZoneInfo(timezone)
        current = datetime.now(zone)
        data = {
            "timezone": zone.key,
            "datetime": current.isoformat(timespec="seconds"),
            "date": current.date().isoformat(),
            "time": current.time().isoformat(timespec="seconds"),
            "weekday": _WEEKDAYS_ZH[current.weekday()],
            "utc_offset": current.strftime("%z")[:3] + ":" + current.strftime("%z")[3:],
        }
        _progress(runtime, "get_current_datetime", "completed")
        return _tool_result("get_current_datetime", data)

    @tool(
        "calculate",
        args_schema=CalculatorInput,
        response_format="content_and_artifact",
        description=(
            "Safely evaluate bounded arithmetic with numbers, parentheses, +, -, *, /, //, %, "
            "and **. Never accepts code, variables, functions, or network input."
        ),
    )
    async def calculate(
        expression: str,
        runtime: ToolRuntime[AgentContext],
    ) -> tuple[str, dict[str, Any]]:
        _progress(runtime, "calculate", "started")
        result = _safe_calculate(expression)
        data = {"expression": expression, "result": _format_decimal(result)}
        _progress(runtime, "calculate", "completed")
        return _tool_result("calculate", data)

    @tool(
        "convert_units",
        args_schema=UnitConversionInput,
        response_format="content_and_artifact",
        description=(
            "Convert compatible length, mass, volume, time, area, speed, and temperature units. "
            "Supports common metric, US customary, English, and Chinese unit names; not currency."
        ),
    )
    async def convert_units(
        value: Decimal,
        from_unit: str,
        to_unit: str,
        runtime: ToolRuntime[AgentContext],
    ) -> tuple[str, dict[str, Any]]:
        _progress(runtime, "convert_units", "started")
        canonical_from = _canonical_unit(from_unit)
        canonical_to = _canonical_unit(to_unit)
        converted = _convert_value(value, canonical_from, canonical_to)
        data = {
            "input": {"value": _format_decimal(value), "unit": canonical_from},
            "output": {"value": _format_decimal(converted), "unit": canonical_to},
        }
        _progress(runtime, "convert_units", "completed")
        return _tool_result("convert_units", data)

    @tool(
        "date_calculator",
        args_schema=DateCalculatorInput,
        response_format="content_and_artifact",
        description=(
            "Add or subtract days from an ISO date, or calculate the signed and absolute "
            "difference between two ISO dates. Use for calendar arithmetic, not current time."
        ),
    )
    async def date_calculator(
        operation: Literal["add_days", "days_between"],
        start_date: date,
        runtime: ToolRuntime[AgentContext],
        days: int | None = None,
        end_date: date | None = None,
        include_end: bool = False,
    ) -> tuple[str, dict[str, Any]]:
        _progress(runtime, "date_calculator", "started")
        if operation == "add_days":
            if days is None:
                raise ValueError("add_days 缺少 days")
            result_date = start_date + timedelta(days=days)
            data = {
                "operation": operation,
                "start_date": start_date.isoformat(),
                "days": days,
                "result_date": result_date.isoformat(),
                "weekday": _WEEKDAYS_ZH[result_date.weekday()],
            }
        else:
            if end_date is None:
                raise ValueError("days_between 缺少 end_date")
            signed_days = (end_date - start_date).days
            absolute_days = abs(signed_days)
            data = {
                "operation": operation,
                "start_date": start_date.isoformat(),
                "end_date": end_date.isoformat(),
                "signed_days": signed_days,
                "absolute_days": absolute_days,
                "inclusive_days": absolute_days + 1 if include_end else absolute_days,
                "include_end": include_end,
            }
        _progress(runtime, "date_calculator", "completed")
        return _tool_result("date_calculator", data)

    return [get_current_datetime, calculate, convert_units, date_calculator]


def _progress(runtime: ToolRuntime[AgentContext], tool_name: str, status: str) -> None:
    runtime.stream_writer(
        {"type": "tool_progress", "tool": tool_name, "status": status}
    )


def _tool_result(tool_name: str, data: dict[str, Any]) -> tuple[str, dict[str, Any]]:
    content = json.dumps(data, ensure_ascii=False, separators=(",", ":"))
    return content, {"provider": "local", "tool": tool_name, "data": data}


def _safe_calculate(expression: str) -> Decimal:
    try:
        parsed = ast.parse(expression, mode="eval")
    except (SyntaxError, ValueError, RecursionError) as exc:
        raise ValueError("算式格式无效") from exc
    if sum(1 for _ in ast.walk(parsed)) > _MAX_EXPRESSION_NODES:
        raise ValueError("算式过于复杂")
    try:
        with localcontext() as context:
            context.prec = 50
            return _evaluate_node(parsed.body, depth=0)
    except (DecimalException, OverflowError, ZeroDivisionError) as exc:
        raise ValueError("算式无法在安全数值范围内计算") from exc


def _evaluate_node(node: ast.AST, *, depth: int) -> Decimal:
    if depth > _MAX_EXPRESSION_DEPTH:
        raise ValueError("算式嵌套过深")
    if isinstance(node, ast.Constant):
        if isinstance(node.value, bool) or not isinstance(node.value, (int, float)):
            raise ValueError("算式只能包含数字")  # noqa: TRY004 - 用户算式校验
        return _bounded_decimal(Decimal(str(node.value)))
    if isinstance(node, ast.UnaryOp) and isinstance(node.op, (ast.UAdd, ast.USub)):
        value = _evaluate_node(node.operand, depth=depth + 1)
        return _bounded_decimal(value if isinstance(node.op, ast.UAdd) else -value)
    if not isinstance(node, ast.BinOp) or not isinstance(
        node.op,
        (ast.Add, ast.Sub, ast.Mult, ast.Div, ast.FloorDiv, ast.Mod, ast.Pow),
    ):
        raise ValueError("算式包含不支持的内容")  # noqa: TRY004 - 用户算式校验

    left = _evaluate_node(node.left, depth=depth + 1)
    right = _evaluate_node(node.right, depth=depth + 1)
    if isinstance(node.op, ast.Add):
        result = left + right
    elif isinstance(node.op, ast.Sub):
        result = left - right
    elif isinstance(node.op, ast.Mult):
        result = left * right
    elif isinstance(node.op, ast.Div):
        if right == 0:
            raise ValueError("除数不能为零")
        result = left / right
    elif isinstance(node.op, ast.FloorDiv):
        if right == 0:
            raise ValueError("除数不能为零")
        result = (left / right).to_integral_value(rounding=ROUND_FLOOR)
    elif isinstance(node.op, ast.Mod):
        if right == 0:
            raise ValueError("除数不能为零")
        quotient = (left / right).to_integral_value(rounding=ROUND_FLOOR)
        result = left - right * quotient
    else:
        if right != right.to_integral_value() or abs(right) > _MAX_POWER:
            raise ValueError("指数必须是绝对值不超过 100 的整数")
        result = left ** int(right)
    return _bounded_decimal(result)


def _bounded_decimal(value: Decimal) -> Decimal:
    if not value.is_finite() or abs(value) > _MAX_ABSOLUTE_VALUE:
        raise ValueError("计算结果超出安全数值范围")
    return value


def _canonical_unit(value: str) -> str:
    canonical = _UNIT_ALIASES.get(value.strip().casefold().replace(" ", ""))
    if canonical is None:
        raise ValueError(f"不支持的单位：{value}")
    return canonical


def _convert_value(value: Decimal, from_unit: str, to_unit: str) -> Decimal:
    if from_unit in _TEMPERATURE_UNITS or to_unit in _TEMPERATURE_UNITS:
        if from_unit not in _TEMPERATURE_UNITS or to_unit not in _TEMPERATURE_UNITS:
            raise ValueError("温度只能与温度单位互相换算")
        return _bounded_decimal(_convert_temperature(value, from_unit, to_unit))

    from_dimension, from_factor, _ = _UNIT_DEFINITIONS[from_unit]
    to_dimension, to_factor, _ = _UNIT_DEFINITIONS[to_unit]
    if from_dimension != to_dimension:
        raise ValueError("两个单位不属于同一换算维度")
    with localcontext() as context:
        context.prec = 50
        return _bounded_decimal(value * from_factor / to_factor)


def _convert_temperature(value: Decimal, from_unit: str, to_unit: str) -> Decimal:
    with localcontext() as context:
        context.prec = 50
        if from_unit == "c":
            celsius = value
        elif from_unit == "f":
            celsius = (value - Decimal(32)) * Decimal(5) / Decimal(9)
        else:
            celsius = value - Decimal("273.15")
        if celsius < Decimal("-273.15"):
            raise ValueError("温度不能低于绝对零度")
        if to_unit == "c":
            return celsius
        if to_unit == "f":
            return celsius * Decimal(9) / Decimal(5) + Decimal(32)
        return celsius + Decimal("273.15")


def _format_decimal(value: Decimal) -> str:
    if value == 0:
        return "0"
    normalized = value.normalize()
    text = format(normalized, "f")
    return text if len(text) <= 160 else format(normalized, "E")
