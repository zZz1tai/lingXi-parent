"""LangChain v1 synchronous chat and multi-mode SSE streaming endpoints."""

from __future__ import annotations

import asyncio
import json
import time
from collections.abc import AsyncGenerator, AsyncIterator
from contextlib import suppress
from typing import Any

from fastapi import APIRouter, Depends, Request
from fastapi.responses import StreamingResponse
from langchain_core.messages import (
    AIMessage,
    AIMessageChunk,
    BaseMessage,
    HumanMessage,
    ToolMessage,
)
from langgraph.errors import GraphRecursionError

from app.agents.builder import get_recursion_limit
from app.api.dependencies import (
    create_agent_context,
    create_llm,
    delete_agent_thread,
    get_agent,
    get_request_id,
)
from app.agents.state import checkpoint_thread_id
from app.chains.business_chat import (
    analyze_context,
    generate_smart_questions,
    stream_context_analysis,
)
from app.config.settings import settings
from app.schemas.request import (
    ChatMode,
    ChatRequest,
    DeleteChatThreadRequest,
    SmartQuestionsRequest,
)
from app.schemas.response import (
    BaseResponse,
    ChatData,
    ChatResponse,
    SmartQuestionsData,
    SmartQuestionsResponse,
    StreamEvent,
    ToolCallRecord,
)
from app.utils.exceptions import AgentError, AgentTimeoutError, SearchError
from app.utils.logger import logger


router = APIRouter(prefix="/api/v1/chat", tags=["chat"])
_SSE_HEARTBEAT_SECONDS = 15.0


class _StreamBudgetExceeded(Exception):
    pass


class MemoryDeleteError(AgentError):
    """Durable conversation memory could not be deleted safely."""

    def __init__(self) -> None:
        super().__init__(
            "Conversation memory could not be deleted",
            code="MEMORY_DELETE_FAILED",
            status_code=503,
        )


def _add_stream_text(current: int, text: str) -> int:
    updated = current + len(text)
    if updated > settings.agent_stream_max_text_chars:
        raise _StreamBudgetExceeded
    return updated


async def _aclose_source(source: Any) -> None:
    close = getattr(source, "aclose", None)
    if callable(close):
        try:
            await close()
        except Exception as exc:
            logger.warning(
                "Failed to close async stream source | error_type=%s",
                type(exc).__name__,
            )


def _build_agent_input(request: ChatRequest) -> dict[str, Any]:
    """Only messages are mutable/checkpointed; metadata lives in context."""

    logger.info("Building agent input | message_length=%d", len(request.message))
    return {"messages": [HumanMessage(content=request.message)]}


def _public_thread_id(request: ChatRequest, request_id: str) -> str:
    """Use an explicit conversation ID or an isolated one-shot fallback."""

    return request.thread_id or request_id


def _build_agent_config(
    request: ChatRequest,
    *,
    request_id: str,
) -> dict[str, Any]:
    """Build recursion and trusted user/thread checkpoint namespaces."""

    public_thread_id = _public_thread_id(request, request_id)
    user_namespace = request.user_id or "anonymous"
    internal_thread_id = checkpoint_thread_id(user_namespace, public_thread_id)
    return {
        "recursion_limit": get_recursion_limit(request.max_iterations),
        "configurable": {"thread_id": internal_thread_id},
        "metadata": {
            "request_id": request_id,
            "checkpoint_namespace": internal_thread_id,
            "user_id_length": len(request.user_id or ""),
            "thread_id_length": len(public_thread_id),
        },
    }


def _normalize_content_blocks(message: Any) -> list[dict[str, Any]]:
    """Return model-neutral text blocks without exposing reasoning blocks."""

    blocks = getattr(message, "content_blocks", None)
    if callable(blocks):
        blocks = blocks()
    if not isinstance(blocks, list):
        content = getattr(message, "content", None)
        blocks = content if isinstance(content, list) else []

    normalized: list[dict[str, Any]] = []
    for block in blocks:
        if hasattr(block, "model_dump"):
            block = block.model_dump()
        if not isinstance(block, dict):
            continue
        block_type = str(block.get("type") or "")
        if block_type not in {"text", "text_delta"}:
            continue
        text = block.get("text")
        if isinstance(text, str) and text:
            normalized.append({"type": "text", "text": text})
    return normalized


