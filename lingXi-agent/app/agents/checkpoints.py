"""Lifecycle helpers for development and production Agent checkpoints."""

from __future__ import annotations

from contextlib import asynccontextmanager
from typing import AsyncIterator

from langgraph.checkpoint.base import BaseCheckpointSaver
from langgraph.checkpoint.memory import InMemorySaver

from app.utils.exceptions import ConfigurationError


def create_in_memory_checkpointer() -> InMemorySaver:
    """Create an isolated in-process saver for development and tests."""

    return InMemorySaver()


@asynccontextmanager
async def checkpointer_lifespan(
    *,
    backend: str | None = None,
    postgres_dsn: str | None = None,
    setup: bool = True,
) -> AsyncIterator[BaseCheckpointSaver]:
    """Open the configured checkpointer and close it on application shutdown.

    ``main.lifespan`` can wrap this context manager and pass the yielded saver
    to ``configure_agent_runtime``.  Keeping that wiring explicit prevents an
    async Postgres connection pool from being created at import time.
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
