"""Tavily 客户端生命周期与显式代理策略。"""

from __future__ import annotations

from contextlib import asynccontextmanager
from typing import TYPE_CHECKING, AsyncIterator

import httpx

from app.config.settings import settings

if TYPE_CHECKING:
    from tavily import AsyncTavilyClient


@asynccontextmanager
async def tavily_client_lifespan() -> AsyncIterator["AsyncTavilyClient"]:
    """创建不受桌面系统代理意外影响的短生命周期 Tavily 客户端。"""

    from tavily import AsyncTavilyClient

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
            api_key=settings.tavily_api_key,
            client=http_client,
        )
