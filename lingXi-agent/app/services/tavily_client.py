"""Tavily 客户端生命周期与显式代理策略。"""

from __future__ import annotations

from contextlib import asynccontextmanager
from typing import TYPE_CHECKING, AsyncIterator

import httpx

from app.config.settings import settings

if TYPE_CHECKING:
    from tavily import AsyncTavilyClient


@asynccontextmanager
async def tavily_client_lifespan(
    api_key: str | None = None,
) -> AsyncIterator["AsyncTavilyClient"]:
    """������������ϵͳ��������Ӱ��Ķ��������� Tavily �ͻ��ˡ�"""

    from tavily import AsyncTavilyClient

    resolved_key = (api_key or "").strip() or settings.tavily_api_key.strip()
    if not resolved_key:
        raise ValueError(
            "Tavily API key is not configured; set it on the Java security "
            "config page or via TAVILY_API_KEY"
        )

    proxy = settings.tavily_https_proxy_value or None
    client_kwargs: dict[str, object] = {
        "timeout": httpx.Timeout(settings.tool_timeout),
        "follow_redirects": False,
        "trust_env": settings.tavily_trust_env,
    }
    if proxy is not None:
        client_kwargs["proxy"] = proxy

    async with httpx.AsyncClient(**client_kwargs) as http_client:
        yield AsyncTavilyClient(
            api_key=resolved_key,
            client=http_client,
        )
