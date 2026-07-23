"""开发和生产环境 Agent 检查点的生命周期辅助函数。"""

from __future__ import annotations

from contextlib import asynccontextmanager
from typing import AsyncIterator

from langgraph.checkpoint.base import BaseCheckpointSaver
from langgraph.checkpoint.memory import InMemorySaver

from app.utils.exceptions import ConfigurationError


def create_in_memory_checkpointer() -> InMemorySaver:
    """创建用于开发和测试的隔离进程内检查点保存器。"""

    return InMemorySaver()


@asynccontextmanager
async def checkpointer_lifespan(
    *,
    backend: str | None = None,
    postgres_dsn: str | None = None,
    setup: bool = True,
) -> AsyncIterator[BaseCheckpointSaver]:
    """打开配置的检查点保存器，并在应用关闭时关闭它。

    ``main.lifespan`` 可以包装此上下文管理器，并将产出的保存器
    传递给 ``configure_agent_runtime``。保持显式连接可防止
    在导入时创建异步 Postgres 连接池。
    """

    selected = (backend or "memory").strip().lower()
    if selected in {"memory", "in_memory", "in-memory"}:
        yield create_in_memory_checkpointer()
        return

    if selected != "postgres":
        raise ConfigurationError(
            "AGENT_CHECKPOINTER_BACKEND must be 'memory' or 'postgres'"
        )

    dsn = (postgres_dsn or "").strip()
    if not dsn:
        raise ConfigurationError(
            "AGENT_POSTGRES_DSN is required when the checkpoint backend is postgres"
        )

    try:
        from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver
    except ImportError as exc:  # pragma: no cover - depends on deployment extras
        raise ConfigurationError(
            "Postgres checkpoint dependencies are not installed"
        ) from exc

    async with AsyncPostgresSaver.from_conn_string(dsn) as saver:
        if setup:
            await saver.setup()
        yield saver