def _message_text(message: Any) -> str:
    """Extract display text from legacy strings or v1 standard content blocks."""

    content = getattr(message, "content", "")
    if isinstance(content, str):
        return content
    return "".join(block["text"] for block in _normalize_content_blocks(message))


def _extract_tool_calls(messages: list[BaseMessage]) -> list[ToolCallRecord]:
    records: list[ToolCallRecord] = []
    for message in messages:
        if not isinstance(message, ToolMessage):
            continue
        records.append(
            ToolCallRecord(
                tool=message.name or "unknown",
                tool_call_id=message.tool_call_id or "",
                output=_message_text(message)[:1_000],
                artifact=getattr(message, "artifact", None),
                status=getattr(message, "status", "success") or "success",
            )
        )
    return records


def _count_iterations(messages: list[BaseMessage]) -> int:
    return sum(
        1
        for message in messages
        if isinstance(message, AIMessage) and bool(message.tool_calls)
    )


def _final_ai_response(messages: list[BaseMessage]) -> str:
    for message in reversed(messages):
        if isinstance(message, AIMessage) and not message.tool_calls:
            text = _message_text(message).strip()
            if text:
                return text
    return ""


@router.delete(
    "/thread",
    response_model=BaseResponse,
    summary="Delete checkpointed chat memory",
)
async def delete_chat_thread(
    request: DeleteChatThreadRequest,
    request_id: str = Depends(get_request_id),
) -> BaseResponse:
    try:
        await delete_agent_thread(
            user_id=request.user_id,
            thread_id=request.thread_id,
        )
    except Exception as exc:
        logger.error(
            "Checkpoint deletion failed | request_id=%s | error_type=%s",
            request_id,
            type(exc).__name__,
        )
        raise MemoryDeleteError() from exc
    logger.info(
        "Checkpoint deleted | request_id=%s | user_id_length=%d | thread_id_length=%d",
        request_id,
        len(request.user_id),
        len(request.thread_id),
    )
    return BaseResponse(success=True, message="deleted")


@router.post("/invoke", response_model=ChatResponse, summary="Synchronous chat")
async def chat_invoke(
    request: ChatRequest,
    request_id: str = Depends(get_request_id),
) -> ChatResponse:
    start_time = time.perf_counter()
    public_thread_id = _public_thread_id(request, request_id)

    if request.mode == ChatMode.CONTEXT_ANALYSIS:
        try:
            llm = create_llm(request.llm_config, profile="context-analysis")
            final_response = await analyze_context(
                llm,
                request.message,
                request.context_data,
            )
            return ChatResponse(
                success=True,
                message="ok",
                data=ChatData(
                    response=final_response,
                    tool_calls=[],
                    iterations=0,
                    request_id=request_id,
                    thread_id=public_thread_id,
                ),
            )
        except AgentError:
            raise
        except Exception as exc:
            logger.error(
                "Context analysis failed | request_id=%s | error_type=%s",
                request_id,
                type(exc).__name__,
            )
            raise SearchError("Context analysis failed") from exc

    try:
        agent = get_agent(checkpointed=request.thread_id is not None)
        context = create_agent_context(
            llm_config=request.llm_config,
            user_id=request.user_id or "",
            thread_id=public_thread_id,
            style=request.style,
            business_tag=request.business_tag or "",
        )
        result = await agent.ainvoke(
            _build_agent_input(request),
            config=_build_agent_config(request, request_id=request_id),
            context=context,
        )
        messages = list(result.get("messages") or [])
        final_response = _final_ai_response(messages)
        if not final_response:
            raise SearchError("Agent returned no displayable answer")

        logger.info(
            "Chat invoke completed | request_id=%s | elapsed=%.2fs | iterations=%d | response_length=%d",
            request_id,
            time.perf_counter() - start_time,
            _count_iterations(messages),
            len(final_response),
        )
        return ChatResponse(
            success=True,
            message="ok",
            data=ChatData(
                response=final_response,
                tool_calls=_extract_tool_calls(messages),
                iterations=_count_iterations(messages),
                request_id=request_id,
                thread_id=public_thread_id,
            ),
        )
    except GraphRecursionError as exc:
        raise AgentTimeoutError(
            f"Agent exceeded maximum iterations ({request.max_iterations or settings.max_iterations})"
        ) from exc
    except AgentError:
        raise
    except Exception as exc:
        logger.error(
            "Chat invoke failed | request_id=%s | elapsed=%.2fs | error_type=%s",
            request_id,
            time.perf_counter() - start_time,
            type(exc).__name__,
        )
        raise SearchError("Agent execution failed") from exc


