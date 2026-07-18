"""
Agent construction module.

Encapsulates the ``create_react_agent`` API from LangGraph to build
reusable, configurable search agents with custom state, dynamic prompts,
and structured output support.
"""

from __future__ import annotations

from typing import Any, Callable, Optional, Sequence, Type

from langchain_core.language_models import BaseChatModel
from langchain_core.messages import BaseMessage
from langchain_core.tools import BaseTool
from langgraph.graph.message import add_messages
from langgraph.graph.state import CompiledStateGraph
from langgraph.managed import RemainingSteps
from langgraph.prebuilt import create_react_agent
from pydantic import BaseModel
from typing_extensions import Annotated, TypedDict

from app.agents.prompts import get_system_prompt
from app.agents.tools.web_search import get_default_tools
from app.config.settings import settings
from app.utils.logger import logger


# ── Custom State Schema ─────────────────────────────────────────────────────

class AgentState(TypedDict):
    """Extended agent state with custom fields.

    Compatible with LangGraph 0.6+ ``create_react_agent`` requirements.
    Includes the standard ``messages`` channel and ``remaining_steps``
    managed field, plus application-specific custom fields.
    """

    # Standard message channel — accumulates all messages via add_messages reducer
    messages: Annotated[list[BaseMessage], add_messages]

    # LangGraph-managed recursion budget.  This must retain the
    # ``RemainingSteps`` annotation so the runtime decrements it after every
    # graph step; a plain ``int`` silently disables that managed behaviour.
    remaining_steps: RemainingSteps

    # Custom fields — carried as metadata through the graph
    style: str                  # "professional" | "casual"
    user_id: str                # Caller-supplied user identifier (may be empty)
    business_tag: str           # Business context tag (may be empty)


# ── Agent Builder ───────────────────────────────────────────────────────────

def build_search_agent(
    model: Optional[BaseChatModel] = None,
    tools: Optional[Sequence[BaseTool]] = None,
    state_schema: Optional[Type] = None,
    response_format: Optional[Type[BaseModel]] = None,
    prompt_fn: Optional[Callable] = None,
) -> CompiledStateGraph:
    """Build a ReAct agent with web search capabilities.

    This is the central factory for creating agent instances. It wraps
    LangGraph's ``create_react_agent`` with sensible defaults and
    application-specific configuration.

    Args:
        model: LLM instance. If ``None``, the default model from
            ``get_llm()`` is used.
        tools: Tool list. If ``None``, the default web search tool set
            is used.
        state_schema: Custom state type. Defaults to ``AgentState``.
        response_format: Pydantic model for structured output. When
            provided, the agent will produce responses conforming to
            this schema via tool-calling strategy.
        prompt_fn: Callable that generates the system prompt from state.
            Defaults to the ``get_system_prompt`` dynamic prompt.

    Returns:
        A compiled LangGraph ``CompiledStateGraph`` ready for invocation.
    """
    # Resolve defaults
    if model is None:
        from app.api.dependencies import get_llm
        model = get_llm()

    if tools is None:
        tools = get_default_tools()

    if state_schema is None:
        state_schema = AgentState

    if prompt_fn is None:
        prompt_fn = get_system_prompt

    # Build agent kwargs
    agent_kwargs: dict[str, Any] = {
        "model": model,
        "tools": tools,
        "state_schema": state_schema,
        "prompt": prompt_fn,
    }

    # Add structured output if requested
    if response_format is not None:
        agent_kwargs["response_format"] = response_format

    logger.info(
        "Building search agent | model=%s | tools=%d | structured=%s",
        getattr(model, "model_name", "unknown"),
        len(tools),
        response_format is not None,
    )

    agent = create_react_agent(**agent_kwargs)
    return agent


def build_extraction_agent(
    model: BaseChatModel,
    response_schema: Type[BaseModel],
    system_prompt: str = "Extract structured information from the provided text accurately and completely.",
) -> CompiledStateGraph:
    """Build a lightweight agent specialized for structured extraction.

    Unlike the full search agent, this agent has no tools — it relies
    purely on the LLM's structured output capability.

    Args:
        model: LLM instance with structured output support.
        response_schema: Pydantic model defining the extraction schema.
        system_prompt: System prompt for the extraction task.

    Returns:
        A compiled LangGraph agent for extraction.
    """
    from langchain_core.messages import SystemMessage

    agent = create_react_agent(
        model=model,
        tools=[],
        prompt=SystemMessage(content=system_prompt),
        response_format=response_schema,
    )

    logger.info(
        "Building extraction agent | model=%s | schema=%s",
        getattr(model, "model_name", "unknown"),
        response_schema.__name__,
    )

    return agent


# ── Convenience: Build with max_iterations config ───────────────────────────

def get_recursion_limit(max_iterations: Optional[int] = None) -> int:
    """Calculate the LangGraph recursion limit from max_iterations.

    Each agent iteration consists of ~2 graph steps (agent node + tool
    node), plus a final response step. We multiply by 2 and add a small
    buffer.

    Args:
        max_iterations: Desired max iterations. Falls back to the
            global setting if ``None``.

    Returns:
        The recursion limit to pass in the runnable config.
    """
    iterations = max_iterations or settings.max_iterations
    return iterations * 2 + 1
