"""LangChain v1 Agent factories for chat and structured extraction."""

from __future__ import annotations

from collections.abc import Callable, Sequence
from typing import Any, Literal

from langchain.agents import create_agent
from langchain.agents.structured_output import ProviderStrategy, ToolStrategy
from langchain_core.language_models import BaseChatModel
from langchain_core.stores import BaseStore
from langchain_core.tools import BaseTool
from langgraph.checkpoint.base import BaseCheckpointSaver
from langgraph.graph.state import CompiledStateGraph
from pydantic import BaseModel

from app.agents.middleware import build_agent_middleware
from app.agents.state import AgentContext, RetailAgentState
from app.agents.tools.web_search import get_default_tools
from app.config.settings import settings
from app.utils.logger import logger


# Compatibility import for application code that previously used
# ``builder.AgentState``.  The implementation is now the v1 schema extending
# ``langchain.agents.AgentState``.
AgentState = RetailAgentState


def build_search_agent(
    model: BaseChatModel | None = None,
    tools: Sequence[BaseTool | Callable[..., Any] | dict[str, Any]] | None = None,
    *,
    state_schema: type[RetailAgentState] = RetailAgentState,
    context_schema: type[AgentContext] = AgentContext,
    response_format: Any | None = None,
    checkpointer: BaseCheckpointSaver | bool | None = None,
    store: BaseStore | None = None,
    middleware: Sequence[Any] | None = None,
) -> CompiledStateGraph:
    """Compile the shared search Agent with v1 middleware and memory."""

    if model is None:
        from app.api.dependencies import get_llm

        model = get_llm(profile="agent-default")

    resolved_tools = list(get_default_tools() if tools is None else tools)
    resolved_middleware = list(build_agent_middleware(model))
    if middleware:
        resolved_middleware.extend(middleware)

    logger.info(
        "Building v1 search agent | model=%s | tools=%d | structured=%s | memory=%s",
        getattr(model, "model_name", getattr(model, "model", "unknown")),
        len(resolved_tools),
        response_format is not None,
        checkpointer is not None,
    )

    return create_agent(
        model=model,
        tools=resolved_tools,
        middleware=resolved_middleware,
        response_format=response_format,
        state_schema=state_schema,
        context_schema=context_schema,
        checkpointer=checkpointer,
        store=store,
        name="lingxi-search-agent",
    )


def build_extraction_agent(
    model: BaseChatModel,
    response_schema: type[BaseModel],
    *,
    strategy: Literal["tool", "provider"] = "tool",
    system_prompt: str = (
        "Extract structured information from the provided text accurately and completely."
    ),
) -> CompiledStateGraph:
    """Compile an extraction Agent with an explicit structured-output strategy."""

    if strategy == "tool":
        response_format: Any = ToolStrategy(
            response_schema,
            handle_errors=(
                "The structured output did not match the required schema. "
                "Correct every validation error and try again."
            ),
        )
    elif strategy == "provider":
        response_format = ProviderStrategy(response_schema, strict=True)
    else:  # defensive: the public request schema already validates this
        raise ValueError(f"Unsupported extraction strategy: {strategy}")

    logger.info(
        "Building extraction agent | model=%s | schema=%s | strategy=%s",
        getattr(model, "model_name", getattr(model, "model", "unknown")),
        response_schema.__name__,
        strategy,
    )

    return create_agent(
        model=model,
        tools=[],
        system_prompt=system_prompt,
        response_format=response_format,
        name=f"lingxi-extraction-{strategy}",
    )


def get_recursion_limit(max_iterations: int | None = None) -> int:
    """Translate the public iteration budget to LangGraph graph steps."""

    iterations = max_iterations or settings.max_iterations
    return iterations * 2 + 3
