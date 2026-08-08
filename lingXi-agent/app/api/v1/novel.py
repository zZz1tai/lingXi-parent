"""小说创作智能体：SSE 流式创作与持久会话管理端点。"""

from __future__ import annotations

from collections.abc import AsyncGenerator, AsyncIterator
from typing import Any, Mapping

from fastapi import APIRouter, Depends, Request
from fastapi.responses import StreamingResponse
from langchain_core.messages import AIMessage, ToolMessage
from langgraph.errors import GraphRecursionError

from app.agents.builder import get_recursion_limit
from app.agents.novel_prompts import (
    NOVEL_SYNOPSIS_SYSTEM_PROMPT,
    compose_novel_synopsis_prompt,
)
from app.agents.state import checkpoint_thread_id
from app.api.dependencies import (
    create_llm,
    create_novel_agent_context,
    delete_agent_thread,
    get_novel_agent,
    get_request_id,
)
from app.api.v1.chat import (
    _StreamBudgetExceeded,
    _SSE_HEARTBEAT_SECONDS,
    _aclose_source,
    _add_stream_text,
    _format_sse_event,
    _message_text,
    _messages_from_update,
    _normalize_content_blocks,
    _safe_custom_event,
    _safe_tool_input,
    _with_heartbeats,
)
from app.observability.tracing import with_trace
from app.schemas.request import (
    DeleteChatThreadRequest,
    NovelSynopsisRequest,
    NovelWriteRequest,
)
from app.schemas.response import (
    BaseResponse,
    NovelSynopsisData,
    NovelSynopsisResponse,
    StreamEvent,
)
from app.utils.exceptions import AgentError
from app.utils.logger import logger


router = APIRouter(prefix="/api/v1/novel", tags=["novel"])


class NovelMemoryDeleteError(AgentError):
    """小说作品持久会话记忆无法安全删除。"""

    def __init__(self) -> None:
        super().__init__(
            "Work conversation memory could not be deleted",
            code="MEMORY_DELETE_FAILED",
            status_code=503,
        )


def _build_novel_agent_config(
    request: NovelWriteRequest,
    *,
    request_id: str,
) -> dict[str, Any]:
    """构建递归限制和作品会话的检查点命名空间。"""

    user_namespace = request.user_id or "anonymous"
    internal_thread_id = checkpoint_thread_id(user_namespace, request.thread_id)
    return {
        "recursion_limit": get_recursion_limit(request.max_iterations),
        "configurable": {"thread_id": internal_thread_id},
        "metadata": {
            "request_id": request_id,
            "checkpoint_namespace": internal_thread_id,
            "user_id_length": len(user_namespace),
            "thread_id_length": len(request.thread_id),
        },
    }


def _novel_context_data(
    context: Mapping[str, Any] | None,
) -> dict[str, Any] | None:
    """规范化作品上下文为 JSON 安全字典。"""
    if not context:
        return None
    return dict(context)


