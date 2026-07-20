"""
Chapter analysis API endpoint.

Migrated from Java AiVideoChapterAnalysisWorker.
Handles source unit building, prompt construction, LLM call, and JSON validation.
"""

from __future__ import annotations

import asyncio
import inspect
import json
import re
import time
from collections.abc import Awaitable, Callable, Iterator
from typing import Optional

from fastapi import APIRouter, Depends, Response
from fastapi.responses import StreamingResponse
from httpx import TimeoutException as HttpxTimeoutException, TransportError
from openai import APIConnectionError, APIStatusError, APITimeoutError, RateLimitError

from app.api.dependencies import create_llm, get_request_id
from app.chains.chapter_analysis import (
    ChapterAnalysisOutputError,
    ChapterAnalysisOutputTooLargeError,
    build_chapter_analysis_chain,
)
from app.schemas.chapter import AnalyzeChapterRequest, AnalyzeChapterResponse
from app.security.outbound import validate_outbound_http_url
from app.services.chapter_analysis import (
    build_planning_context,
    build_prompt,
    build_source_units,
)
from app.utils.exceptions import (
    ConfigurationError,
    InputValidationError,
    ModelNotAvailableError,
)
from app.utils.logger import logger

router = APIRouter(prefix="/api/v1/video", tags=["chapter"])
CHAPTER_MAX_CONCURRENCY = 4
CHAPTER_SLOT_WAIT_SECONDS = 5.0
_chapter_slots = asyncio.BoundedSemaphore(CHAPTER_MAX_CONCURRENCY)
CONTRACT_ERROR_LOG_LIMIT = 600


class _ChapterConfigurationError(ValueError):
    """The Java-to-Python chapter request omitted required runtime config."""


class _ChapterCapacityError(RuntimeError):
    """The per-worker chapter provider capacity is temporarily exhausted."""


def _iter_exception_chain(exc: BaseException) -> Iterator[BaseException]:
    """Yield an exception and its explicit/implicit causes without looping."""
    current: Optional[BaseException] = exc
    seen: set[int] = set()
    while current is not None and id(current) not in seen:
        seen.add(id(current))
        yield current
        current = current.__cause__ or current.__context__


def _safe_contract_error_detail(exc: BaseException) -> str:
    """Return a short, log-safe contract failure summary without model payloads."""

    detail = str(exc)[:4_000]
    detail = re.sub(
        r"(?i)input_value=.*?(?=,\s*input_type=|$)",
        "input_value=[REDACTED]",
        detail,
        flags=re.DOTALL,
    )
    detail = re.sub(r"(?i)(api[_-]?key|authorization|bearer)\s*[:=]\s*\S+", r"\1=[REDACTED]", detail)
    detail = re.sub(r"[\w.+-]+@[\w.-]+", "[REDACTED_EMAIL]", detail)
    detail = re.sub(r"(?<!\d)1\d{10}(?!\d)", "[REDACTED_PHONE]", detail)
    detail = " ".join(detail.split())
    if len(detail) > CONTRACT_ERROR_LOG_LIMIT:
        return detail[:CONTRACT_ERROR_LOG_LIMIT] + "…"
    return detail or "未提供具体契约校验信息"


def _chapter_error_details(exc: BaseException) -> tuple[str, bool, str, int]:
    """Map provider failures to a stable transport contract for Java."""
    chain = list(_iter_exception_chain(exc))
    if isinstance(exc, _ChapterCapacityError):
        return (
            "CHAPTER_CAPACITY_EXHAUSTED",
            True,
            "章节分析服务繁忙，请稍后重试",
            503,
        )
    if any(
        isinstance(item, (APITimeoutError, HttpxTimeoutException, TimeoutError))
        for item in chain
    ):
        return (
            "CHAPTER_LLM_TIMEOUT",
            True,
            "章节分析模型调用超时，请稍后重试",
            504,
        )
    if any(isinstance(item, RateLimitError) for item in chain):
        return (
            "CHAPTER_LLM_RATE_LIMITED",
            True,
            "章节分析模型请求过于频繁，请稍后重试",
            429,
        )
    if any(isinstance(item, (APIConnectionError, TransportError)) for item in chain):
        return (
            "CHAPTER_LLM_CONNECTION_ERROR",
            True,
            "章节分析模型暂时不可用，请稍后重试",
            503,
        )
    for item in chain:
        if isinstance(item, APIStatusError):
            provider_status = item.status_code
            if provider_status == 429:
                return (
                    "CHAPTER_LLM_RATE_LIMITED",
                    True,
                    "章节分析模型请求过于频繁，请稍后重试",
                    429,
                )
            if provider_status >= 500:
                return (
                    "CHAPTER_LLM_PROVIDER_UNAVAILABLE",
                    True,
                    "章节分析模型暂时不可用，请稍后重试",
                    503,
                )
            return (
                "CHAPTER_LLM_PROVIDER_REJECTED",
                False,
                "章节分析模型拒绝了本次请求",
                502,
            )
    if isinstance(exc, (ChapterAnalysisOutputError, ChapterAnalysisOutputTooLargeError)):
        return (
            "CHAPTER_LLM_OUTPUT_INVALID",
            False,
            "章节分析模型返回结果不符合契约",
            502,
        )
    if isinstance(exc, (_ChapterConfigurationError, ConfigurationError, InputValidationError)):
        return (
            "CHAPTER_CONFIGURATION_INVALID",
            False,
            "章节分析运行配置无效",
            400,
        )
    if isinstance(exc, ModelNotAvailableError):
        return (
            "CHAPTER_LLM_UNAVAILABLE",
            True,
            "章节分析模型暂时不可用，请稍后重试",
            503,
        )
    return ("CHAPTER_ANALYSIS_FAILED", False, "章节解析失败", 500)


