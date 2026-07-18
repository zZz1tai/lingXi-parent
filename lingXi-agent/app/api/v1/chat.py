"""
Chat API endpoints — synchronous invoke and SSE streaming.

Provides:
- ``POST /api/v1/chat/invoke`` — Full response after agent completes
- ``POST /api/v1/chat/stream`` — Real-time SSE stream of agent execution
"""

from __future__ import annotations

import json
import time
from typing import Any, AsyncGenerator

from fastapi import APIRouter, Depends
from fastapi.responses import StreamingResponse
from langchain_core.messages import AIMessage, HumanMessage, ToolMessage

from app.agents.builder import get_recursion_limit
from app.api.dependencies import create_llm, get_agent, get_request_id
from app.chains.business_chat import (
    analyze_context,
    generate_smart_questions,
    stream_context_analysis,
)
from app.config.settings import settings
from app.schemas.request import ChatMode, ChatRequest, SmartQuestionsRequest
from app.schemas.response import (
    ChatData,
    ChatResponse,
    SmartQuestionsData,
    SmartQuestionsResponse,
    StreamEvent,
)
from app.utils.exceptions import AgentTimeoutError, SearchError
from app.utils.logger import logger

router = APIRouter(prefix="/api/v1/chat", tags=["chat"])


# ── Helper Functions ────────────────────────────────────────────────────────

def _build_agent_input(request: ChatRequest) -> dict[str, Any]:
    """Construct the input state dict for the agent from a chat request."""
    messages = [HumanMessage(content=request.message)]
    logger.info(
        "Building agent input | messages=%d | message_length=%d",
        len(messages),
        len(request.message),
    )
    
    # 打印当前使用的 style
    logger.info("Using style=%s for prompt", request.style)
    
    return {
        "messages": messages,
        "style": request.style,
        "user_id": request.user_id or "",
        "business_tag": request.business_tag or "",
    }


def _extract_tool_calls(messages: list) -> list[dict[str, Any]]:
    """Extract tool call records from the agent's message history."""
    tool_calls: list[dict[str, Any]] = []

    for msg in messages:
        if isinstance(msg, ToolMessage):
            tool_calls.append({
                "tool": msg.name or "unknown",
                "tool_call_id": msg.tool_call_id or "",
                "output": msg.content[:500] if msg.content else "",  # Truncate long outputs
            })

    return tool_calls


def _count_iterations(messages: list) -> int:
    """Count the number of agent iterations (AI messages with tool calls)."""
    count = 0
    for msg in messages:
        if isinstance(msg, AIMessage) and msg.tool_calls:
            count += 1
    return count


# ── Synchronous Invoke ──────────────────────────────────────────────────────

