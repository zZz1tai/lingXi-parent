"""小说创作智能体：SSE 流式创作与持久会话管理端点。"""

from __future__ import annotations

from collections.abc import AsyncGenerator, AsyncIterator
from typing import Any, Mapping

from fastapi import APIRouter, Depends, Request
from fastapi.responses import StreamingResponse
from langchain_core.messages import AIMessage, ToolMessage
from langchain_core.output_parsers import JsonOutputParser
from langgraph.errors import GraphRecursionError

from app.agents.builder import get_recursion_limit
from app.agents.novel_idea import (
    IdeaTagScrubber,
    validate_idea_ask,
    validate_idea_doc,
)
from app.agents.novel_prompts import (
    NOVEL_CONTEXT_ANALYSIS_SYSTEM_PROMPT,
    NOVEL_OUTLINE_SYSTEM_PROMPT,
    NOVEL_PACING_ANALYSIS_SYSTEM_PROMPT,
    NOVEL_SYNOPSIS_SYSTEM_PROMPT,
    compose_novel_context_analysis_prompt,
    compose_novel_outline_prompt,
    compose_novel_pacing_analysis_prompt,
    compose_novel_polish_instruction,
    compose_novel_style_instruction,
    compose_novel_synopsis_prompt,
    parse_polish_message,
    parse_style_message,
)
from app.agents.state import checkpoint_thread_id
from app.api.dependencies import (
    create_llm,
    create_novel_agent_context,
    delete_agent_thread,
    get_novel_agent,
    get_novel_idea_agent,
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
    NovelContextAnalyzeRequest,
    NovelIdeaRequest,
    NovelOutlineRequest,
    NovelPacingRequest,
    NovelSynopsisRequest,
    NovelWriteRequest,
)
from app.schemas.response import (
    BaseResponse,
    NovelContextAnalyzeData,
    NovelContextAnalyzeResponse,
    NovelContextChange,
    NovelOutlineData,
    NovelOutlineResponse,
    NovelPacingData,
    NovelPacingResponse,
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
    request: NovelWriteRequest | NovelIdeaRequest,
    *,
    request_id: str,
) -> dict[str, Any]:
    """构建递归限制和会话检查点命名空间（创作/构思共用）。

    构思会话与创作会话同样按 (user_id, thread_id) 哈希隔离，
    因此同一用户可同时进行构思追问与正文创作而互不干扰。
    """

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


def _expand_polish_message(message: str) -> str:
    """把「【精修】<template_id>\\n<目标文字>」标记消息展开为完整精修指令。"""

    parsed = parse_polish_message(message)
    if parsed is None:
        return message
    template_id, target = parsed
    return compose_novel_polish_instruction(template_id, target)


def _expand_style_message(message: str) -> str:
    """把「【文风】<标题>\\n<内容>\\n\\n<目标文字>」标记展开为按文风改写指令。"""

    parsed = parse_style_message(message)
    if parsed is None:
        return message
    title, body, target = parsed
    return compose_novel_style_instruction(title, body, target)


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
        message = _expand_polish_message(_expand_style_message(request.message))
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
            {"messages": [{"role": "user", "content": message}]},
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


