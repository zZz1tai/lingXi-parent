"""Application-lifetime HTTP client for provider calls."""

from __future__ import annotations

import httpx

from app.config.settings import settings
from app.utils.exceptions import ConfigurationError


_client: httpx.AsyncClient | None = None


async def initialize_http_client() -> httpx.AsyncClient:
    """Create the shared provider client once per application worker."""
    global _client
    if _client is None or _client.is_closed:
        _client = httpx.AsyncClient(
            timeout=httpx.Timeout(settings.provider_http_timeout_seconds),
            limits=httpx.Limits(
                max_connections=settings.provider_http_max_connections,
                max_keepalive_connections=settings.provider_http_max_keepalive_connections,
            ),
            follow_redirects=False,
        )
    return _client


def get_http_client() -> httpx.AsyncClient:
    """Return the initialized provider client for FastAPI dependencies."""
    if _client is None or _client.is_closed:
        raise ConfigurationError("Provider HTTP client is not initialized")
    return _client


def http_client_ready() -> bool:
    """Return whether the application-lifetime client is usable."""
    return _client is not None and not _client.is_closed


async def close_http_client() -> None:
    """Close the shared provider connection pool."""
    global _client
    if _client is not None and not _client.is_closed:
        await _client.aclose()
    _client = None