async def _invoke_with_capacity(analysis_chain, *args, **kwargs):
    """Bound long-running provider calls without allowing an unbounded wait queue."""

    try:
        await asyncio.wait_for(
            _chapter_slots.acquire(),
            timeout=CHAPTER_SLOT_WAIT_SECONDS,
        )
    except TimeoutError:
        raise _ChapterCapacityError("chapter analysis capacity exhausted") from None
    try:
        return await analysis_chain.ainvoke(*args, **kwargs)
    finally:
        _chapter_slots.release()


# ── Endpoint ─────────────────────────────────────────────────────────────────

async def analyze_chapter(
    request: AnalyzeChapterRequest,
    response: Response,
    request_id: str = Depends(get_request_id),
) -> AnalyzeChapterResponse:
    return await _run_chapter_analysis(request, response, request_id)


async def _run_chapter_analysis(
    request: AnalyzeChapterRequest,
    response: Response,
    request_id: str,
    progress_callback: Callable[[str, int, str], Awaitable[None] | None] | None = None,
) -> AnalyzeChapterResponse:
    """Analyze a novel chapter and produce a structured story bible.

    Flow:
    1. Build source units from raw text
    2. Build LLM prompt
    3. Call LLM
    4. Parse and validate JSON response
    5. Return validated result to Java for persistence
    """
    start_time = time.time()

    logger.info(
        "Chapter analysis started | request_id=%s | title_length=%d | text_length=%d | has_project_chars=%s",
        request_id,
        len(request.chapter_title),
        len(request.source_text),
        request.project_characters is not None and len(request.project_characters) > 0,
    )

    try:
        if progress_callback is not None:
            emitted = progress_callback(
                "PREPARING",
                10,
                "正在切分章节原文并准备人物规范",
            )
            if inspect.isawaitable(emitted):
                await emitted
        # Step 1: Build source units
        source_units = build_source_units(request.source_text)
        logger.info(
            "Source units built | request_id=%s | count=%d",
            request_id,
            len(source_units),
        )

        if not source_units:
            response.status_code = 400
            return AnalyzeChapterResponse(
                success=False,
                error="章节原文为空，无法分析",
                error_code="CHAPTER_SOURCE_EMPTY",
                request_id=request_id,
            )

        # Step 2: Build prompt
        project_characters = (
            [
                character.model_dump(by_alias=True)
                for character in request.project_characters
            ]
            if request.project_characters
            else None
        )
        prompt = build_prompt(
            chapter_title=request.chapter_title,
            source_units=source_units,
            project_characters=project_characters,
            video_model=request.video_model,
        )
        planning_context = build_planning_context(
            chapter_title=request.chapter_title,
            source_units=source_units,
            project_characters=project_characters,
        )
        logger.info(
            "Prompt built | request_id=%s | prompt_length=%d",
            request_id,
            len(prompt),
        )

        # Step 3: Run the reusable LangChain workflow. Java owns the timeout
        # value and transports it as seconds; Python owns streaming behavior.
        if request.llm_config is None or request.llm_config.timeout_seconds is None:
            raise _ChapterConfigurationError(
                "章节分析缺少 llm_config.timeout_seconds 配置"
            )
        llm_config = request.llm_config
        if llm_config.base_url:
            try:
                normalized_base_url = validate_outbound_http_url(llm_config.base_url)
            except InputValidationError as exc:
                raise _ChapterConfigurationError(
                    "章节分析 LLM 地址未通过出站安全校验"
                ) from exc
            llm_config = llm_config.model_copy(
                update={"base_url": normalized_base_url}
            )
        llm = create_llm(
            llm_config,
            max_retries=0,
            temperature=0.1,
            streaming=True,
            profile="chapter-analysis",
        )
        logger.info(
            "Calling chapter LCEL chain | request_id=%s | timeout_seconds=%d | streaming=true",
            request_id,
            request.llm_config.timeout_seconds,
        )
        analysis_chain = build_chapter_analysis_chain(llm)
        chain_result = await _invoke_with_capacity(
            analysis_chain,
            prompt,
            source_units,
            planning_context=planning_context,
            request_id=request_id,
            video_model=request.video_model,
            timeout_seconds=request.llm_config.timeout_seconds,
            progress_callback=progress_callback,
        )
        raw_response = chain_result.raw_response
        story_bible = chain_result.story_bible
        
        logger.info(
            "LLM response received | request_id=%s | response_length=%d | repairs=%d",
            request_id,
            len(raw_response),
            chain_result.repair_count,
        )

        # Step 4: The chain has already parsed and deterministically validated
        # the result before it reaches persistence.
        logger.info(
            "Story bible validated | request_id=%s | scenes=%d | characters=%d",
            request_id,
            len(story_bible.get("scenes", [])),
            len(story_bible.get("characters", [])),
        )

        elapsed = time.time() - start_time
        logger.info(
            "Chapter analysis completed | request_id=%s | elapsed=%.2fs",
            request_id,
            elapsed,
        )

        response.status_code = 200
        return AnalyzeChapterResponse(
            success=True,
            story_bible=story_bible,
            repair_count=chain_result.repair_count,
            request_id=request_id,
        )

    except Exception as exc:
        elapsed = time.time() - start_time
        error_code, retryable, error_message, http_status = _chapter_error_details(exc)
        if error_code == "CHAPTER_LLM_OUTPUT_INVALID":
            logger.error(
                "Chapter analysis contract validation failed | request_id=%s | elapsed=%.2fs | "
                "error_code=%s | retryable=%s | error_type=%s | contract_detail=%s",
                request_id,
                elapsed,
                error_code,
                retryable,
                type(exc).__name__,
                _safe_contract_error_detail(exc),
            )
        else:
            logger.error(
                "Chapter analysis failed | request_id=%s | elapsed=%.2fs | error_code=%s | "
                "retryable=%s | error_type=%s",
                request_id,
                elapsed,
                error_code,
                retryable,
                type(exc).__name__,
            )
        response.status_code = http_status
        return AnalyzeChapterResponse(
            success=False,
            error=error_message,
            error_code=error_code,
            retryable=retryable,
            request_id=request_id,
        )