async def _stream_idea_events(
    request: NovelIdeaRequest,
    request_id: str,
) -> AsyncGenerator[str, None]:
    """将小说构思 Agent 的事件流转换为 SSE。

    构思 Agent 通过 ``[IDEA_ASK]``/``[IDEA_DOC]`` 结构化块表达
    追问与完成：普通对话文本按 token 转发，块被剥离成
    ``clarification``（追问，携带 questions）与 ``idea_doc``
    （构思文档）事件。
    """

    emitted_characters = 0
    agent_stream: Any = None
    try:
        context = create_novel_agent_context(
            llm_config=request.llm_config,
            user_id=request.user_id,
            thread_id=request.thread_id,
        )
        agent = get_novel_idea_agent(
            checkpointed=True,
            model=context.model,
        )
        agent_stream = agent.astream(
            {"messages": [{"role": "user", "content": request.message}]},
            config=with_trace(
                _build_novel_agent_config(request, request_id=request_id),
                "generate-novel-idea-stream",
                user_id=request.user_id,
                thread_id=request.thread_id,
                tags=["novel-idea"],
                metadata={"request_id": request_id},
            ),
            context=context,
            stream_mode=["messages", "updates", "custom"],
        )
        scrubber = IdeaTagScrubber()
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
                clean_text = scrubber.push(text)
                if clean_text:
                    emitted_characters = _add_stream_text(
                        emitted_characters, clean_text
                    )
                    yield _format_sse_event(
                        StreamEvent(
                            type="token",
                            content=clean_text,
                            # 只转发清洗后的文本，避免原始块协议 JSON
                            # 通过 content_blocks 旁路泄露给前端。
                            content_blocks=[{"type": "text", "text": clean_text}],
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

            nodes = list(chunk) if isinstance(chunk, dict) else []
            yield _format_sse_event(
                StreamEvent(
                    type="update",
                    data={"nodes": nodes},
                    request_id=request_id,
                    thread_id=request.thread_id,
                )
            )

        # 流结束：处理残留文本与已收集的块。
        tail = scrubber.flush_text()
        if tail:
            emitted_characters = _add_stream_text(emitted_characters, tail)
            yield _format_sse_event(
                StreamEvent(
                    type="token",
                    content=tail,
                    request_id=request_id,
                    thread_id=request.thread_id,
                )
            )

        for kind, data in scrubber.blocks:
            if kind == "ask":
                try:
                    payload = validate_idea_ask(data)
                except ValueError:
                    logger.warning(
                        "Invalid idea ask block ignored | request_id=%s",
                        request_id,
                    )
                    continue
                yield _format_sse_event(
                    StreamEvent(
                        type="clarification",
                        content="请回答以下问题，让我把构思补全。",
                        data={"questions": payload["questions"]},
                        request_id=request_id,
                        thread_id=request.thread_id,
                    )
                )
            else:
                try:
                    doc = validate_idea_doc(data)
                except ValueError:
                    logger.warning(
                        "Invalid idea doc block ignored | request_id=%s",
                        request_id,
                    )
                    continue
                yield _format_sse_event(
                    StreamEvent(
                        type="idea_doc",
                        content="构思已经完成，可以一键开书。",
                        data={"doc": doc},
                        request_id=request_id,
                        thread_id=request.thread_id,
                    )
                )

        yield _format_sse_event(
            StreamEvent(
                type="done",
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
            "Novel idea stream failed | request_id=%s | error_type=%s",
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
    "/idea/stream",
    summary="SSE streaming novel idea brainstorming",
    response_class=StreamingResponse,
    responses={
        200: {
            "description": (
                "Token stream with clarification (questions) and "
                "idea_doc events"
            ),
            "content": {"text/event-stream": {"schema": {"type": "string"}}},
        }
    },
)
async def novel_idea_stream(
    request: NovelIdeaRequest,
    http_request: Request,
    request_id: str = Depends(get_request_id),
) -> StreamingResponse:
    # 与创作端点一致：先校验本次请求选择的模型，出站策略失败返回 422。
    if request.llm_config is not None:
        create_llm(request.llm_config, profile="novel-stream-preflight")

    async def event_generator() -> AsyncGenerator[str, None]:
        source: AsyncIterator[str] = _stream_idea_events(request, request_id)
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


#: 各层级允许的子级映射，用于校验模型输出的三层大纲树。
_OUTLINE_CHILD_LEVELS: dict[str, str | None] = {
    "BOOK": "VOLUME",
    "VOLUME": "CHAPTER",
    "CHAPTER": None,
}


def _validate_outline_node(
    nodes: list[dict[str, Any]],
    *,
    expected_level: str,
    depth: int,
) -> None:
    """校验大纲树节点层级嵌套与章节号合法性。"""

    if depth > 4:
        raise ValueError("outline tree nesting is too deep")
    for node in nodes:
        if not isinstance(node, dict):
            raise ValueError(f"outline node must be an object, got {type(node).__name__}")
        if node.get("level") != expected_level:
            raise ValueError(
                f"outline node level mismatch, expected={expected_level}, "
                f"actual={node.get('level')!r}"
            )
        if not isinstance(node.get("title"), str) or not node["title"].strip():
            raise ValueError(f"outline {expected_level} node missing a title")
        if expected_level == "CHAPTER":
            chapter_no = node.get("chapterNo")
            if not isinstance(chapter_no, int) or chapter_no < 1:
                raise ValueError("CHAPTER outline node must carry a positive chapterNo")
        child_level = _OUTLINE_CHILD_LEVELS[expected_level]
        children = node.get("children") or []
        if child_level is None:
            if children:
                raise ValueError("CHAPTER outline node must not have children")
        else:
            _validate_outline_node(children, expected_level=child_level, depth=depth + 1)


def _validate_outline_payload(data: dict[str, Any]) -> dict[str, Any]:
    """校验并规范化模型输出的大纲载荷（tree + gaps）。"""

    tree = data.get("tree")
    if not isinstance(tree, list) or not tree:
        raise ValueError("model returned an empty outline tree")
    _validate_outline_node(tree, expected_level="BOOK", depth=0)
    gaps = data.get("gaps")
    if gaps is None:
        gaps = []
    if not isinstance(gaps, list):
        raise ValueError("gaps must be a list")
    valid_issues = {"ORPHAN_CHAPTER", "MISSING_CHAPTER", "MISMATCH"}
    for gap in gaps:
        if not isinstance(gap, dict):
            raise ValueError(f"gap item must be an object, got {type(gap).__name__}")
        if gap.get("issue") not in valid_issues:
            raise ValueError(f"invalid gap issue: {gap.get('issue')!r}")
    return {"tree": tree, "gaps": gaps}


@router.post(
    "/outline/generate",
    response_model=NovelOutlineResponse,
    summary="Generate a three-level novel outline with gap checking",
)
async def novel_outline_generate(
    request: NovelOutlineRequest,
    request_id: str = Depends(get_request_id),
) -> NovelOutlineResponse:
    """生成小说三层大纲（全书→卷→章）并检查大纲-章节断链。

    不走 Agent 图：直连白名单 LLM，一次性输出结构化大纲树与断链报告，
    供 Java 侧持久化后由人工确认；不产生会话记忆。
    """
    try:
        llm = create_llm(
            request.llm_config,
            profile="novel-outline",
            temperature=0.4,
            max_retries=2,
        )
        messages = [
            ("system", NOVEL_OUTLINE_SYSTEM_PROMPT),
            (
                "user",
                compose_novel_outline_prompt(
                    work_context=(
                        request.work_context.model_dump(mode="json")
                        if request.work_context is not None
                        else None
                    ),
                    chapters=[
                        chapter.model_dump(mode="json", exclude_none=True)
                        for chapter in request.chapters
                    ],
                    outline_tree=request.outline_tree,
                ),
            ),
        ]
        response = await llm.ainvoke(messages)
        content = getattr(response, "content", None)
        if isinstance(content, list):
            text_parts = [
                block.get("text", "")
                for block in content
                if isinstance(block, dict) and block.get("type") == "text"
            ]
            content = "".join(text_parts)
        raw_text = str(content or "").strip()
        if not raw_text:
            raise RuntimeError("model returned an empty outline response")
        parsed = JsonOutputParser().parse(raw_text)
        payload = _validate_outline_payload(parsed)
    except AgentError:
        raise
    except Exception as exc:  # noqa: BLE001
        logger.error(
            "Novel outline generation failed | request_id=%s | error_type=%s | "
            "chapters=%d",
            request_id,
            type(exc).__name__,
            len(request.chapters),
        )
        raise
    logger.info(
        "Novel outline generated | request_id=%s | chapters=%d | tree_nodes=%d | "
        "gaps=%d",
        request_id,
        len(request.chapters),
        len(payload["tree"]),
        len(payload["gaps"]),
    )
    return NovelOutlineResponse(
        success=True,
        message="generated",
        data=NovelOutlineData(
            tree=payload["tree"],
            gaps=payload["gaps"],
        ),
    )


#: 节奏分析允许的问题类型白名单。
_PACING_ISSUE_TYPES = {"PLODDING", "RUSHED", "MONOTONE", "PADDING", "NO_HOOK"}

#: 节奏分析固定的四个评估维度。
_PACING_DIMENSIONS = {"事件密度", "对话与动作", "情绪起伏", "段落节奏"}


def _validate_pacing_payload(data: dict[str, Any]) -> dict[str, Any]:
    """校验并规范化模型输出的节奏分析载荷。"""

    score = data.get("score")
    if not isinstance(score, int) or not 1 <= score <= 100:
        raise ValueError("score must be an integer between 1 and 100")
    level = data.get("level")
    if not isinstance(level, str) or level not in {
        "relaxed", "steady", "balanced", "intense", "rapid",
    }:
        raise ValueError(f"invalid pacing level: {level!r}")
    score_note = data.get("scoreNote") or data.get("score_note")
    level_note = data.get("levelNote") or data.get("level_note")
    summary = data.get("summary")
    if not isinstance(score_note, str) or not score_note.strip():
        raise ValueError("pacing analysis missing scoreNote")
    if not isinstance(level_note, str) or not level_note.strip():
        raise ValueError("pacing analysis missing levelNote")
    if not isinstance(summary, str) or not summary.strip():
        raise ValueError("pacing analysis missing summary")

    dimensions = data.get("dimensions") or []
    if not isinstance(dimensions, list):
        raise ValueError("dimensions must be a list")
    for dimension in dimensions:
        if not isinstance(dimension, dict):
            raise ValueError(f"dimension item must be an object, got {type(dimension).__name__}")
        if dimension.get("name") not in _PACING_DIMENSIONS:
            raise ValueError(f"invalid pacing dimension: {dimension.get('name')!r}")
        dim_score = dimension.get("score")
        if not isinstance(dim_score, int) or not 1 <= dim_score <= 100:
            raise ValueError("dimension score must be an integer between 1 and 100")
        if not isinstance(dimension.get("note"), str) or not dimension["note"].strip():
            raise ValueError("dimension note must be non-empty")

    issues = data.get("issues") or []
    if not isinstance(issues, list):
        raise ValueError("issues must be a list")
    for issue in issues:
        if not isinstance(issue, dict):
            raise ValueError(f"issue item must be an object, got {type(issue).__name__}")
        if issue.get("type") not in _PACING_ISSUE_TYPES:
            raise ValueError(f"invalid pacing issue type: {issue.get('type')!r}")
        if not isinstance(issue.get("issue"), str) or not issue["issue"].strip():
            raise ValueError("pacing issue description must be non-empty")
        if not isinstance(issue.get("suggestion"), str) or not issue["suggestion"].strip():
            raise ValueError("pacing issue suggestion must be non-empty")

    suggestions = data.get("suggestions") or []
    if not isinstance(suggestions, list) or not all(
        isinstance(item, str) and item.strip() for item in suggestions
    ):
        raise ValueError("suggestions must be a list of non-empty strings")

    return {
        "score": score,
        "score_note": score_note,
        "level": level,
        "level_note": level_note,
        "summary": summary,
        "dimensions": dimensions,
        "issues": issues,
        "suggestions": suggestions,
    }


@router.post(
    "/pacing/analyze",
    response_model=NovelPacingResponse,
    summary="Analyze chapter pacing with score and suggestions",
)
async def novel_pacing_analyze(
    request: NovelPacingRequest,
    request_id: str = Depends(get_request_id),
) -> NovelPacingResponse:
    """分析章节节奏：总分、实际档位、四维评分、问题清单与修改建议。

    不走 Agent 图：直连白名单 LLM，一次性输出结构化节奏分析，
    供前端节奏面板展示；建议与精修模板能力呼应，可一键跳转精修。
    """
    try:
        llm = create_llm(
            request.llm_config,
            profile="novel-pacing",
            temperature=0.3,
            max_retries=2,
        )
        messages = [
            ("system", NOVEL_PACING_ANALYSIS_SYSTEM_PROMPT),
            (
                "user",
                compose_novel_pacing_analysis_prompt(
                    work_name=request.work_name,
                    genre=request.genre,
                    chapter_title=request.chapter_title,
                    pacing_level=request.pacing_level,
                    content=request.content,
                ),
            ),
        ]
        response = await llm.ainvoke(messages)
        content = getattr(response, "content", None)
        if isinstance(content, list):
            text_parts = [
                block.get("text", "")
                for block in content
                if isinstance(block, dict) and block.get("type") == "text"
            ]
            content = "".join(text_parts)
        raw_text = str(content or "").strip()
        if not raw_text:
            raise RuntimeError("model returned an empty pacing analysis")
        parsed = JsonOutputParser().parse(raw_text)
        payload = _validate_pacing_payload(parsed)
    except AgentError:
        raise
    except Exception as exc:  # noqa: BLE001
        logger.error(
            "Novel pacing analysis failed | request_id=%s | error_type=%s | "
            "content_chars=%d",
            request_id,
            type(exc).__name__,
            len(request.content),
        )
        raise
    logger.info(
        "Novel pacing analyzed | request_id=%s | score=%d | level=%s | "
        "issues=%d",
        request_id,
        payload["score"],
        payload["level"],
        len(payload["issues"]),
    )
    return NovelPacingResponse(
        success=True,
        message="analyzed",
        data=NovelPacingData(
            score=payload["score"],
            score_note=payload["score_note"],
            level=payload["level"],
            level_note=payload["level_note"],
            summary=payload["summary"],
            dimensions=payload["dimensions"],
            issues=payload["issues"],
            suggestions=payload["suggestions"],
        ),
    )


def _validate_context_changes_payload(
    data: dict[str, Any],
    request: NovelContextAnalyzeRequest,
) -> list[NovelContextChange]:
    """校验模型候选变更的结构、目标归属与重复项。"""

    raw_changes = data.get("changes")
    if not isinstance(raw_changes, list):
        raise ValueError("changes must be a list")
    if len(raw_changes) > 20:
        raise ValueError("changes must not exceed 20 items")

    setting_ids = {item.setting_id for item in request.settings}
    foreshadow_ids = {item.foreshadow_id for item in request.foreshadows}
    existing_titles = {
        ("setting", item.title.strip().casefold()) for item in request.settings
    } | {
        ("foreshadow", item.title.strip().casefold())
        for item in request.foreshadows
    }
    seen_updates: set[tuple[str, int]] = set()
    seen_adds: set[tuple[str, str]] = set()
    changes: list[NovelContextChange] = []
    for raw_change in raw_changes:
        change = NovelContextChange.model_validate(raw_change)
        if change.operation == "UPDATE":
            allowed_ids = setting_ids if change.resource_type == "setting" else foreshadow_ids
            if change.target_id not in allowed_ids:
                raise ValueError(
                    f"unknown {change.resource_type} targetId: {change.target_id}"
                )
            update_key = (change.resource_type, int(change.target_id))
            if update_key in seen_updates:
                raise ValueError("the same target must not be updated twice")
            seen_updates.add(update_key)
        else:
            add_key = (change.resource_type, change.title.strip().casefold())
            if add_key in existing_titles or add_key in seen_adds:
                raise ValueError("ADD change duplicates an existing or proposed title")
            seen_adds.add(add_key)
        changes.append(change)
    return changes


@router.post(
    "/context/analyze",
    response_model=NovelContextAnalyzeResponse,
    summary="Analyze chapter changes to settings and foreshadows",
)
async def novel_context_analyze(
    request: NovelContextAnalyzeRequest,
    request_id: str = Depends(get_request_id),
) -> NovelContextAnalyzeResponse:
    """分析章节产生的长期资料变化，只返回待人工确认的 ADD/UPDATE 清单。"""

    try:
        llm = create_llm(
            request.llm_config,
            profile="novel-context-analysis",
            temperature=0.1,
            max_retries=2,
        )
        prompt_data = request.model_dump(
            mode="json",
            by_alias=True,
            exclude={"llm_config"},
            exclude_none=True,
        )
        response = await llm.ainvoke([
            ("system", NOVEL_CONTEXT_ANALYSIS_SYSTEM_PROMPT),
            ("user", compose_novel_context_analysis_prompt(prompt_data)),
        ])
        content = getattr(response, "content", None)
        if isinstance(content, list):
            content = "".join(
                block.get("text", "")
                for block in content
                if isinstance(block, dict) and block.get("type") == "text"
            )
        raw_text = str(content or "").strip()
        if not raw_text:
            raise RuntimeError("model returned an empty context analysis")
        parsed = JsonOutputParser().parse(raw_text)
        changes = _validate_context_changes_payload(parsed, request)
    except AgentError:
        raise
    except Exception as exc:  # noqa: BLE001
        logger.error(
            "Novel context analysis failed | request_id=%s | error_type=%s | "
            "chapter_id=%d",
            request_id,
            type(exc).__name__,
            request.chapter_id,
        )
        raise
    logger.info(
        "Novel context analyzed | request_id=%s | chapter_id=%d | changes=%d",
        request_id,
        request.chapter_id,
        len(changes),
    )
    return NovelContextAnalyzeResponse(
        success=True,
        message="analyzed",
        data=NovelContextAnalyzeData(changes=changes),
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
