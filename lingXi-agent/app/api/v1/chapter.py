"""
Chapter analysis API endpoint.

Migrated from Java AiVideoChapterAnalysisWorker.
Handles source unit building, prompt construction, LLM call, and JSON validation.
"""

from __future__ import annotations

import json
import time
from typing import Any, Optional

from fastapi import APIRouter, Depends
from pydantic import BaseModel, Field

from app.api.dependencies import create_llm, get_request_id
from app.schemas.request import LLMConfig
from app.services.chapter_analysis import (
    SourceUnit,
    build_prompt,
    build_source_units,
    parse_and_validate,
)
from app.utils.exceptions import SearchError
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
    error: Optional[str] = None


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
            )

        # Step 2: Build prompt
        prompt = build_prompt(
            chapter_title=request.chapter_title,
            source_units=source_units,
            project_characters=request.project_characters,
        )
        logger.info(
            "Prompt built | request_id=%s | prompt_length=%d",
            request_id,
            len(prompt),
        )

        # Step 3: Call LLM (with extended timeout for chapter analysis)
        from langchain_openai import ChatOpenAI

        llm_config = request.llm_config
        llm_kwargs = {
            "model": llm_config.model if llm_config else "qwen-max",
            "api_key": llm_config.api_key if llm_config else None,
            "timeout": 300,  # 5 minutes for chapter analysis
            "max_retries": 1,
        }
        if llm_config and llm_config.base_url:
            llm_kwargs["base_url"] = llm_config.base_url
        llm = ChatOpenAI(**llm_kwargs)

        from langchain_core.messages import HumanMessage

        messages = [HumanMessage(content=prompt)]

        logger.info("Calling LLM | request_id=%s", request_id)
        result = await llm.ainvoke(messages)
        raw_response = result.content if hasattr(result, "content") else str(result)
        logger.info(
            "LLM response received | request_id=%s | response_length=%d",
            request_id,
            len(raw_response),
        )

        # Step 4: Parse and validate
        story_bible = parse_and_validate(raw_response, source_units)
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
        )

    except Exception as exc:
        import traceback
        elapsed = time.time() - start_time
        logger.error(
            "Chapter analysis failed | request_id=%s | elapsed=%.2fs | error=%s\n%s",
            request_id,
            elapsed,
            str(exc),
            traceback.format_exc(),
        )
        return AnalyzeChapterResponse(
            success=False,
            error=str(exc),
        )
