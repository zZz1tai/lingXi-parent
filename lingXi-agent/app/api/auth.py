"""Service-to-service authentication for protected Agent APIs."""

from __future__ import annotations

from hmac import compare_digest
from typing import Annotated

from fastapi import Security
from fastapi.security import APIKeyHeader

from app.config.settings import settings
from app.utils.exceptions import AgentError, ConfigurationError


SERVICE_KEY_HEADER = "X-Agent-Service-Key"
_service_key = APIKeyHeader(name=SERVICE_KEY_HEADER, auto_error=False)


async def require_service_auth(
    supplied_key: Annotated[str | None, Security(_service_key)] = None,
) -> None:
    """Require the Java-to-Python shared secret and fail closed if unset."""

    expected = settings.service_api_key_value
    if not expected:
        raise ConfigurationError("Agent service authentication is not configured")
    if supplied_key is None or not compare_digest(supplied_key, expected):
        raise AgentError("Unauthorized", code="UNAUTHORIZED", status_code=401)