async def _stream_novel_events(
    request: NovelWriteRequest,
    request_id: str,
) -> AsyncGenerator[str, None]:
    """将小说创作 Agent 的 LangChain v1 事件流转换为 SSE。"""

    emitted_text = False
    emitted_characters = 0
    streamed_text_parts: list[str] = []
    final_response = ""
    agent_stream: Any = None
    try:
        context = create_novel_agent_context(
            llm_config=request.llm_config,
            user_id=request.user_id,
            thread_id=request.thread_id,
            novel_context=_novel_context_data(
                request.work_context.model_dump(mode="json")
                if request.work_context is not None
                else None
            ),
        )
        agent = get_novel_agent(
            checkpointed=True,
            model=context.model,
        )
        agent_stream = agent.astream(
            {"messages": [{"role": "user", "content": request.message}]},
            config=with_trace(
                _build_novel_agent_config(request, request_id=request_id),
                "generate-novel-stream",
                user_id=request.user_id,
                thread_id=request.thread_id,
                tags=["novel"],
                metadata={
                    "request_id": request_id,
                    "work_id": (
                        request.work_context.work_id
                        if request.work_context is not None
                        else None
                    ),
                },
            ),
            context=context,
            stream_mode=["messages", "updates", "custom"],
        )
        async for stream_mode, chunk in agent_stream:
            if stream_mode == "messages":
                if not isinstance(chunk, tuple) or len(chunk) != 2:
                    continue
                message, metadata = chunk
                if not isinstance(message, AIMessage):
                    continue
                text = _message_text(message)
                if not text:
                    continue
                emitted_characters = _add_stream_text(emitted_characters, text)
                emitted_text = True
                streamed_text_parts.append(text)
                yield _format_sse_event(
                    StreamEvent(
                        type="token",
                        content=text,
                        content_blocks=_normalize_content_blocks(message)
                        or [{"type": "text", "text": text}],
                        data={
                            "node": (
                                str(metadata.get("langgraph_node") or "model")
                                if isinstance(metadata, dict)
                                else "model"
                            )
                        },
                        request_id=request_id,
                        thread_id=request.thread_id,
                    )
                )
                continue

            if stream_mode == "custom":
                yield _format_sse_event(
                    _safe_custom_event(
                        chunk,
                        request_id=request_id,
                        thread_id=request.thread_id,
                    )
                )
                continue

            if stream_mode != "updates":
                continue

            update_messages = _messages_from_update(chunk)
            for message in update_messages:
                if isinstance(message, AIMessage) and message.tool_calls:
                    for tool_call in message.tool_calls:
                        tool_name = str(tool_call.get("name") or "unknown")
                        yield _format_sse_event(
                            StreamEvent(
                                type="tool_start",
                                tool=tool_name,
                                tool_input=_safe_tool_input(
                                    tool_name,
                                    tool_call.get("args"),
                                ),
                                request_id=request_id,
                                thread_id=request.thread_id,
                            )
                        )
                elif isinstance(message, ToolMessage):
                    yield _format_sse_event(
                        StreamEvent(
                            type="tool_end",
                            tool=message.name or "unknown",
                            data={
                                "status": getattr(message, "status", "success")
                            },
                            request_id=request_id,
                            thread_id=request.thread_id,
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
                    thread_id=request.thread_id,
                )
            )

        yield _format_sse_event(
            StreamEvent(
                type="done",
                content=None if emitted_text else final_response,
                request_id=request_id,
                thread_id=request.thread_id,
            )
        )
    except _StreamBudgetExceeded:
        yield _format_sse_event(
            StreamEvent(
                type="error",
                content="Stream output limit exceeded",
                request_id=request_id,
                thread_id=request.thread_id,
            )
        )
    except GraphRecursionError:
        yield _format_sse_event(
            StreamEvent(
                type="error",
                content="Agent exceeded the configured iteration limit",
                request_id=request_id,
                thread_id=request.thread_id,
            )
        )
    except Exception as exc:  # noqa: BLE001
        logger.error(
            "Novel stream failed | request_id=%s | error_type=%s",
            request_id,
            type(exc).__name__,
        )
        yield _format_sse_event(
            StreamEvent(
                type="error",
                content="Agent stream failed",
                request_id=request_id,
                thread_id=request.thread_id,
            )
        )
    finally:
        await _aclose_source(agent_stream)