@router.post("/invoke", response_model=ChatResponse, summary="Synchronous chat")
async def chat_invoke(
    request: ChatRequest,
    request_id: str = Depends(get_request_id),
) -> ChatResponse:
    """Process a chat message synchronously and return the complete response.

    The agent will:
    1. Analyze the user's message
    2. Decide whether web search is needed
    3. Execute search if necessary
    4. Generate a final response

    The agent is bounded by ``max_iterations`` to prevent infinite loops.
    """
    start_time = time.time()

    if request.mode == ChatMode.CONTEXT_ANALYSIS:
        logger.info(
            "Context analysis | request_id=%s | message_length=%d",
            request_id,
            len(request.message),
        )
        try:
            llm = create_llm(request.llm_config)
            final_response = await analyze_context(
                llm,
                request.message,
                request.context_data,
            )
            elapsed = time.time() - start_time
            logger.info(
                "Context analysis completed | request_id=%s | elapsed=%.2fs | response_length=%d",
                request_id,
                elapsed,
                len(final_response),
            )
            return ChatResponse(
                success=True,
                message="ok",
                data=ChatData(
                    response=final_response,
                    tool_calls=[],
                    iterations=0,
                    request_id=request_id,
                ),
            )
        except Exception as exc:
            logger.error(
                "Context analysis failed | request_id=%s | error=%s",
                request_id,
                str(exc),
            )
            raise SearchError(f"Context analysis failed: {exc}") from exc

    logger.info(
        "Chat invoke | request_id=%s | style=%s | message_length=%d",
        request_id,
        request.style,
        len(request.message),
    )

    try:
        # 普通对话：使用 Agent
        agent = get_agent(llm_config=request.llm_config)
        input_data = _build_agent_input(request)
        config = {
            "recursion_limit": get_recursion_limit(request.max_iterations),
            "metadata": {"request_id": request_id},
        }

        result = await agent.ainvoke(input_data, config=config)

        # Extract the final AI response
        messages = result.get("messages", [])
        final_response = ""
        for msg in reversed(messages):
            if isinstance(msg, AIMessage) and msg.content and not msg.tool_calls:
                final_response = msg.content
                break

        elapsed = time.time() - start_time
        logger.info(
            "Chat invoke completed | request_id=%s | elapsed=%.2fs | iterations=%d | response_length=%d",
            request_id,
            elapsed,
            _count_iterations(messages),
            len(final_response),
        )
        logger.info("LLM response: %s", final_response[:200] if final_response else "empty")

        return ChatResponse(
            success=True,
            message="ok",
            data=ChatData(
                response=final_response,
                tool_calls=_extract_tool_calls(messages),
                iterations=_count_iterations(messages),
                request_id=request_id,
            ),
        )

    except Exception as exc:
        import traceback
        elapsed = time.time() - start_time
        logger.error(
            "Chat invoke failed | request_id=%s | elapsed=%.2fs | error=%s\n%s",
            request_id,
            elapsed,
            str(exc),
            traceback.format_exc(),
        )

        if "recursion_limit" in str(exc).lower() or "max_iterations" in str(exc).lower():
            raise AgentTimeoutError(
                f"Agent exceeded maximum iterations ({request.max_iterations or settings.max_iterations})"
            ) from exc

        raise SearchError(f"Agent execution failed: {exc}") from exc


@router.post(
    "/smart-questions",
    response_model=SmartQuestionsResponse,
    summary="Generate structured smart questions",
)
async def smart_questions(
    request: SmartQuestionsRequest,
    request_id: str = Depends(get_request_id),
) -> SmartQuestionsResponse:
    """Generate exactly three questions from transported conversation data."""

    try:
        llm = create_llm(
            request.llm_config,
            profile="smart-questions",
            temperature=0.2,
            max_retries=1,
        )
        questions = await generate_smart_questions(llm, request.chat_history)
        return SmartQuestionsResponse(
            success=True,
            message="ok",
            data=SmartQuestionsData(
                questions=questions,
                request_id=request_id,
            ),
        )
    except Exception as exc:
        logger.error(
            "Smart questions failed | request_id=%s | error=%s",
            request_id,
            str(exc),
        )
        raise SearchError(f"Smart questions failed: {exc}") from exc


# ── SSE Streaming ───────────────────────────────────────────────────────────

def _format_sse_event(event: StreamEvent) -> str:
    """Format a StreamEvent as an SSE data line."""
    data = event.model_dump(exclude_none=True)
    return f"data: {json.dumps(data, ensure_ascii=False)}\n\n"


async def _stream_context_analysis(
    request: ChatRequest,
    request_id: str,
) -> AsyncGenerator[str, None]:
    """Stream the explicit Python-owned context-analysis chain."""
    full_response = ""

    try:
        llm = create_llm(request.llm_config)
        async for content in stream_context_analysis(
            llm,
            request.message,
            request.context_data,
        ):
            full_response += content
            yield _format_sse_event(StreamEvent(
                type="token",
                content=content,
                request_id=request_id,
            ))

        yield _format_sse_event(StreamEvent(
            type="done",
            content=full_response,
            request_id=request_id,
        ))

    except Exception as exc:
        logger.error(
            "Context analysis stream error | request_id=%s | error=%s",
            request_id,
            str(exc),
        )
        yield _format_sse_event(StreamEvent(
            type="error",
            content=str(exc),
            request_id=request_id,
        ))