@router.post(
    "/smart-questions",
    response_model=SmartQuestionsResponse,
    summary="Generate structured smart questions",
)
async def smart_questions(
    request: SmartQuestionsRequest,
    request_id: str = Depends(get_request_id),
) -> SmartQuestionsResponse:
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
            data=SmartQuestionsData(questions=questions, request_id=request_id),
        )
    except AgentError:
        raise
    except Exception as exc:
        logger.error(
            "Smart questions failed | request_id=%s | error_type=%s",
            request_id,
            type(exc).__name__,
        )
        raise SearchError("Smart question generation failed") from exc


def _format_sse_event(event: StreamEvent) -> str:
    data = event.model_dump(mode="json", exclude_none=True)
    return f"data: {json.dumps(data, ensure_ascii=False, separators=(',', ':'))}\n\n"


async def _stream_context_analysis(
    request: ChatRequest,
    request_id: str,
) -> AsyncGenerator[str, None]:
    public_thread_id = _public_thread_id(request, request_id)
    emitted_text = False
    emitted_characters = 0
    context_stream: Any = None
    try:
        llm = create_llm(request.llm_config, profile="context-analysis-stream")
        context_stream = stream_context_analysis(
            llm,
            request.message,
            request.context_data,
        )
        async for content in context_stream:
            if not content:
                continue
            emitted_characters = _add_stream_text(emitted_characters, content)
            emitted_text = True
            yield _format_sse_event(
                StreamEvent(
                    type="token",
                    content=content,
                    content_blocks=[{"type": "text", "text": content}],
                    request_id=request_id,
                    thread_id=public_thread_id,
                )
            )
        yield _format_sse_event(
            StreamEvent(
                type="done",
                content=None if emitted_text else "",
                request_id=request_id,
                thread_id=public_thread_id,
            )
        )
    except _StreamBudgetExceeded:
        yield _format_sse_event(
            StreamEvent(
                type="error",
                content="Stream output limit exceeded",
                request_id=request_id,
                thread_id=public_thread_id,
            )
        )
    except Exception as exc:
        logger.error(
            "Context stream failed | request_id=%s | error_type=%s",
            request_id,
            type(exc).__name__,
        )
        yield _format_sse_event(
            StreamEvent(
                type="error",
                content="Context analysis stream failed",
                request_id=request_id,
                thread_id=public_thread_id,
            )
        )
    finally:
        await _aclose_source(context_stream)


def _messages_from_update(update: Any) -> list[BaseMessage]:
    messages: list[BaseMessage] = []
    if not isinstance(update, dict):
        return messages
    for node_update in update.values():
        if not isinstance(node_update, dict):
            continue
        value = node_update.get("messages")
        if isinstance(value, BaseMessage):
            messages.append(value)
        elif isinstance(value, list):
            messages.extend(item for item in value if isinstance(item, BaseMessage))
    return messages


def _json_safe(value: Any) -> Any:
    try:
        json.dumps(value)
        return value
    except (TypeError, ValueError):
        return str(value)[:2_000]


