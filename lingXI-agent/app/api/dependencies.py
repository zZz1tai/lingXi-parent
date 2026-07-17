"""
FastAPI dependency injection providers.

Manages singleton instances of the LLM model and search agent,
providing them to route handlers via ``Depends()``.
"""

from __future__ import annotations

from typing import Optional

from langchain_core.language_models import BaseChatModel
from langgraph.graph.state import CompiledStateGraph

from app.agents.builder import build_search_agent
from app.config.settings import settings
from app.utils.exceptions import ConfigurationError, ModelNotAvailableError
from app.utils.logger import logger


# ── Singleton Cache ─────────────────────────────────────────────────────────

_llm_instance: Optional[BaseChatModel] = None
_agent_instance: Optional[CompiledStateGraph] = None


# ── LLM Provider ────────────────────────────────────────────────────────────

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
            "timeout": 30,  # 30 seconds timeout for LLM calls
            "max_retries": 1,  # Only retry once to fail fast
        }

        # Custom API base for Doubao / Coze / other compatible endpoints
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

def get_agent() -> CompiledStateGraph:
    """Return a cached search agent instance.

    The agent is built once on first access using the default LLM
    and tool configuration. Subsequent calls return the same instance.

    Returns:
        A compiled LangGraph ``CompiledStateGraph`` agent.
    """
    global _agent_instance

    if _agent_instance is not None:
        return _agent_instance

    model = get_llm()
    _agent_instance = build_search_agent(model=model)
    logger.info("Search agent initialized and cached")
    return _agent_instance


# ── Request ID Provider ─────────────────────────────────────────────────────

def get_request_id() -> str:
    """Generate a unique request ID for tracing.

    In production, this would be extracted from request headers
    (e.g. ``X-Request-ID``). For now, generates a new UUID.
    """
    from app.utils.logger import generate_request_id
    return generate_request_id()


# ── Lifecycle Hooks ─────────────────────────────────────────────────────────

def reset_singletons() -> None:
    """Reset cached instances (useful for testing or reconfiguration)."""
    global _llm_instance, _agent_instance
    _llm_instance = None
    _agent_instance = None
    logger.info("Singleton instances reset")
