"""固定目的地、凭据安全且响应有界的 Java Tool Gateway 客户端。"""

from __future__ import annotations

import json
from dataclasses import dataclass
from typing import Any
from urllib.parse import urlsplit, urlunsplit

import httpx
from pydantic import BaseModel, ConfigDict, Field, model_validator

from app.config.settings import settings
from app.services.http_client import get_http_client
from app.utils.exceptions import ConfigurationError
from app.utils.logger import logger


class _StrictResponseModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class ToolMetadata(_StrictResponseModel):
    request_id: str = Field(..., min_length=1, max_length=128)
    tool: str = Field(..., min_length=1, max_length=64)
    elapsed_ms: int = Field(..., ge=0)
    generated_at: str = Field(..., min_length=1, max_length=128)
    permission_filtered: bool
    truncated: bool


class ToolErrorBody(_StrictResponseModel):
    code: str = Field(..., min_length=1, max_length=64)
    message: str = Field(..., min_length=1, max_length=512)
    retryable: bool


class ToolEnvelope(_StrictResponseModel):
    success: bool
    data: dict[str, Any] | None
    metadata: ToolMetadata
    error: ToolErrorBody | None

    @model_validator(mode="after")
    def validate_outcome(self) -> "ToolEnvelope":
        if self.success and (self.data is None or self.error is not None):
            raise ValueError("successful tool response must contain data only")
        if not self.success and (self.data is not None or self.error is None):
            raise ValueError("failed tool response must contain error only")
        return self


@dataclass(frozen=True, slots=True)
class ToolCallResult:
    data: dict[str, Any]
    metadata: ToolMetadata


class AgentToolClientError(Exception):
    """只暴露 Java 返回的稳定错误码和安全消息。"""

    def __init__(self, code: str, message: str, *, retryable: bool = False) -> None:
        self.code = code
        self.public_message = message
        self.retryable = retryable
        super().__init__(f"{code}: {message}")


class AgentToolClient:
    """调用配置固定的 Java 内部工具入口。"""

    def __init__(self, base_url: str) -> None:
        self._base_url = _validate_gateway_base_url(base_url)
        self._invoke_url = f"{self._base_url}/internal/ai/tools/invoke"

    async def invoke(
        self,
        *,
        tool: str,
        arguments: dict[str, Any],
        token: str,
        agent_request_id: str,
        thread_id: str,
    ) -> ToolCallResult:
        if not token or not agent_request_id or not thread_id:
            raise AgentToolClientError(
                "TOOL_UNAUTHORIZED",
                "当前对话没有可用的业务数据访问凭据",
            )
        payload = {
            "tool": tool,
            "arguments": arguments,
            "request_context": {
                "agent_request_id": agent_request_id,
                "thread_id": thread_id,
            },
        }
        headers = {
            "Authorization": f"Bearer {token}",
            "X-Agent-Request-Id": agent_request_id,
            "Accept": "application/json",
        }
        raw = bytearray()
        try:
            async with get_http_client().stream(
                "POST",
                self._invoke_url,
                json=payload,
                headers=headers,
                timeout=httpx.Timeout(settings.agent_tool_timeout_seconds),
            ) as response:
                content_length = response.headers.get("content-length")
                if content_length:
                    try:
                        declared_length = int(content_length)
                    except ValueError as exc:
                        raise AgentToolClientError(
                            "TOOL_INTERNAL_ERROR",
                            "业务数据服务返回了无效响应",
                        ) from exc
                    if declared_length > settings.agent_tool_max_response_bytes:
                        raise AgentToolClientError(
                            "TOOL_INTERNAL_ERROR",
                            "业务工具返回的数据超过安全上限",
                        )
                async for chunk in response.aiter_bytes():
                    if len(raw) + len(chunk) > settings.agent_tool_max_response_bytes:
                        raise AgentToolClientError(
                            "TOOL_INTERNAL_ERROR",
                            "业务工具返回的数据超过安全上限",
                        )
                    raw.extend(chunk)
                status_code = response.status_code
        except AgentToolClientError:
            raise
        except httpx.TimeoutException as exc:
            raise AgentToolClientError(
                "TOOL_TIMEOUT",
                "业务数据查询超时，请缩小范围或稍后重试",
                retryable=True,
            ) from exc
        except httpx.HTTPError as exc:
            logger.warning(
                "Java tool request failed | tool=%s | error_type=%s",
                tool,
                type(exc).__name__,
            )
            raise AgentToolClientError(
                "TOOL_INTERNAL_ERROR",
                "业务数据服务暂时不可用",
                retryable=True,
            ) from exc

        try:
            decoded = json.loads(raw.decode("utf-8"))
            envelope = ToolEnvelope.model_validate(decoded)
        except (UnicodeError, ValueError, TypeError) as exc:
            raise AgentToolClientError(
                "TOOL_INTERNAL_ERROR",
                "业务数据服务返回了无效响应",
                retryable=status_code >= 500,
            ) from exc

        if not envelope.success:
            assert envelope.error is not None
            raise AgentToolClientError(
                envelope.error.code,
                envelope.error.message,
                retryable=envelope.error.retryable,
            )
        if status_code < 200 or status_code >= 300:
            raise AgentToolClientError(
                "TOOL_INTERNAL_ERROR",
                "业务数据服务返回了异常状态",
                retryable=status_code >= 500,
            )
        assert envelope.data is not None
        return ToolCallResult(data=envelope.data, metadata=envelope.metadata)


def create_agent_tool_client() -> AgentToolClient | None:
    """按功能开关创建客户端；关闭时不向模型注册业务工具。"""
    if not settings.agent_tools_enabled:
        return None
    return AgentToolClient(settings.agent_tool_base_url)


def _validate_gateway_base_url(value: str) -> str:
    candidate = value.strip().rstrip("/")
    parsed = urlsplit(candidate)
    if parsed.scheme not in {"https", "http"} or not parsed.hostname:
        raise ConfigurationError("AGENT_TOOL_BASE_URL must be an absolute HTTP(S) URL")
    if parsed.username is not None or parsed.password is not None:
        raise ConfigurationError("AGENT_TOOL_BASE_URL must not contain credentials")
    if parsed.query or parsed.fragment or parsed.path not in {"", "/"}:
        raise ConfigurationError("AGENT_TOOL_BASE_URL must not contain path, query, or fragment")
    try:
        port = parsed.port
    except ValueError as exc:
        raise ConfigurationError("AGENT_TOOL_BASE_URL contains an invalid port") from exc
    host = parsed.hostname.lower().rstrip(".")
    authority = f"{host}:{port}" if port is not None else host
    allowlist = settings.agent_tool_host_allowlist
    if host not in allowlist and authority not in allowlist:
        raise ConfigurationError("Java Tool Gateway destination is not allowlisted")
    if parsed.scheme == "http" and not settings.agent_tool_allow_insecure_http:
        raise ConfigurationError("Java Tool Gateway must use HTTPS")
    return urlunsplit((parsed.scheme, authority, "", "", ""))
