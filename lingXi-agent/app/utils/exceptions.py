"""
Custom exception hierarchy and global FastAPI error handlers.

All business exceptions inherit from ``AgentError`` so that the
global handler can return a uniform JSON error envelope.
"""

from __future__ import annotations

from fastapi import Request
from fastapi.responses import JSONResponse


# ── Base Exception ──────────────────────────────────────────────────────────

class AgentError(Exception):
    """Base exception for all application-level errors."""

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


# ── Concrete Exceptions ────────────────────────────────────────────────────

class ConfigurationError(AgentError):
    """Raised when required configuration is missing or invalid."""

    def __init__(self, message: str) -> None:
        super().__init__(
            message,
            code="CONFIG_ERROR",
            status_code=500,
            public_message="Agent service configuration is invalid",
        )


class SearchError(AgentError):
    """Raised when the web search tool fails."""

    def __init__(self, message: str) -> None:
        super().__init__(
            message,
            code="SEARCH_ERROR",
            status_code=502,
            public_message="The upstream search service failed",
        )


class AgentTimeoutError(AgentError):
    """Raised when the agent exceeds its maximum iteration limit."""

    def __init__(self, message: str = "Agent exceeded maximum iterations") -> None:
        super().__init__(message, code="AGENT_TIMEOUT", status_code=504)


class InputValidationError(AgentError):
    """Raised when request input fails validation."""

    def __init__(self, message: str) -> None:
        super().__init__(message, code="VALIDATION_ERROR", status_code=422)


class ModelNotAvailableError(AgentError):
    """Raised when the LLM model is unreachable."""

    def __init__(self, message: str) -> None:
        super().__init__(
            message,
            code="MODEL_UNAVAILABLE",
            status_code=503,
            public_message="The configured model is temporarily unavailable",
        )


# ── Global Error Handlers ──────────────────────────────────────────────────

async def agent_error_handler(_request: Request, exc: AgentError) -> JSONResponse:
    """Handle all ``AgentError`` subclasses with a uniform JSON envelope."""
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
    """Catch-all handler — hides internal details from the client."""
    from app.utils.logger import logger
    # Provider exceptions can embed response bodies, headers, or credentials in
    # their rendered traceback. Keep production logs useful without serializing
    # attacker/provider-controlled exception text.
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
    """Handle Pydantic / FastAPI validation errors."""
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
