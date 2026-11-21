"""应用范围的LangChain v1模型、Agent图和内存资源。"""

from __future__ import annotations

import hashlib
import json
import os
from collections import OrderedDict
from collections.abc import Callable
from threading import RLock
from typing import Any, Literal, Mapping

from langchain.chat_models import init_chat_model
from langchain_core.language_models import BaseChatModel
from langgraph.checkpoint.base import BaseCheckpointSaver
from langgraph.graph.state import CompiledStateGraph
from langgraph.store.base import BaseStore
from pydantic import SecretStr

from app.agents.builder import build_search_agent
from app.agents.checkpoints import create_in_memory_checkpointer
from app.agents.novel_builder import (
    build_novel_agent,
    build_novel_idea_agent,
    get_novel_tools,
)
from app.agents.state import AgentContext, checkpoint_thread_id
from app.agents.tools.business_data import create_business_data_tools
from app.agents.tools.general import create_general_tools
from app.agents.tools.csv_analysis import create_csv_analysis_tool
from app.agents.tools.text_compare import create_text_compare_tool
from app.agents.tools.knowledge_search import create_knowledge_search_tool
from app.agents.tools.weather import create_weather_tool
from app.agents.tools.web_search import get_default_tools
from app.config.settings import settings
from app.schemas.request import LLMConfig, UserContext
from app.security.outbound import validate_outbound_http_url
from app.services.agent_tool_client import AgentToolClient
from app.services.http_client import get_http_client
from app.services.knowledge import KnowledgeRetriever
from app.services.memory import LongTermMemoryService
from app.utils.exceptions import (
    AgentError,
    ConfigurationError,
    InputValidationError,
    ModelNotAvailableError,
)
from app.utils.logger import logger

_llm_instance: BaseChatModel | None = None
_checkpointer_instance: BaseCheckpointSaver | None = None
_store_instance: BaseStore | None = None
_knowledge_retriever_instance: KnowledgeRetriever | None = None
_agent_tool_client_instance: AgentToolClient | None = None
_memory_service_instance: LongTermMemoryService | None = None

_model_cache: OrderedDict[str, BaseChatModel] = OrderedDict()
_model_cache_lock = RLock()
_MODEL_CACHE_LIMIT = max(1, min(int(os.getenv("AGENT_MODEL_CACHE_SIZE", "8")), 32))


# 模型实例由配置 LRU 分配；保留强引用，避免对象地址被新的模型复用。
_GraphKey = tuple[int, Callable[..., CompiledStateGraph], bool, tuple[bool, ...]]
_graph_cache: OrderedDict[_GraphKey, tuple[BaseChatModel, CompiledStateGraph]] = OrderedDict()


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
                _, evicted = _model_cache.popitem(last=False)
                for key in list(_graph_cache):
                    if key[0] == id(evicted):
                        del _graph_cache[key]
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
    knowledge_retriever: KnowledgeRetriever | None = None,
    agent_tool_client: AgentToolClient | None = None,
    memory_service: LongTermMemoryService | None = None,
) -> None:
    """在服务前注入生命周期拥有的持久化内存、Store 和知识后端。"""

    global _checkpointer_instance, _store_instance
    global _knowledge_retriever_instance, _agent_tool_client_instance, _memory_service_instance
    with _model_cache_lock:
        _checkpointer_instance = checkpointer
        _store_instance = store
        _knowledge_retriever_instance = knowledge_retriever
        _agent_tool_client_instance = agent_tool_client
        _memory_service_instance = memory_service
        _graph_cache.clear()


def get_checkpointer() -> BaseCheckpointSaver:
    """返回配置的内存，回退到隔离的开发内存。"""

    global _checkpointer_instance
    if _checkpointer_instance is None:
        _checkpointer_instance = create_in_memory_checkpointer()
        logger.warning(
            "Using in-memory Agent checkpoints; configure Postgres for production"
        )
    return _checkpointer_instance


def get_memory_service() -> LongTermMemoryService | None:
    """返回生命周期拥有的长期记忆服务；功能关闭时为 ``None``。"""

    return _memory_service_instance


def _cached_graph(
    builder: Callable[..., CompiledStateGraph],
    tools_factory: Callable[[], list[Any]],
    model: BaseChatModel | None,
    checkpointed: bool,
) -> CompiledStateGraph:
    # 构图与后端切换使用同一把锁；推理在锁外执行。
    with _model_cache_lock:
        if model is None:
            model = get_llm(profile="agent-default")
        tool_flags = (
            (settings.weather_enabled, settings.agent_write_actions_enabled)
            if builder is build_search_agent else ()
        )
        key = (id(model), builder, checkpointed, tool_flags)
        cached = _graph_cache.get(key)
        if cached is not None:
            _graph_cache.move_to_end(key)
            return cached[1]

        graph = builder(
            model=model,
            tools=tools_factory(),
            checkpointer=get_checkpointer() if checkpointed else None,
            store=_store_instance,
        )
        _graph_cache[key] = (model, graph)
        if len(_graph_cache) > _MODEL_CACHE_LIMIT:
            _graph_cache.popitem(last=False)
        return graph


