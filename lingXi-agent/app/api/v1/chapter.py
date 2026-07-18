"""
Chapter analysis API endpoint.

Migrated from Java AiVideoChapterAnalysisWorker.
Handles source unit building, prompt construction, LLM call, and JSON validation.
"""

from __future__ import annotations

import time
from collections.abc import Iterator
from typing import Any, Optional

from fastapi import APIRouter, Depends
from httpx import TimeoutException as HttpxTimeoutException
from openai import APITimeoutError
from pydantic import BaseModel, Field

from app.api.dependencies import create_llm, get_request_id
from app.chains.chapter_analysis import build_chapter_analysis_chain
from app.schemas.request import LLMConfig
from app.services.chapter_analysis import (
    build_prompt,
    build_source_units,
)
from app.utils.logger import logger

router = APIRouter(prefix="/api/v1/video", tags=["chapter"])


# ── Request/Response Models ──────────────────────────────────────────────────

class AnalyzeChapterRequest(BaseModel):
    """Request body for POST /api/v1/video/analyze-chapter."""

    chapter_title: str = Field(
        default="",
        description="Chapter title",
    )
    source_text: str = Field(
        ...,
        min_length=1,
        max_length=100000,
        description="Raw chapter source text",
    )
    project_characters: Optional[list[dict[str, Any]]] = Field(
        default=None,
        description="Existing project characters for cross-chapter identity reuse",
    )
    video_model: str = Field(
        ...,
        min_length=1,
        description="Configured downstream Wanx model used for duration normalization",
    )
    llm_config: Optional[LLMConfig] = Field(
        default=None,
        description="LLM configuration from Java backend",
    )


class AnalyzeChapterResponse(BaseModel):
    """Response for POST /api/v1/video/analyze-chapter."""

    success: bool
    story_bible: Optional[dict[str, Any]] = None
    source_units: Optional[list[dict[str, Any]]] = None
    prompt: Optional[str] = None
    raw_llm_response: Optional[str] = None
    repair_count: int = 0
    error: Optional[str] = None
    error_code: Optional[str] = None
    retryable: bool = False


class _ChapterConfigurationError(ValueError):
    """The Java-to-Python chapter request omitted required runtime config."""


def _iter_exception_chain(exc: BaseException) -> Iterator[BaseException]:
    """Yield an exception and its explicit/implicit causes without looping."""
    current: Optional[BaseException] = exc
    seen: set[int] = set()
    while current is not None and id(current) not in seen:
        seen.add(id(current))
        yield current
        current = current.__cause__ or current.__context__


def _chapter_error_details(exc: BaseException) -> tuple[str, bool, str]:
    """Map provider failures to a stable transport contract for Java."""
    if any(
        isinstance(item, (APITimeoutError, HttpxTimeoutException, TimeoutError))
        for item in _iter_exception_chain(exc)
    ):
        return (
            "CHAPTER_LLM_TIMEOUT",
            True,
            "章节分析模型调用超时，请稍后重试",
        )
    if isinstance(exc, _ChapterConfigurationError):
        return (
            "CHAPTER_CONFIGURATION_INVALID",
            False,
            str(exc),
        )
    return ("CHAPTER_ANALYSIS_FAILED", False, str(exc) or "章节解析失败")


# ── Endpoint ─────────────────────────────────────────────────────────────────

@router.post("/analyze-chapter", response_model=AnalyzeChapterResponse)
async def analyze_chapter(
    request: AnalyzeChapterRequest,
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
        "Chapter analysis started | request_id=%s | title=%s | text_length=%d | has_project_chars=%s",
        request_id,
        request.chapter_title,
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
            return AnalyzeChapterResponse(
                success=False,
                error="章节原文为空，无法分析",
                error_code="CHAPTER_SOURCE_EMPTY",
            )

        # Step 2: Build prompt
        prompt = build_prompt(
            chapter_title=request.chapter_title,
            source_units=source_units,
            project_characters=request.project_characters,
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
        llm = create_llm(
            request.llm_config,
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
        chain_result = await analysis_chain.ainvoke(
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

        # Serialize source units for response
        su_list = [
            {"id": su.id, "order": su.order, "paragraph_no": su.paragraph_no, "text": su.text}
            for su in source_units
        ]

        return AnalyzeChapterResponse(
            success=True,
            story_bible=story_bible,
            source_units=su_list,
            prompt=prompt,
            raw_llm_response=raw_response,
            repair_count=chain_result.repair_count,
        )

    except Exception as exc:
        import traceback
        elapsed = time.time() - start_time
        error_code, retryable, error_message = _chapter_error_details(exc)
        logger.error(
            "Chapter analysis failed | request_id=%s | elapsed=%.2fs | error_code=%s | "
            "retryable=%s | error=%s\n%s",
            request_id,
            elapsed,
            error_code,
            retryable,
            str(exc),
            traceback.format_exc(),
        )
        return AnalyzeChapterResponse(
            success=False,
            error=error_message,
            error_code=error_code,
            retryable=retryable,
        )