async def _stream_agent_events(
    request: ChatRequest,
    request_id: str,
) -> AsyncGenerator[str, None]:
    """Translate LangChain v1 messages/updates/custom modes into SSE."""

    public_thread_id = _public_thread_id(request, request_id)
    emitted_text = False
    emitted_characters = 0
    final_response = ""
    agent_stream: Any = None
    try:
        agent = get_agent(checkpointed=request.thread_id is not None)
        context = create_agent_context(
            llm_config=request.llm_config,
            user_id=request.user_id or "",
            thread_id=public_thread_id,
            style=request.style,
            business_tag=request.business_tag or "",
        )
        agent_stream = agent.astream(
            _build_agent_input(request),
            config=_build_agent_config(request, request_id=request_id),
            context=context,
            stream_mode=["messages", "updates", "custom"],
        )
        async for stream_mode, chunk in agent_stream:
            if stream_mode == "messages":
                if not isinstance(chunk, tuple) or len(chunk) != 2:
                    continue
                message, metadata = chunk
                if not isinstance(message, (AIMessage, AIMessageChunk)):
                    continue
                text = _message_text(message)
                if not text:
                    continue
                emitted_characters = _add_stream_text(emitted_characters, text)
                emitted_text = True
                yield _format_sse_event(
                    StreamEvent(
                        type="token",
                        content=text,
                        content_blocks=_normalize_content_blocks(message) or [
                            {"type": "text", "text": text}
                        ],
                        data={
                            "node": str(metadata.get("langgraph_node") or "model")
                            if isinstance(metadata, dict)
                            else "model"
                        },
                        request_id=request_id,
                        thread_id=public_thread_id,
                    )
                )
                continue

            if stream_mode == "custom":
                yield _format_sse_event(
                    StreamEvent(
                        type="custom",
                        data=_json_safe(chunk),
                        request_id=request_id,
                        thread_id=public_thread_id,
                    )
                )
                continue

            if stream_mode != "updates":
                continue

            update_messages = _messages_from_update(chunk)
            for message in update_messages:
                if isinstance(message, AIMessage) and message.tool_calls:
                    for tool_call in message.tool_calls:
                        yield _format_sse_event(
                            StreamEvent(
                                type="tool_start",
                                tool=str(tool_call.get("name") or "unknown"),
                                tool_input=tool_call.get("args")
                                if isinstance(tool_call.get("args"), dict)
                                else {},
                                request_id=request_id,
                                thread_id=public_thread_id,
                            )
                        )
                elif isinstance(message, ToolMessage):
                    yield _format_sse_event(
                        StreamEvent(
                            type="tool_end",
                            tool=message.name or "unknown",
                            tool_output=_message_text(message)[:1_000],
                            data={
                                "status": getattr(message, "status", "success"),
                                "artifact": _json_safe(getattr(message, "artifact", None)),
                            },
                            request_id=request_id,
                            thread_id=public_thread_id,
                        )
                    )
                elif isinstance(message, AIMessage) and not message.tool_calls:
                    candidate = _message_text(message).strip()
                    if candidate:
                        final_response = candidate

            nodes = list(chunk) if isinstance(chunk, dict) else []
            yield _format_sse_event(
                StreamEvent(
                    type="update",
                    data={"nodes": nodes},
                    request_id=request_id,
                    thread_id=public_thread_id,
                )
            )

        if not emitted_text and final_response:
            _add_stream_text(0, final_response)
        yield _format_sse_event(
            StreamEvent(
                type="done",
                # Token events already carried text.  Avoid appending the same
                # answer a second time in clients that concatenate all content.
                content=None if emitted_text else final_response,
                request_id=request_id,
                thread_id=public_thread_id,
            )
        )
    except _StreamBudgetExceeded:
        yield _format_sse_event(
            StreamEvent(
                type="error",
                content="Stream output limit exceeded",
                request_id=request_id,
                thread_id=public_thread_id,
            )
        )
    except GraphRecursionError:
        yield _format_sse_event(
            StreamEvent(
                type="error",
                content="Agent exceeded the configured iteration limit",
                request_id=request_id,
                thread_id=public_thread_id,
            )
        )
    except Exception as exc:
        logger.error(
            "Agent stream failed | request_id=%s | error_type=%s",
            request_id,
            type(exc).__name__,
        )
        yield _format_sse_event(
            StreamEvent(
                type="error",
                content="Agent stream failed",
                request_id=request_id,
                thread_id=public_thread_id,
            )
        )
    finally:
        await _aclose_source(agent_stream)


