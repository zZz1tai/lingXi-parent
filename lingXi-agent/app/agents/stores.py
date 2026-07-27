"""LangGraph 长期 Store 的应用生命周期辅助函数。"""

from __future__ import annotations

from contextlib import asynccontextmanager
from typing import AsyncIterator

from langgraph.store.base import BaseStore
from langgraph.store.memory import InMemoryStore

from app.utils.exceptions import ConfigurationError


def create_in_memory_store() -> InMemoryStore:
    """创建用于开发和测试的隔离进程内 Store。"""

    return InMemoryStore()


@asynccontextmanager
async def store_lifespan(
    *,
    backend: str | None = None,
    postgres_dsn: str | None = None,
    setup: bool = True,
) -> AsyncIterator[BaseStore | None]:
    """打开配置的长期 Store，并在应用关闭时释放连接。"""

    selected = (backend or "disabled").strip().lower()
    if selected == "disabled":
        yield None
        return
    if selected in {"memory", "in_memory", "in-memory"}:
        yield create_in_memory_store()
        return
    if selected != "postgres":
        raise ConfigurationError(
            "AGENT_STORE_BACKEND must be 'disabled', 'memory', or 'postgres'"
        )

    dsn = (postgres_dsn or "").strip()
    if not dsn:
        raise ConfigurationError(
            "AGENT_STORE_POSTGRES_DSN is required when the Store backend is postgres"
        )

    try:
        from langgraph.store.postgres.aio import AsyncPostgresStore
    except ImportError as exc:  # pragma: no cover - depends on deployment extras
        raise ConfigurationError(
            "Postgres Store dependencies are not installed"
        ) from exc

    async with AsyncPostgresStore.from_conn_string(dsn) as store:
        if setup:
            await store.setup()
        yield store
