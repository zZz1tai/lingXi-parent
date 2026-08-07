"""应用程序生命周期的HTTP客户端，用于提供商调用。

``trust_env=False`` 使出站请求不继承操作系统/环境代理（如 Windows 系统
代理），避免代理链路异常导致业务调用失败；LLM 供应商调用走 LangChain
自带客户端，不受此影响。
"""

from __future__ import annotations

import httpx

from app.config.settings import settings
from app.utils.exceptions import ConfigurationError


_client: httpx.AsyncClient | None = None


async def initialize_http_client() -> httpx.AsyncClient:
    """为每个应用程序工作进程创建共享的提供商客户端。"""
    global _client
    if _client is None or _client.is_closed:
        _client = httpx.AsyncClient(
            timeout=httpx.Timeout(settings.provider_http_timeout_seconds),
            limits=httpx.Limits(
                max_connections=settings.provider_http_max_connections,
                max_keepalive_connections=settings.provider_http_max_keepalive_connections,
            ),
            follow_redirects=False,
            trust_env=False,
        )
    return _client


def get_http_client() -> httpx.AsyncClient:
    """返回已初始化的提供商客户端，供FastAPI依赖使用。"""
    if _client is None or _client.is_closed:
        raise ConfigurationError("Provider HTTP client is not initialized")
    return _client


def http_client_ready() -> bool:
    """返回应用程序生命周期客户端是否可用。"""
    return _client is not None and not _client.is_closed


async def close_http_client() -> None:
    """关闭共享的提供商连接池。"""
    global _client
    if _client is not None and not _client.is_closed:
        await _client.aclose()
    _client = None
