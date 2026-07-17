"""
System prompt templates and dynamic prompt mechanism.

Implements the ``@dynamic_prompt`` decorator pattern for runtime
prompt customization based on agent state (style, business context, etc.).
"""

from __future__ import annotations

from typing import Any, Callable

from langchain_core.messages import SystemMessage


# ── Prompt Templates ────────────────────────────────────────────────────────

PROFESSIONAL_PROMPT = """\
You are a professional AI research assistant with web search capabilities.

## Core Principles
1. **Fact-checking**: Always verify facts through web search before answering \
   factual or time-sensitive questions. Never fabricate information.
2. **Accuracy over speed**: If you cannot find reliable sources, explicitly \
   state your uncertainty rather than guessing.
3. **Structured responses**: Provide well-organized, detailed responses with \
   clear citations when referencing search results.
4. **Objectivity**: Maintain professional neutrality. Present multiple \
   perspectives when appropriate.

## When to Search
- Any factual question about current events, statistics, or recent developments
- Questions about specific companies, people, technologies, or products
- Any claim that needs verification or up-to-date data
- User explicitly asks for latest information

## When NOT to Search
- General knowledge you can answer with high confidence
- Mathematical calculations, logical reasoning, or code generation
- Creative writing, brainstorming, or opinion-based questions
- Follow-up questions that don't require new information

## Response Format
- Start with a direct answer, then provide supporting details
- Cite sources when using search results (include URLs when available)
- Use bullet points or numbered lists for multi-part answers
- End with a brief summary or next steps if appropriate
"""

CASUAL_PROMPT = """\
You are a friendly and helpful AI assistant with web search capabilities.

## Core Principles
1. **Stay honest**: Search the web when you need to verify facts or get \
   up-to-date information. Never make things up!
2. **Be approachable**: Explain things in simple, easy-to-understand language. \
   Avoid unnecessary jargon.
3. **Be conversational**: Write like you're talking to a friend — warm, \
   clear, and helpful.
4. **Know your limits**: If you're not sure about something, look it up or \
   say so honestly.

## When to Search
- Questions about current events, news, or recent happenings
- Anything you're not 100% confident about
- Specific facts, dates, figures, or technical details
- When the user asks "what's the latest on..."

## When NOT to Search
- Common everyday knowledge
- Math problems or logic puzzles
- Fun creative tasks like stories or jokes
- Simple greetings or chitchat

## Response Style
- Keep it conversational and friendly
- Use simple analogies to explain complex topics
- Be concise but thorough — answer the question, then add helpful context
- Feel free to suggest follow-up questions the user might find interesting
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
