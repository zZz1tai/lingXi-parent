"""
自定义异常层次结构和全局 FastAPI 错误处理程序。

所有业务异常都继承自 ``AgentError``，以便全局处理程序
可以返回统一的 JSON 错误信封。
"""

from __future__ import annotations

from fastapi import Request
from fastapi.responses import JSONResponse


# ── 基础异常 ────────────────────────────────────────────────────────────────

class AgentError(Exception):
    """所有应用级错误的基类异常。"""

    def __init__(
        self,
        message: str,
        code: str = "AGENT_ERROR",
        status_code: int = 500,
        public_message: str | None = None,
    ) -> None:
        self.message = message
        self.code = code
        self.status_code = status_code
        self.public_message = public_message or message
        super().__init__(message)


# ── 具体业务异常 ────────────────────────────────────────────────────────────

class ConfigurationError(AgentError):
    """当必需的配置缺失或无效时引发。"""

    def __init__(self, message: str) -> None:
        super().__init__(
            message,
            code="CONFIG_ERROR",
            status_code=500,
            public_message="Agent service configuration is invalid",
        )


class SearchError(AgentError):
    """当网络搜索工具失败时引发。"""

    def __init__(self, message: str) -> None:
        super().__init__(
            message,
            code="SEARCH_ERROR",
            status_code=502,
            public_message="The upstream search service failed",
        )


class AgentTimeoutError(AgentError):
    """当智能体超过最大迭代次数限制时引发。"""

    def __init__(self, message: str = "Agent exceeded maximum iterations") -> None:
        super().__init__(message, code="AGENT_TIMEOUT", status_code=504)


class InputValidationError(AgentError):
    """当请求输入验证失败时引发。"""

    def __init__(self, message: str) -> None:
        super().__init__(message, code="VALIDATION_ERROR", status_code=422)


class ModelNotAvailableError(AgentError):
    """当 LLM 模型不可达时引发。"""

    def __init__(self, message: str) -> None:
        super().__init__(
            message,
            code="MODEL_UNAVAILABLE",
            status_code=503,
            public_message="The configured model is temporarily unavailable",
        )


# ── 全局错误处理器 ──────────────────────────────────────────────────────────

async def agent_error_handler(_request: Request, exc: AgentError) -> JSONResponse:
    """使用统一的 JSON 信封处理所有 ``AgentError`` 子类。"""
    return JSONResponse(
        status_code=exc.status_code,
        content={
            "success": False,
            "error": {
                "code": exc.code,
                "message": exc.public_message,
            },
            "request_id": _request_id(),
        },
    )


async def generic_error_handler(_request: Request, exc: Exception) -> JSONResponse:
    """全局兜底处理程序——对客户端隐藏内部细节。"""
    from app.utils.logger import logger
    # 模型提供方异常的堆栈可能包含响应体、请求头或凭据。
    # 生产日志只记录可控的异常类型，避免序列化由攻击者或提供方控制的文本。
    logger.error("Unhandled exception | error_type=%s", type(exc).__name__)
    return JSONResponse(
        status_code=500,
        content={
            "success": False,
            "error": {
                "code": "INTERNAL_ERROR",
                "message": "An unexpected error occurred. Please try again later.",
            },
            "request_id": _request_id(),
        },
    )


async def validation_exception_handler(
    _request: Request, exc: Exception
) -> JSONResponse:
    """处理 Pydantic / FastAPI 验证错误。"""
    details: list[str] = []
    errors = getattr(exc, "errors", None)
    if callable(errors):
        try:
            entries = errors(include_input=False)
        except TypeError:
            entries = errors()
        for entry in entries:
            location = ".".join(str(part) for part in entry.get("loc", ()))
            message = str(entry.get("msg", "Invalid value"))
            details.append(f"{location}: {message}" if location else message)

    return JSONResponse(
        status_code=422,
        content={
            "success": False,
            "error": {
                "code": "VALIDATION_ERROR",
                "message": "; ".join(details) or "Request validation failed",
            },
            "request_id": _request_id(),
        },
    )


def _request_id() -> str:
    from app.utils.logger import get_request_id

    return get_request_id()