@router.post("/analyze-chapter", response_model=AnalyzeChapterResponse)
async def analyze_chapter_endpoint(
    request: AnalyzeChapterRequest,
    response: Response,
    request_id: str = Depends(get_request_id),
) -> AnalyzeChapterResponse:
    """Backward-compatible non-streaming chapter-analysis endpoint."""

    return await analyze_chapter(request, response, request_id)


@router.post("/analyze-chapter/stream", response_model=None)
async def analyze_chapter_stream(
    request: AnalyzeChapterRequest,
    request_id: str = Depends(get_request_id),
) -> StreamingResponse:
    """Stream NDJSON stage events followed by one terminal result event."""

    async def event_stream():
        queue: asyncio.Queue[dict[str, object] | None] = asyncio.Queue(maxsize=16)

        async def report(stage: str, progress: int, message: str) -> None:
            await queue.put(
                {
                    "type": "progress",
                    "stage": stage,
                    "progress": progress,
                    "message": message,
                    "request_id": request_id,
                }
            )

        async def run_analysis() -> None:
            http_response = Response()
            try:
                result = await _run_chapter_analysis(
                    request,
                    http_response,
                    request_id,
                    progress_callback=report,
                )
                await queue.put(
                    {
                        "type": "result",
                        "status_code": http_response.status_code,
                        "response": result.model_dump(mode="json"),
                    }
                )
            finally:
                await queue.put(None)

        task = asyncio.create_task(run_analysis())
        try:
            while True:
                event = await queue.get()
                if event is None:
                    break
                yield json.dumps(event, ensure_ascii=False, separators=(",", ":")) + "\n"
        finally:
            if not task.done():
                task.cancel()
            try:
                await task
            except asyncio.CancelledError:
                pass

    return StreamingResponse(
        event_stream(),
        media_type="application/x-ndjson",
        headers={"X-Accel-Buffering": "no", "Cache-Control": "no-cache"},
    )
