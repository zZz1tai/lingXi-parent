"""
FastAPI dependency injection providers.

Manages LLM model and search agent instances.
Supports both singleton mode (env-based) and per-request mode (config from Java).
"""

from __future__ import annotations

from typing import Optional

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

def create_llm(config: Optional[LLMConfig] = None) -> BaseChatModel:
    """Create an LLM instance from config or env settings.

    Args:
        config: Optional LLM config from Java backend request.
                If None, uses env-based settings (singleton).

    Returns:
        A configured ``BaseChatModel`` instance.
    """
    # Use singleton if no config provided
    if config is None:
        return get_llm()

    # Create per-request LLM from Java-provided config
    try:
        from langchain_openai import ChatOpenAI

        kwargs: dict = {
            "model": config.model,
            "api_key": config.api_key,
            "temperature": settings.temperature,
            "timeout": 30,
            "max_retries": 1,
        }

        if config.base_url:
            kwargs["base_url"] = config.base_url

        logger.info(
            "Creating LLM from config | model=%s | base_url=%s | api_key=%s...",
            config.model,
            config.base_url or "default",
            config.api_key[:10] if config.api_key else "None",
        )

        llm = ChatOpenAI(**kwargs)
        logger.info(
            "LLM created from request config | model=%s | base_url=%s",
            config.model,
            config.base_url or "default",
        )
        return llm

    except Exception as exc:
        logger.error("Failed to create LLM from config: %s", str(exc))
        raise ModelNotAvailableError(
            f"Failed to initialize LLM from config: {exc}"
        ) from exc


def get_llm() -> BaseChatModel:
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
        from langchain_openai import ChatOpenAI

        kwargs: dict = {
            "model": settings.model_name,
            "api_key": settings.openai_api_key,
            "temperature": settings.temperature,
            "timeout": 30,
            "max_retries": 1,
        }

        if settings.openai_api_base:
            kwargs["base_url"] = settings.openai_api_base

        _llm_instance = ChatOpenAI(**kwargs)
        logger.info(
            "LLM initialized | provider=%s | model=%s | base_url=%s",
            settings.llm_provider,
            settings.model_name,
            settings.openai_api_base or "default",
        )
        return _llm_instance

    except Exception as exc:
        raise ModelNotAvailableError(
            f"Failed to initialize LLM: {exc}"
        ) from exc


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
