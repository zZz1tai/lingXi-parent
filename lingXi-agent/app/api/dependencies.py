"""应用范围的LangChain v1模型、Agent图和内存资源。"""

from __future__ import annotations

import hashlib
import json
import os
from collections import OrderedDict
from threading import RLock
from typing import Any

from langchain.chat_models import init_chat_model
from langchain_core.language_models import BaseChatModel
from langgraph.checkpoint.base import BaseCheckpointSaver
from langgraph.graph.state import CompiledStateGraph
from langgraph.store.base import BaseStore
from pydantic import SecretStr

from app.agents.builder import build_search_agent
from app.agents.checkpoints import create_in_memory_checkpointer
from app.agents.state import AgentContext, checkpoint_thread_id
from app.config.settings import settings
from app.schemas.request import LLMConfig
from app.security.outbound import validate_outbound_http_url
from app.services.http_client import get_http_client
from app.utils.exceptions import (
    AgentError,
    ConfigurationError,
    InputValidationError,
    ModelNotAvailableError,
)
from app.utils.logger import logger


_llm_instance: BaseChatModel | None = None
_agent_instance: CompiledStateGraph | None = None
_ephemeral_agent_instance: CompiledStateGraph | None = None
_checkpointer_instance: BaseCheckpointSaver | None = None
_store_instance: BaseStore | None = None

_model_cache: OrderedDict[str, BaseChatModel] = OrderedDict()
_model_cache_lock = RLock()
_MODEL_CACHE_LIMIT = max(1, min(int(os.getenv("AGENT_MODEL_CACHE_SIZE", "8")), 32))


def _secret_value(value: str | SecretStr) -> str:
    return value.get_secret_value() if isinstance(value, SecretStr) else value


def _redact_secret(message: str, secret: str | SecretStr) -> str:
    """如果提供者在错误消息中包含凭据，则删除该凭据。"""

    raw_secret = _secret_value(secret)
    if not raw_secret:
        return message
    return message.replace(raw_secret, "[REDACTED]")


def _normalized_model_values(
    config: LLMConfig | None,
) -> tuple[str, str, str | None, float | None]:
    api_key = (
        _secret_value(config.api_key)
        if config is not None
        else _secret_value(settings.openai_api_key)
    )
    model_name = config.model if config is not None else settings.model_name
    raw_base_url = config.base_url if config is not None else settings.openai_api_base
    base_url = str(raw_base_url).rstrip("/") if raw_base_url else None
    if base_url:
        base_url = validate_outbound_http_url(base_url)
    configured_timeout = config.timeout_seconds if config is not None else None
    return api_key, model_name, base_url, configured_timeout


def _model_cache_key(
    *,
    api_key: str,
    model_name: str,
    base_url: str | None,
    timeout: float,
    max_retries: int,
    temperature: float,
    streaming: bool | None,
) -> str:
    payload = json.dumps(
        {
            "api_key": api_key,
            "model": model_name,
            "base_url": base_url,
            "timeout": timeout,
            "max_retries": max_retries,
            "temperature": temperature,
            "streaming": streaming,
        },
        sort_keys=True,
        separators=(",", ":"),
    )
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


def _new_chat_model(
    config: LLMConfig | None = None,
    *,
    profile: str | None = None,
    timeout: float | None = None,
    max_retries: int | None = None,
    temperature: float | None = None,
    streaming: bool | None = None,
) -> BaseChatModel:
    """构造一个OpenAI兼容的v1聊天模型，不缓存它。"""

    api_key, model_name, base_url, configured_timeout = _normalized_model_values(config)
    if not api_key:
        raise ConfigurationError(
            "LLM API key is not configured. Pass llm_config.api_key or set OPENAI_API_KEY."
        )

    kwargs: dict[str, Any] = {
        "api_key": api_key,
        "temperature": settings.temperature if temperature is None else temperature,
        "timeout": 30 if timeout is None and configured_timeout is None else (
            configured_timeout if timeout is None else timeout
        ),
        "max_retries": 1 if max_retries is None else max_retries,
        "output_version": "v1",
        # 应用自有客户端关闭自动重定向，防止白名单提供方把模型流量
        # 重定向到不受信任或内网地址。
        "http_async_client": get_http_client(),
    }
    if base_url:
        kwargs["base_url"] = base_url
    if streaming is not None:
        kwargs["streaming"] = streaming

    model = init_chat_model(
        model_name,
        model_provider="openai",
        **kwargs,
    )
    logger.info(
        "LLM initialized | profile=%s | provider=%s | model_length=%d | endpoint=%s | "
        "timeout=%s | temperature=%s | max_retries=%s | streaming=%s",
        profile or "default",
        settings.llm_provider,
        len(model_name),
        "custom-allowlisted" if base_url else "default",
        kwargs["timeout"],
        kwargs["temperature"],
        kwargs["max_retries"],
        kwargs.get("streaming", "provider-default"),
    )
    return model


