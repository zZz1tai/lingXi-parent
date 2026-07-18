"""First-class LangChain v1 middleware used by the retail Agent."""

from __future__ import annotations

import copy
from collections.abc import Awaitable, Callable
from typing import Any

from langchain.agents.middleware import (
    ModelRequest,
    ModelResponse,
    SummarizationMiddleware,
    wrap_model_call,
    wrap_tool_call,
)
from langchain_core.language_models import BaseChatModel
from langchain_core.messages import ToolMessage, get_buffer_string
from langgraph.prebuilt.tool_node import ToolCallRequest
from langgraph.types import Command

from app.agents.prompts import get_system_prompt
from app.agents.state import AgentContext
from app.utils.logger import logger


class RuntimeModelSummarizationMiddleware(SummarizationMiddleware):
    """Summarize with the same provider selected for the current request.

    A shallow delegate is created per call so concurrent requests never mutate
    the shared middleware's ``model``.  Summary failures leave the original
    state untouched; provider error text is never persisted as conversation
    memory.
    """

    def _delegate_for_runtime(self, runtime: Any) -> SummarizationMiddleware:
        context = getattr(runtime, "context", None)
        runtime_model = getattr(context, "model", None)
        delegate = copy.copy(self)
        if runtime_model is not None:
            delegate.model = runtime_model
        return delegate

    async def _acreate_summary(self, messages_to_summarize: list[Any]) -> str:
        """Generate a summary without persisting provider exception text."""

        if not messages_to_summarize:
            return "No previous conversation history."
        trimmed = self._trim_messages_for_summary(messages_to_summarize)
        if not trimmed:
            return "Previous conversation was too long to summarize."
        formatted = get_buffer_string(trimmed, format="xml")
        response = await self.model.ainvoke(
            self.summary_prompt.format(messages=formatted).rstrip(),
            config={"metadata": {"lc_source": "summarization"}},
        )
        return response.text.strip()

    def _create_summary(self, messages_to_summarize: list[Any]) -> str:
        """Synchronous equivalent that lets the outer safety boundary handle errors."""

        if not messages_to_summarize:
            return "No previous conversation history."
        trimmed = self._trim_messages_for_summary(messages_to_summarize)
        if not trimmed:
            return "Previous conversation was too long to summarize."
        formatted = get_buffer_string(trimmed, format="xml")
        response = self.model.invoke(
            self.summary_prompt.format(messages=formatted).rstrip(),
            config={"metadata": {"lc_source": "summarization"}},
        )
        return response.text.strip()

    async def abefore_model(
        self,
        state: Any,
        runtime: Any,
    ) -> dict[str, Any] | None:
        delegate = self._delegate_for_runtime(runtime)
        try:
            return await SummarizationMiddleware.abefore_model(
                delegate,
                state,
                runtime,
            )
        except Exception as exc:
            logger.error(
                "Conversation summarization failed | error_type=%s",
                type(exc).__name__,
            )
            return None

    def before_model(
        self,
        state: Any,
        runtime: Any,
    ) -> dict[str, Any] | None:
        delegate = self._delegate_for_runtime(runtime)
        try:
            return SummarizationMiddleware.before_model(delegate, state, runtime)
        except Exception as exc:
            logger.error(
                "Conversation summarization failed | error_type=%s",
                type(exc).__name__,
            )
            return None


@wrap_model_call
async def select_runtime_model(
    request: ModelRequest[AgentContext],
    handler: Callable[[ModelRequest[AgentContext]], Awaitable[ModelResponse]],
) -> ModelResponse:
    """Route a call to the bounded, request-selected model when present."""

    context = request.runtime.context if request.runtime is not None else None
    model = context.model if context is not None else None
    if model is not None and model is not request.model:
        request = request.override(model=model)
    return await handler(request)


@wrap_tool_call
async def handle_tool_errors(
    request: ToolCallRequest,
    handler: Callable[
        [ToolCallRequest],
        Awaitable[ToolMessage | Command[Any]],
    ],
) -> ToolMessage | Command[Any]:
    """Convert tool failures into a model-visible, call-ID-safe result."""

    try:
        return await handler(request)
    except Exception as exc:
        tool_name = str(request.tool_call.get("name") or "unknown")
        tool_call_id = str(request.tool_call.get("id") or "")
        logger.warning(
            "Tool call failed | tool=%s | error_type=%s",
            tool_name,
            type(exc).__name__,
        )
        return ToolMessage(
            content=(
                "工具暂时不可用。请基于已有信息回答，并明确说明未能完成该工具调用。"
            ),
            tool_call_id=tool_call_id,
            name=tool_name,
            status="error",
        )


def build_agent_middleware(model: BaseChatModel) -> list[Any]:
    """Build the ordered middleware stack for one compiled Agent graph."""

    return [
        RuntimeModelSummarizationMiddleware(
            model=model,
            trigger=("tokens", 12_000),
            keep=("tokens", 4_000),
        ),
        get_system_prompt,
        select_runtime_model,
        handle_tool_errors,
    ]