def get_agent(
    *,
    checkpointed: bool = True,
    model: BaseChatModel | None = None,
) -> CompiledStateGraph:
    return _cached_graph(build_search_agent, _runtime_tools, model, checkpointed)


def _runtime_tools() -> list[Any]:
    """装配当前进程可用工具；知识后端未启用时不暴露工具。"""
    tools = create_general_tools()
    tools.append(create_csv_analysis_tool())
    tools.append(create_text_compare_tool())
    if settings.weather_enabled:
        tools.append(create_weather_tool())
    tools.extend(get_default_tools())
    if _knowledge_retriever_instance is not None:
        tools.append(create_knowledge_search_tool(_knowledge_retriever_instance))
    if _agent_tool_client_instance is not None:
        tools.extend(create_business_data_tools(_agent_tool_client_instance))
    return tools


async def resolve_checkpoint_thread(
    user_id: str, thread_id: str, business: Literal["chat", "novel"],
) -> str:
    """只续接归属明确的旧检查点，保留来源混合或未知的原件。"""
    scoped = checkpoint_thread_id(user_id, thread_id, business)
    legacy = checkpoint_thread_id(user_id, thread_id, None)
    saved = await get_checkpointer().aget_tuple({"configurable": {"thread_id": legacy}})
    if saved is None:
        return scoped
    owners = {
        "lingxi-search-agent": "chat",
        "lingxi-novel-agent": "novel",
        "lingxi-novel-idea-agent": "novel",
    }
    names = {saved.metadata.get("lc_agent_name")}
    messages = saved.checkpoint.get("channel_values", {}).get("messages", [])
    names.update(message.name for message in messages if getattr(message, "type", None) == "ai")
    domains = {owners[name] for name in names if name in owners}
    return legacy if domains == {business} else scoped


async def delete_agent_thread(
    *, user_id: str, thread_id: str, business: Literal["chat", "novel"] = "chat",
) -> None:
    internal_id = await resolve_checkpoint_thread(user_id, thread_id, business)
    await get_checkpointer().adelete_thread(internal_id)


def create_agent_context(
    *,
    llm_config: LLMConfig | None,
    user_id: str,
    thread_id: str,
    checkpointed: bool = False,
    style: str,
    business_tag: str,
    user_context: UserContext | None = None,
    agent_request_id: str = "",
    tool_access_token: SecretStr | None = None,
    memory_preferences: tuple[tuple[str, str], ...] = (),
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
        checkpointed=checkpointed,
        style="casual" if style == "casual" else "professional",
        business_tag=business_tag,
        user_name=user_context.user_name if user_context is not None else "",
        role_code=(user_context.role_code or "") if user_context is not None else "",
        role_name=(user_context.role_name or "") if user_context is not None else "",
        region_id=user_context.region_id if user_context is not None else None,
        region_name=(user_context.region_name or "") if user_context is not None else "",
        permissions=(
            tuple(user_context.permissions) if user_context is not None else ()
        ),
        memory_preferences=memory_preferences,
        agent_request_id=agent_request_id,
        tool_access_token=tool_access_token,
        tavily_api_key=(
            llm_config.tavily_api_key if llm_config is not None else None
        ),
        model=model,
    )


def get_novel_agent(
    *,
    checkpointed: bool = True,
    model: BaseChatModel | None = None,
) -> CompiledStateGraph:
    return _cached_graph(build_novel_agent, get_novel_tools, model, checkpointed)


def get_novel_idea_agent(
    *,
    checkpointed: bool = True,
    model: BaseChatModel | None = None,
) -> CompiledStateGraph:
    return _cached_graph(build_novel_idea_agent, get_novel_tools, model, checkpointed)


def create_novel_agent_context(
    *,
    llm_config: LLMConfig | None,
    user_id: str,
    thread_id: str,
    novel_context: Mapping[str, Any] | None = None,
) -> AgentContext:
    """构建小说创作调用上下文并解析任何有界的模型覆盖。"""

    model = (
        create_llm(
            llm_config,
            profile="chat-request",
            streaming=True,
        )
        if llm_config is not None
        else None
    )
    return AgentContext(
        user_id=user_id,
        thread_id=thread_id,
        checkpointed=bool(thread_id),
        novel_context=novel_context,
        tavily_api_key=(
            llm_config.tavily_api_key if llm_config is not None else None
        ),
        model=model,
    )


def get_request_id() -> str:
    """重用请求中间件的ID，仅在直接调用时生成。"""

    from app.utils.logger import generate_request_id
    from app.utils.logger import get_request_id as current_request_id

    current = current_request_id()
    return generate_request_id() if not current or current == "-" else current


def reset_singletons() -> None:
    """重置用于测试和应用程序关闭的进程本地缓存。"""

    global _llm_instance, _checkpointer_instance, _store_instance
    global _knowledge_retriever_instance, _agent_tool_client_instance, _memory_service_instance
    with _model_cache_lock:
        _llm_instance = None
        _checkpointer_instance = None
        _store_instance = None
        _knowledge_retriever_instance = None
        _agent_tool_client_instance = None
        _memory_service_instance = None
        _model_cache.clear()
        _graph_cache.clear()
    logger.info("LangChain runtime singletons reset")