async def _stream_agent_events(
    request: ChatRequest,
    request_id: str,
) -> AsyncGenerator[str, None]:
    """Generate SSE events from the agent's execution stream.

    Uses ``astream_events`` (v2) for granular token-level streaming.
    Supports both ``values`` and ``messages`` stream modes internally.
    """
    full_response = ""

    try:
        agent = get_agent(llm_config=request.llm_config)
        input_data = _build_agent_input(request)
        config = {
            "recursion_limit": get_recursion_limit(request.max_iterations),
            "metadata": {"request_id": request_id},
        }
        async for event in agent.astream_events(
            input_data,
            config=config,
            version="v2",
        ):
            event_kind = event.get("event", "")

            # ── Token-level streaming (LLM output) ──
            if event_kind == "on_chat_model_stream":
                chunk = event.get("data", {}).get("chunk")
                if chunk and hasattr(chunk, "content") and chunk.content:
                    content = chunk.content
                    if isinstance(content, str):
                        full_response += content
                        yield _format_sse_event(StreamEvent(
                            type="token",
                            content=content,
                            request_id=request_id,
                        ))

            # ── Tool invocation start ──
            elif event_kind == "on_tool_start":
                tool_name = event.get("name", "unknown")
                tool_input = event.get("data", {}).get("input", {})
                logger.info(
                    "Tool start | request_id=%s | tool=%s",
                    request_id,
                    tool_name,
                )
                yield _format_sse_event(StreamEvent(
                    type="tool_start",
                    tool=tool_name,
                    tool_input=tool_input if isinstance(tool_input, dict) else {"query": str(tool_input)},
                    request_id=request_id,
                ))

            # ── Tool invocation end ──
            elif event_kind == "on_tool_end":
                tool_name = event.get("name", "unknown")
                tool_output = event.get("data", {}).get("output", "")
                logger.info(
                    "Tool end | request_id=%s | tool=%s",
                    request_id,
                    tool_name,
                )
                yield _format_sse_event(StreamEvent(
                    type="tool_end",
                    tool=tool_name,
                    tool_output=str(tool_output)[:1000] if tool_output else "",
                    request_id=request_id,
                ))

        # ── Stream complete ──
        yield _format_sse_event(StreamEvent(
            type="done",
            content=full_response,
            request_id=request_id,
        ))

    except Exception as exc:
        logger.error(
            "Stream error | request_id=%s | error=%s",
            request_id,
            str(exc),
        )
        yield _format_sse_event(StreamEvent(
            type="error",
            content=str(exc),
            request_id=request_id,
        ))


@router.post(
    "/stream",
    summary="SSE streaming chat",
    response_class=StreamingResponse,
    responses={
        200: {
            "description": "Server-Sent Events stream terminated by data: [DONE]",
            "content": {
                "text/event-stream": {
                    "schema": {"type": "string"},
                }
            },
        }
    },
)
async def chat_stream(
    request: ChatRequest,
    request_id: str = Depends(get_request_id),
) -> StreamingResponse:
    """Stream agent execution in real-time via Server-Sent Events.

    Event types:
    - ``token``: Individual text token from the LLM
    - ``tool_start``: A tool is about to be invoked
    - ``tool_end``: A tool has completed execution
    - ``done``: Agent execution complete, includes full response
    - ``error``: An error occurred during execution

    The stream ends with a ``[DONE]`` sentinel event.
    """
    logger.info(
        "Chat stream | request_id=%s | style=%s | mode=%s",
        request_id,
        request.style,
        request.mode.value,
    )

    async def event_generator() -> AsyncGenerator[str, None]:
        if request.mode == ChatMode.CONTEXT_ANALYSIS:
            async for event in _stream_context_analysis(request, request_id):
                yield event
        else:
            async for event in _stream_agent_events(request, request_id):
                yield event
        # SSE termination sentinel
        yield "data: [DONE]\n\n"

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",  # Disable nginx buffering
        },
    )
