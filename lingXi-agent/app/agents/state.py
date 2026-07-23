"""LangChain v1 状态与不可变运行时上下文契约。"""

from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass, field
from typing import Literal

from langchain.agents import AgentState
from langchain_core.language_models import BaseChatModel


class RetailAgentState(AgentState):
    """单个对话线程的可变、可检查点状态。

    LangChain 拥有消息归约器和结构化响应通道。
    调用方身份、响应风格和模型路由是调用元数据，
    因此它们有意放在 :class:`AgentContext` 中，
    而不是作为可变状态持久化。
    """


@dataclass(frozen=True, slots=True)
class AgentContext:
    """每次调用的不可变上下文，可供中间件和工具使用。"""

    user_id: str = ""
    thread_id: str = ""
    style: Literal["professional", "casual"] = "professional"
    business_tag: str = ""
    model: BaseChatModel | None = field(default=None, repr=False, compare=False)


def checkpoint_thread_id(user_id: str, thread_id: str) -> str:
    """构建明确且不包含个人身份信息的检查点命名空间。"""

    payload = json.dumps(
        [user_id or "anonymous", thread_id],
        ensure_ascii=False,
        separators=(",", ":"),
    )
    return "lingxi:" + hashlib.sha256(payload.encode("utf-8")).hexdigest()