async def _with_heartbeats(
    source: AsyncIterator[str],
    *,
    http_request: Request,
    request_id: str,
    thread_id: str,
) -> AsyncGenerator[str, None]:
    """Cancel Agent work on disconnect and keep idle SSE connections alive."""

    queue: asyncio.Queue[str | None] = asyncio.Queue(maxsize=16)

    async def produce() -> None:
        try:
            async for event in source:
                await queue.put(event)
        finally:
            # Never block cleanup on a full queue.  If the sentinel cannot be
            # inserted, the consumer also observes ``producer.done()`` after
            # draining the queued events.
            with suppress(asyncio.QueueFull):
                queue.put_nowait(None)

    producer = asyncio.create_task(produce())
    deadline = asyncio.get_running_loop().time() + settings.agent_stream_max_seconds
    try:
        while True:
            if await http_request.is_disconnected():
                producer.cancel()
                return
            if producer.done() and queue.empty():
                return
            remaining = deadline - asyncio.get_running_loop().time()
            if remaining <= 0:
                producer.cancel()
                yield _format_sse_event(
                    StreamEvent(
                        type="error",
                        content="Stream time limit exceeded",
                        request_id=request_id,
                        thread_id=thread_id,
                    )
                )
                return
            try:
                event = await asyncio.wait_for(
                    queue.get(),
                    timeout=min(_SSE_HEARTBEAT_SECONDS, remaining),
                )
            except TimeoutError:
                if asyncio.get_running_loop().time() >= deadline:
                    producer.cancel()
                    yield _format_sse_event(
                        StreamEvent(
                            type="error",
                            content="Stream time limit exceeded",
                            request_id=request_id,
                            thread_id=thread_id,
                        )
                    )
                    return
                yield _format_sse_event(
                    StreamEvent(
                        type="heartbeat",
                        request_id=request_id,
                        thread_id=thread_id,
                    )
                )
                continue
            if event is None:
                return
            yield event
    finally:
        producer.cancel()
        try:
            with suppress(asyncio.CancelledError):
                await producer
        finally:
            # ``async for`` does not close its iterator when cancellation lands
            # while ``produce`` is blocked on ``queue.put``.  Close explicitly
            # so LangGraph/provider streams are released deterministically.
            await _aclose_source(source)


@router.post(
    "/stream",
    summary="SSE streaming chat",
    response_class=StreamingResponse,
    responses={
        200: {
            "description": "LangChain v1 message/update/custom event stream",
            "content": {"text/event-stream": {"schema": {"type": "string"}}},
        }
    },
)
async def chat_stream(
    request: ChatRequest,
    http_request: Request,
    request_id: str = Depends(get_request_id),
) -> StreamingResponse:
    public_thread_id = _public_thread_id(request, request_id)
    # Validate and cache a request-selected model before the SSE response is
    # started so an outbound URL policy failure remains a normal HTTP 422.
    if request.llm_config is not None:
        create_llm(request.llm_config, profile="chat-stream-preflight")

    async def event_generator() -> AsyncGenerator[str, None]:
        source: AsyncIterator[str]
        if request.mode == ChatMode.CONTEXT_ANALYSIS:
            source = _stream_context_analysis(request, request_id)
        else:
            source = _stream_agent_events(request, request_id)

        async for event in _with_heartbeats(
            source,
            http_request=http_request,
            request_id=request_id,
            thread_id=public_thread_id,
        ):
            yield event

        if not await http_request.is_disconnected():
            yield "data: [DONE]\n\n"

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache, no-transform",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )
