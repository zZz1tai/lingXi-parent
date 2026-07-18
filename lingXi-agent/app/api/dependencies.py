"""
FastAPI dependency injection providers.

Manages LLM model and search agent instances.
Supports both singleton mode (env-based) and per-request mode (config from Java).
"""

from __future__ import annotations

from typing import Any, Optional

from langchain_core.language_models import BaseChatModel
from langgraph.graph.state import CompiledStateGraph

from app.agents.builder import build_search_agent
from app.config.settings import settings
from app.schemas.request import LLMConfig
from app.utils.exceptions import ConfigurationError, ModelNotAvailableError
from app.utils.logger import logger


# ── Singleton Cache (fallback when no config provided) ───────────────────────

_llm_instance: Optional[BaseChatModel] = None
_agent_instance: Optional[CompiledStateGraph] = None


# ── LLM Provider ────────────────────────────────────────────────────────────

def _redact_secret(message: str, secret: str) -> str:
    """Remove a credential if a provider includes it in an error message."""
    if not secret:
        return message
    return message.replace(secret, "[REDACTED]")


def _new_chat_model(
    config: Optional[LLMConfig] = None,
    *,
    profile: Optional[str] = None,
    timeout: Optional[float] = None,
    max_retries: Optional[int] = None,
    temperature: Optional[float] = None,
    streaming: Optional[bool] = None,
) -> BaseChatModel:
    """Construct one OpenAI-compatible chat model without caching it."""
    from langchain_openai import ChatOpenAI

    api_key = config.api_key if config is not None else settings.openai_api_key
    model_name = config.model if config is not None else settings.model_name
    base_url = config.base_url if config is not None else settings.openai_api_base
    configured_timeout = config.timeout_seconds if config is not None else None
    effective_timeout = timeout if timeout is not None else configured_timeout
    if not api_key:
        raise ConfigurationError(
            "LLM API key is not configured. Pass llm_config.api_key or set "
            "OPENAI_API_KEY."
        )

    kwargs: dict[str, Any] = {
        "model": model_name,
        "api_key": api_key,
        "temperature": settings.temperature if temperature is None else temperature,
        "timeout": 30 if effective_timeout is None else effective_timeout,
        "max_retries": 1 if max_retries is None else max_retries,
    }
    if base_url:
        kwargs["base_url"] = base_url
    if streaming is not None:
        kwargs["streaming"] = streaming

    model = ChatOpenAI(**kwargs)
    logger.info(
        "LLM initialized | profile=%s | provider=%s | model=%s | endpoint=%s | "
        "timeout=%s | temperature=%s | max_retries=%s | streaming=%s",
        profile or "default",
        settings.llm_provider,
        model_name,
        "custom" if base_url else "default",
        kwargs["timeout"],
        kwargs["temperature"],
        kwargs["max_retries"],
        kwargs.get("streaming", "provider-default"),
    )
    return model


def create_llm(
    config: Optional[LLMConfig] = None,
    *,
    profile: Optional[str] = None,
    timeout: Optional[float] = None,
    max_retries: Optional[int] = None,
    temperature: Optional[float] = None,
    streaming: Optional[bool] = None,
) -> BaseChatModel:
    """Create an LLM instance from config or env settings.

    Args:
        config: Optional LLM config from Java backend request. Its
                ``timeout_seconds`` is used unless ``timeout`` explicitly
                overrides it. If None, uses env-based settings (singleton).
        profile: Optional workload label for credential-free diagnostics.
        timeout: Optional provider request timeout in seconds.
        max_retries: Optional provider retry count.
        temperature: Optional sampling temperature override.
        streaming: Optional ChatOpenAI streaming-mode override.

    Returns:
        A configured ``BaseChatModel`` instance.
    """
    # Preserve the singleton for ordinary env-configured chat calls.  A caller
    # asking for different runtime characteristics (for example the five-minute
    # chapter-analysis timeout) receives a purpose-built model instead.
    has_overrides = any(
        value is not None
        for value in (timeout, max_retries, temperature, streaming)
    )
    if config is None and not has_overrides:
        return get_llm(profile=profile)

    try:
        return _new_chat_model(
            config,
            profile=profile,
            timeout=timeout,
            max_retries=max_retries,
            temperature=temperature,
            streaming=streaming,
        )
    except Exception as exc:
        if isinstance(exc, ConfigurationError):
            raise
        api_key = config.api_key if config is not None else settings.openai_api_key
        safe_error = _redact_secret(str(exc), api_key)
        logger.error(
            "Failed to create LLM | profile=%s | error=%s",
            profile or "default",
            safe_error,
        )
        raise ModelNotAvailableError(
            f"Failed to initialize LLM for profile '{profile or 'default'}': "
            f"{safe_error}"
        ) from None


def get_llm(*, profile: Optional[str] = None) -> BaseChatModel:
    """Return a cached LLM instance based on application settings.

    Supports OpenAI-compatible APIs (including Doubao/Coze) via the
    ``openai_api_base`` setting.

    Returns:
        A configured ``BaseChatModel`` instance.

    Raises:
        ConfigurationError: If the API key is not configured.
        ModelNotAvailableError: If the model fails to initialize.
    """
    global _llm_instance

    if _llm_instance is not None:
        return _llm_instance

    if not settings.openai_api_key:
        raise ConfigurationError(
            "OPENAI_API_KEY is not configured. "
            "Please set it in the .env file or as an environment variable."
        )

    try:
        _llm_instance = _new_chat_model(profile=profile)
        return _llm_instance

    except Exception as exc:
        if isinstance(exc, ConfigurationError):
            raise
        safe_error = _redact_secret(str(exc), settings.openai_api_key)
        logger.error(
            "Failed to initialize cached LLM | profile=%s | error=%s",
            profile or "default",
            safe_error,
        )
        raise ModelNotAvailableError(
            f"Failed to initialize LLM: {safe_error}"
        ) from None


# ── Agent Provider ──────────────────────────────────────────────────────────

def get_agent(llm_config: Optional[LLMConfig] = None) -> CompiledStateGraph:
    """Return a search agent instance.

    When llm_config is provided, creates a new agent with that config.
    When llm_config is None, returns the cached singleton agent.

    Args:
        llm_config: Optional LLM config from Java backend.

    Returns:
        A compiled LangGraph ``CompiledStateGraph`` agent.
    """
    # If config provided, always create new agent (no caching)
    if llm_config is not None:
        model = create_llm(llm_config)
        agent = build_search_agent(model=model)
        logger.info("Search agent created from request config")
        return agent

    # Fallback to singleton
    global _agent_instance
    if _agent_instance is not None:
        return _agent_instance

    model = get_llm()
    _agent_instance = build_search_agent(model=model)
    logger.info("Search agent initialized and cached")
    return _agent_instance


# ── Request ID Provider ─────────────────────────────────────────────────────

def get_request_id() -> str:
    """Generate a unique request ID for tracing."""
    from app.utils.logger import generate_request_id
    return generate_request_id()


# ── Lifecycle Hooks ─────────────────────────────────────────────────────────

def reset_singletons() -> None:
    """Reset cached instances (useful for testing or reconfiguration)."""
    global _llm_instance, _agent_instance
    _llm_instance = None
    _agent_instance = None
    logger.info("Singleton instances reset")