def create_llm(
    config: LLMConfig | None = None,
    *,
    profile: str | None = None,
    timeout: float | None = None,
    max_retries: int | None = None,
    temperature: float | None = None,
    streaming: bool | None = None,
) -> BaseChatModel:
    """从凭证安全的有限缓存中返回模型。"""

    if config is None and all(
        value is None for value in (timeout, max_retries, temperature, streaming)
    ):
        return get_llm(profile=profile)

    try:
        api_key, model_name, base_url, configured_timeout = _normalized_model_values(config)
        effective_timeout = float(
            30 if timeout is None and configured_timeout is None else (
                configured_timeout if timeout is None else timeout
            )
        )
        effective_retries = 1 if max_retries is None else max_retries
        effective_temperature = settings.temperature if temperature is None else temperature
        cache_key = _model_cache_key(
            api_key=api_key,
            model_name=model_name,
            base_url=base_url,
            timeout=effective_timeout,
            max_retries=effective_retries,
            temperature=effective_temperature,
            streaming=streaming,
        )

        with _model_cache_lock:
            cached = _model_cache.get(cache_key)
            if cached is not None:
                _model_cache.move_to_end(cache_key)
                return cached

            model = _new_chat_model(
                config,
                profile=profile,
                timeout=timeout,
                max_retries=max_retries,
                temperature=temperature,
                streaming=streaming,
            )
            _model_cache[cache_key] = model
            while len(_model_cache) > _MODEL_CACHE_LIMIT:
                _model_cache.popitem(last=False)
            return model
    except Exception as exc:
        if isinstance(exc, InputValidationError) and config is None:
            raise ConfigurationError("Configured LLM base URL is not allowed") from exc
        if isinstance(exc, AgentError):
            raise
        api_key = config.api_key if config is not None else settings.openai_api_key
        safe_error = _redact_secret(str(exc), api_key)
        logger.error(
            "Failed to create LLM | profile=%s | error_type=%s",
            profile or "default",
            type(exc).__name__,
        )
        raise ModelNotAvailableError(
            f"Failed to initialize LLM for profile '{profile or 'default'}': {safe_error}"
        ) from None


def get_llm(*, profile: str | None = None) -> BaseChatModel:
    """返回缓存的环境配置默认模型。"""

    global _llm_instance
    if _llm_instance is not None:
        return _llm_instance

    try:
        _llm_instance = _new_chat_model(profile=profile)
        return _llm_instance
    except Exception as exc:
        if isinstance(exc, InputValidationError):
            raise ConfigurationError("Configured LLM base URL is not allowed") from exc
        if isinstance(exc, AgentError):
            raise
        safe_error = _redact_secret(str(exc), settings.openai_api_key)
        logger.error(
            "Failed to initialize cached LLM | profile=%s | error_type=%s",
            profile or "default",
            type(exc).__name__,
        )
        raise ModelNotAvailableError(
            f"Failed to initialize LLM: {safe_error}"
        ) from None


def configure_agent_runtime(
    checkpointer: BaseCheckpointSaver,
    *,
    store: BaseStore | None = None,
) -> None:
    """在服务前注入生命周期拥有的持久化内存/存储资源。"""

    global _checkpointer_instance, _store_instance, _agent_instance
    global _ephemeral_agent_instance
    _checkpointer_instance = checkpointer
    _store_instance = store
    _agent_instance = None
    _ephemeral_agent_instance = None


def get_checkpointer() -> BaseCheckpointSaver:
    """返回配置的内存，回退到隔离的开发内存。"""

    global _checkpointer_instance
    if _checkpointer_instance is None:
        _checkpointer_instance = create_in_memory_checkpointer()
        logger.warning(
            "Using in-memory Agent checkpoints; configure Postgres for production"
        )
    return _checkpointer_instance


def get_agent(
    *,
    checkpointed: bool = True,
    model: BaseChatModel | None = None,
) -> CompiledStateGraph:
    """返回使用请求模型的图，如果不存在则返回传统默认值。"""

    if model is not None:
        return build_search_agent(
            model=model,
            checkpointer=get_checkpointer() if checkpointed else None,
            store=_store_instance,
        )

    global _agent_instance, _ephemeral_agent_instance
    if checkpointed:
        if _agent_instance is None:
            _agent_instance = build_search_agent(
                model=get_llm(profile="agent-default"),
                checkpointer=get_checkpointer(),
                store=_store_instance,
            )
            logger.info("Checkpointed search agent initialized and cached")
        return _agent_instance

    if _ephemeral_agent_instance is None:
        _ephemeral_agent_instance = build_search_agent(
            model=get_llm(profile="agent-default"),
            checkpointer=None,
            store=_store_instance,
        )
        logger.info("Ephemeral search agent initialized and cached")
    return _ephemeral_agent_instance


async def delete_agent_thread(*, user_id: str, thread_id: str) -> None:
    """永久删除一个用户的检查点对话。"""

    await get_checkpointer().adelete_thread(
        checkpoint_thread_id(user_id, thread_id)
    )


def create_agent_context(
    *,
    llm_config: LLMConfig | None,
    user_id: str,
    thread_id: str,
    style: str,
    business_tag: str,
) -> AgentContext:
    """构建不可变上下文并解析任何有界的模型覆盖。"""

    model = (
        create_llm(llm_config, profile="chat-request")
        if llm_config is not None
        else None
    )
    return AgentContext(
        user_id=user_id,
        thread_id=thread_id,
        style="casual" if style == "casual" else "professional",
        business_tag=business_tag,
        model=model,
    )


def get_request_id() -> str:
    """重用请求中间件的ID，仅在直接调用时生成。"""

    from app.utils.logger import generate_request_id, get_request_id as current_request_id

    current = current_request_id()
    return generate_request_id() if not current or current == "-" else current


def reset_singletons() -> None:
    """重置用于测试和应用程序关闭的进程本地缓存。"""

    global _llm_instance, _agent_instance, _ephemeral_agent_instance
    global _checkpointer_instance, _store_instance
    _llm_instance = None
    _agent_instance = None
    _ephemeral_agent_instance = None
    _checkpointer_instance = None
    _store_instance = None
    with _model_cache_lock:
        _model_cache.clear()
    logger.info("LangChain runtime singletons reset")
