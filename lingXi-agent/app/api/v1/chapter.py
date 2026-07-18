"""
Chapter analysis API endpoint.

Migrated from Java AiVideoChapterAnalysisWorker.
Handles source unit building, prompt construction, LLM call, and JSON validation.
"""

from __future__ import annotations

import asyncio
import time
from collections.abc import Iterator
from typing import Optional

from fastapi import APIRouter, Depends, Response
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

@router.post("/analyze-chapter", response_model=AnalyzeChapterResponse)
async def analyze_chapter(
    request: AnalyzeChapterRequest,
    response: Response,
    request_id: str = Depends(get_request_id),
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
            request_id=request_id,
            video_model=request.video_model,
            timeout_seconds=request.llm_config.timeout_seconds,
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
