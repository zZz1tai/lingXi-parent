"""
System prompt templates and dynamic prompt mechanism.

Implements the ``@dynamic_prompt`` decorator pattern for runtime
prompt customization based on agent state (style, business context, etc.).
"""

from __future__ import annotations

from typing import Any, Callable

from langchain_core.messages import SystemMessage
from app.utils.logger import logger


# ── Prompt Templates ────────────────────────────────────────────────────────

PROFESSIONAL_PROMPT = """\
你是灵犀智能零售终端管理系统的AI助手，具备联网搜索能力。请用中文回答。

## 重要规则
1. **用户消息中可能包含数据**：如果用户消息包含"数据看板信息"、"工单统计"、"销售统计"等内容，必须基于这些数据回答问题
2. **直接回答问题**：用户问什么就回答什么，不要只说"你好"就结束了
3. **数据分析**：用户问销售、设备、订单等问题时，直接给出分析和建议
4. **联网搜索**：需要最新信息时使用搜索功能
5. **简洁专业**：回答要简洁有用，不要废话

## 回答格式
- 先给出直接回答，再提供详细说明
- 引用数据时注明来源
"""

CASUAL_PROMPT = """\
你是灵犀智能零售终端管理系统的AI助手，像朋友一样和用户聊天。请用中文回答。

## 回答规则
- 用户说"你好"时，热情地打招呼并问有什么可以帮忙的
- 保持友好、自然、乐于助人
- 用简单易懂的语言解释事物
- 不确定的事情可以说"我帮你查一下"
- 闲聊时轻松友好，工作问题时认真专业
"""


# ── Dynamic Prompt Decorator ────────────────────────────────────────────────

def dynamic_prompt(func: Callable[..., Any]) -> Callable[..., Any]:
    """Decorator that marks a function as a dynamic prompt provider.

    The decorated function receives the agent state dict and should
    return either a ``SystemMessage`` or a plain string that will be
    used as the system prompt for that invocation.

    Usage::

        @dynamic_prompt
        def my_prompt(state: dict) -> list:
            style = state.get("style", "professional")
            return [SystemMessage(content=...)]

    The ``_is_dynamic_prompt`` attribute is set so that other parts
    of the system can introspect whether a callable is a dynamic
    prompt provider.
    """
    func._is_dynamic_prompt = True  # type: ignore[attr-defined]
    return func


# ── Dynamic Prompt Implementation ───────────────────────────────────────────

@dynamic_prompt
def get_system_prompt(state: dict[str, Any]) -> list:
    """Generate a dynamic system prompt based on agent state.

    Examines the ``style`` and ``business_tag`` fields in the state
    to select and customize the system prompt at runtime.

    Args:
        state: The current agent state dictionary. Expected keys:
            - ``style``: ``"professional"`` or ``"casual"``
            - ``business_tag``: Optional business context string

    Returns:
        A list containing a ``SystemMessage`` with the composed prompt.
    """
    style: str = state.get("style", "professional")

    # Select base prompt template
    if style == "casual":
        base_prompt = CASUAL_PROMPT
    else:
        base_prompt = PROFESSIONAL_PROMPT

    logger.info("System prompt selected | style=%s | prompt_length=%d", style, len(base_prompt))

    # Append business context if available
    business_tag: str | None = state.get("business_tag")
    if business_tag:
        base_prompt += f"\n\n## Current Business Context\n{business_tag}"

    return [SystemMessage(content=base_prompt)]


def get_prompt_text(style: str = "professional") -> str:
    """Get the raw prompt text for a given style (utility function).

    Useful for endpoints that need to inject the system prompt
    directly into the message list rather than through the
    agent's prompt mechanism.

    Args:
        style: ``"professional"`` or ``"casual"``

    Returns:
        The prompt text as a plain string.
    """
    if style == "casual":
        return CASUAL_PROMPT
    return PROFESSIONAL_PROMPT
