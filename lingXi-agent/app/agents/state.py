"""LangChain v1 state and immutable runtime context contracts."""

from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass, field
from typing import Literal

from langchain.agents import AgentState
from langchain_core.language_models import BaseChatModel


class RetailAgentState(AgentState):
    """Mutable, checkpointed state for one conversation thread.

    LangChain owns the message reducer and the structured-response channel.
    Caller identity, response style, and model routing are invocation metadata,
    so they intentionally live in :class:`AgentContext` instead of being
    persisted as mutable state.
    """


@dataclass(frozen=True, slots=True)
class AgentContext:
    """Immutable per-invocation context available to middleware and tools."""

    user_id: str = ""
    thread_id: str = ""
    style: Literal["professional", "casual"] = "professional"
    business_tag: str = ""
    model: BaseChatModel | None = field(default=None, repr=False, compare=False)


def checkpoint_thread_id(user_id: str, thread_id: str) -> str:
    """Build an unambiguous, non-PII checkpoint namespace."""

    payload = json.dumps(
        [user_id or "anonymous", thread_id],
        ensure_ascii=False,
        separators=(",", ":"),
    )
    return "lingxi:" + hashlib.sha256(payload.encode("utf-8")).hexdigest()