@router.post(
    "/write/stream",
    summary="SSE streaming novel writing",
    response_class=StreamingResponse,
    responses={
        200: {
            "description": "LangChain v1 message/update/custom event stream",
            "content": {"text/event-stream": {"schema": {"type": "string"}}},
        }
    },
)
async def novel_write_stream(
    request: NovelWriteRequest,
    http_request: Request,
    request_id: str = Depends(get_request_id),
) -> StreamingResponse:
    # 在启动 SSE 响应前校验并缓存本次请求选择的模型，确保出站地址策略
    # 校验失败时仍能返回常规 HTTP 422，而不是流内错误。
    if request.llm_config is not None:
        create_llm(request.llm_config, profile="novel-stream-preflight")

    async def event_generator() -> AsyncGenerator[str, None]:
        source: AsyncIterator[str] = _stream_novel_events(request, request_id)
        async for event in _with_heartbeats(
            source,
            http_request=http_request,
            request_id=request_id,
            thread_id=request.thread_id,
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


@router.post(
    "/synopsis/generate",
    response_model=NovelSynopsisResponse,
    summary="Generate a story synopsis from the work name",
)
async def novel_synopsis_generate(
    request: NovelSynopsisRequest,
    request_id: str = Depends(get_request_id),
) -> NovelSynopsisResponse:
    """根据书名（与可选题材）直接调用 LLM 生成 200~400 字故事梗概。

    不走 Agent 图：无需联网核查、无需会话记忆，也不写入任何历史记录；
    供新建作品表单在填好书名后一键自动拟写梗概使用。
    """
    try:
        llm = create_llm(request.llm_config, profile="novel-synopsis")
        messages = [
            ("system", NOVEL_SYNOPSIS_SYSTEM_PROMPT),
            (
                "user",
                compose_novel_synopsis_prompt(
                    work_name=request.work_name,
                    work_type=request.work_type,
                    genre=request.genre,
                ),
            ),
        ]
        response = await llm.ainvoke(messages)
        synopsis = getattr(response, "content", None)
        if isinstance(synopsis, list):
            text_parts = [
                block.get("text", "")
                for block in synopsis
                if isinstance(block, dict) and block.get("type") == "text"
            ]
            synopsis = "".join(text_parts)
        synopsis = str(synopsis or "").strip()
        if not synopsis:
            raise RuntimeError("model returned an empty synopsis")
    except AgentError:
        raise
    except Exception as exc:  # noqa: BLE001
        logger.error(
            "Novel synopsis generation failed | request_id=%s | error_type=%s",
            request_id,
            type(exc).__name__,
        )
        raise
    logger.info(
        "Novel synopsis generated | request_id=%s | work_name_length=%d",
        request_id,
        len(request.work_name),
    )
    return NovelSynopsisResponse(
        success=True,
        message="generated",
        data=NovelSynopsisData(synopsis=synopsis),
    )


@router.post(
    "/synopsis/stream",
    summary="Stream a story synopsis from the work name",
)
async def novel_synopsis_stream(
    request: NovelSynopsisRequest,
    request_id: str = Depends(get_request_id),
) -> StreamingResponse:
    """SSE 流式拟写梗概：与 /synopsis/generate 同构，仅改为逐 token 输出。

    不进入 Agent 图、不产生会话记忆；供新建作品表单一键拟写梗概时
    在文本框中逐字呈现。
    """

    async def event_source() -> AsyncIterator[str]:
        try:
            llm = create_llm(request.llm_config, profile="novel-synopsis")
            messages = [
                ("system", NOVEL_SYNOPSIS_SYSTEM_PROMPT),
                (
                    "user",
                    compose_novel_synopsis_prompt(
                        work_name=request.work_name,
                        work_type=request.work_type,
                        genre=request.genre,
                    ),
                ),
            ]
            full_parts: list[str] = []
            async for chunk in llm.astream(messages):
                raw_content = getattr(chunk, "content", "")
                if isinstance(raw_content, str):
                    text = raw_content
                else:
                    text = "".join(
                        block["text"]
                        for block in _normalize_content_blocks(chunk)
                    )
                if not text:
                    continue
                full_parts.append(text)
                yield _format_sse_event(StreamEvent(type="token", content=text))
            synopsis = "".join(full_parts).strip()
            if not synopsis:
                raise RuntimeError("model returned an empty synopsis")
            yield _format_sse_event(StreamEvent(type="done", content=synopsis))
            logger.info(
                "Novel synopsis streamed | request_id=%s | work_name_length=%d | chars=%d",
                request_id,
                len(request.work_name),
                len(synopsis),
            )
        except Exception as exc:  # noqa: BLE001
            logger.error(
                "Novel synopsis stream failed | request_id=%s | error_type=%s",
                request_id,
                type(exc).__name__,
            )
            yield _format_sse_event(
                StreamEvent(type="error", content="AI 拟写梗概失败，请稍后重试")
            )

    return StreamingResponse(
        event_source(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )


@router.delete(
    "/thread",
    response_model=BaseResponse,
    summary="Delete checkpointed novel work conversation",
)
async def delete_novel_thread(
    request: DeleteChatThreadRequest,
    request_id: str = Depends(get_request_id),
) -> BaseResponse:
    try:
        await delete_agent_thread(
            user_id=request.user_id,
            thread_id=request.thread_id,
        )
    except Exception as exc:  # noqa: BLE001
        logger.error(
            "Novel checkpoint deletion failed | request_id=%s | error_type=%s",
            request_id,
            type(exc).__name__,
        )
        raise NovelMemoryDeleteError() from exc
    logger.info(
        "Novel checkpoint deleted | request_id=%s | user_id_length=%d | thread_id_length=%d",
        request_id,
        len(request.user_id),
        len(request.thread_id),
    )
    return BaseResponse(success=True, message="deleted")
