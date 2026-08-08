"""小说创作智能体工厂，编译独立的 LangChain v1 Agent。

与通用搜索 Agent 共用状态、上下文与工具错误边界，但使用
小说创作专用提示词中间件和会话摘要提示词，并仅装配创作
所需工具：本地通用工具 + 联网搜索（事实核查）。
"""

from __future__ import annotations

from collections.abc import Sequence
from typing import Any

from langchain.agents import create_agent
from langchain_core.language_models import BaseChatModel
from langchain_core.tools import BaseTool
from langgraph.checkpoint.base import BaseCheckpointSaver
from langgraph.graph.state import CompiledStateGraph
from langgraph.store.base import BaseStore

from app.agents.middleware import (
    RuntimeModelSummarizationMiddleware,
    handle_tool_errors,
    select_runtime_model,
)
from app.agents.novel_prompts import (
    NOVEL_GOAL_ORIENTED_SUMMARY_PROMPT,
    get_novel_system_prompt,
)
from app.agents.state import AgentContext, RetailAgentState
from app.agents.tools.general import create_general_tools
from app.agents.tools.web_search import get_default_tools
from app.utils.logger import logger


def build_novel_agent(
    model: BaseChatModel,
    *,
    tools: Sequence[BaseTool] | None = None,
    checkpointer: BaseCheckpointSaver | None = None,
    store: BaseStore | None = None,
    middleware: Sequence[Any] | None = None,
) -> CompiledStateGraph:
    """编译小说创作 Agent，装配专用提示词与记忆中间件。"""

    resolved_tools = list(get_default_tools() if tools is None else tools)
    resolved_middleware = [
        RuntimeModelSummarizationMiddleware(
            model=model,
            trigger=("tokens", 12_000),
            keep=("tokens", 4_000),
            summary_prompt=NOVEL_GOAL_ORIENTED_SUMMARY_PROMPT,
        ),
        get_novel_system_prompt,
        select_runtime_model,
        handle_tool_errors,
    ]
    if middleware:
        resolved_middleware.extend(middleware)

    logger.info(
        "Building novel agent | model=%s | tools=%d | memory=%s",
        getattr(model, "model_name", getattr(model, "model", "unknown")),
        len(resolved_tools),
        checkpointer is not None,
    )

    return create_agent(
        model=model,
        tools=resolved_tools,
        middleware=resolved_middleware,
        state_schema=RetailAgentState,
        context_schema=AgentContext,
        checkpointer=checkpointer,
        store=store,
        name="lingxi-novel-agent",
    )


def get_novel_tools() -> list[BaseTool]:
    """小说创作 Agent 的工具清单：通用本地工具 + 联网搜索。"""

    return list(create_general_tools()) + list(get_default_tools())
