"""零售 Agent 使用的一等 LangChain v1 中间件。"""

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
    """使用当前请求选定的提供商进行摘要。

    每次调用创建浅拷贝代理，确保并发请求不会修改
    共享中间件的 ``model``。摘要失败时保持原始状态不变；
    提供商错误文本不会作为对话记忆持久化。
    """

    def _delegate_for_runtime(self, runtime: Any) -> SummarizationMiddleware:
        """为当前运行时创建浅拷贝代理。"""
        context = getattr(runtime, "context", None)
        runtime_model = getattr(context, "model", None)
        delegate = copy.copy(self)
        if runtime_model is not None:
            delegate.model = runtime_model
        return delegate

    async def _acreate_summary(self, messages_to_summarize: list[Any]) -> str:
        """生成摘要，不持久化提供商异常文本。"""

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
        """同步等效方法，由外部安全边界处理错误。"""

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
        """异步模型调用前处理。"""
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
        """同步模型调用前处理。"""
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
    """当存在绑定的请求选定模型时，将调用路由到该模型。"""

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
    """将工具失败转换为模型可见且调用 ID 安全的结果。"""

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
    """为编译后的 Agent 图构建有序中间件栈。"""

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
