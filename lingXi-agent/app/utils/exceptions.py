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
    ) -> None:
        self.message = message
        self.code = code
        self.status_code = status_code
        super().__init__(message)


# ── Concrete Exceptions ────────────────────────────────────────────────────

class ConfigurationError(AgentError):
    """Raised when required configuration is missing or invalid."""

    def __init__(self, message: str) -> None:
        super().__init__(message, code="CONFIG_ERROR", status_code=500)


class SearchError(AgentError):
    """Raised when the web search tool fails."""

    def __init__(self, message: str) -> None:
        super().__init__(message, code="SEARCH_ERROR", status_code=502)


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
        super().__init__(message, code="MODEL_UNAVAILABLE", status_code=503)


# ── Global Error Handlers ──────────────────────────────────────────────────

async def agent_error_handler(_request: Request, exc: AgentError) -> JSONResponse:
    """Handle all ``AgentError`` subclasses with a uniform JSON envelope."""
    return JSONResponse(
        status_code=exc.status_code,
        content={
            "success": False,
            "error": {
                "code": exc.code,
                "message": exc.message,
            },
        },
    )


async def generic_error_handler(_request: Request, exc: Exception) -> JSONResponse:
    """Catch-all handler — hides internal details from the client."""
    return JSONResponse(
        status_code=500,
        content={
            "success": False,
            "error": {
                "code": "INTERNAL_ERROR",
                "message": "An unexpected error occurred. Please try again later.",
            },
        },
    )


async def validation_exception_handler(
    _request: Request, exc: Exception
) -> JSONResponse:
    """Handle Pydantic / FastAPI validation errors."""
    return JSONResponse(
        status_code=422,
        content={
            "success": False,
            "error": {
                "code": "VALIDATION_ERROR",
                "message": str(exc),
            },
        },
    )
