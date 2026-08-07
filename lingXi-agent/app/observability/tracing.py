"""Langfuse 可观测性集成。

Langfuse 4.x 的 LangChain CallbackHandler 在每次根 run 时从
RunnableConfig 的 metadata 解析 ``langfuse_trace_name``、
``langfuse_user_id``、``langfuse_session_id``、``langfuse_tags`` 等专用键，
因此进程级复用单个 handler 即可实现请求级 trace 隔离。``with_trace`` 把
专用键合并进调用方已有的 config，未启用或初始化失败时原样返回，不影响
业务链路。
"""

from __future__ import annotations

from typing import Any

from app.config.settings import settings
from app.utils.logger import logger

_handler: Any = None


def is_tracing_enabled() -> bool:
    """追踪功能是否完整配置并开启。"""
    return (
        settings.langfuse_enabled
        and bool(settings.langfuse_public_key_value)
        and bool(settings.langfuse_secret_key_value)
    )


def _get_handler() -> Any:
    """惰性创建进程级 Langfuse LangChain handler；未配置时返回 None。"""
    global _handler
    if _handler is None:
        if not is_tracing_enabled():
            return None
        try:
            from langfuse import Langfuse
            from langfuse.langchain import CallbackHandler

            Langfuse(
                public_key=settings.langfuse_public_key_value,
                secret_key=settings.langfuse_secret_key_value,
                host=settings.langfuse_host.rstrip("/"),
                environment=settings.langfuse_environment or None,
                debug=settings.langfuse_debug,
            )
            _handler = CallbackHandler(
                public_key=settings.langfuse_public_key_value,
            )
            logger.info(
                "Langfuse tracing enabled | host=%s | environment=%s",
                settings.langfuse_host,
                settings.langfuse_environment,
            )
        except Exception as exc:  # noqa: BLE001 - 初始化失败不能影响业务链路
            logger.warning(
                "Langfuse handler initialization failed | error_type=%s",
                type(exc).__name__,
            )
            _handler = None
    return _handler


def with_trace(
    config: dict[str, Any],
    name: str,
    *,
    user_id: str = "",
    thread_id: str = "",
    tags: list[str] | None = None,
    metadata: dict[str, Any] | None = None,
) -> dict[str, Any]:
    """把 Langfuse trace 合并进 RunnableConfig，返回新的 config。

    合并内容：``callbacks``（进程级 handler）以及嵌入 metadata 的
    ``langfuse_*`` 专用键。原有 metadata 会被保留；业务字段可通过
    ``metadata`` 一并挂到 trace 上。``tags`` 用于跨 trace 的 feature 维度
    过滤（如 ``["chat"]``、``["extract"]``），须在 trace 创建时确定。
    """
    handler = _get_handler()
    if handler is None:
        return config
    merged = dict(config)
    merged["callbacks"] = [handler]
    trace_meta: dict[str, Any] = {"langfuse_trace_name": name}
    if user_id:
        trace_meta["langfuse_user_id"] = user_id
    if thread_id:
        trace_meta["langfuse_session_id"] = thread_id
    if tags:
        trace_meta["langfuse_tags"] = tags
    if metadata:
        trace_meta.update(metadata)
    existing = merged.get("metadata")
    if isinstance(existing, dict):
        merged["metadata"] = {**existing, **trace_meta}
    else:
        merged["metadata"] = trace_meta
    return merged


def flush_traces() -> None:
    """同步刷新待发送的 trace；仅在进程退出边界调用。"""
    if _handler is None:
        return
    try:
        from langfuse import get_client

        get_client(public_key=settings.langfuse_public_key_value).flush()
    except Exception as exc:  # noqa: BLE001 - 退出边界兜底
        logger.warning(
            "Langfuse flush failed | error_type=%s",
            type(exc).__name__,
        )


def reset_tracing() -> None:
    """重置进程级 handler（测试与生命周期重启）。"""
    global _handler
    _handler = None
