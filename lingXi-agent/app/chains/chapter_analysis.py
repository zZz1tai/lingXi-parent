"""Dedicated LCEL workflow for structured chapter analysis."""

from __future__ import annotations

import asyncio
from dataclasses import dataclass
from typing import Any

from langchain_core.output_parsers import JsonOutputParser, StrOutputParser
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.runnables import Runnable, RunnableConfig
from pydantic import ValidationError

from app.schemas.chapter import validate_story_bible_structure
from app.services.chapter_analysis import SourceUnit, validate_document


MAX_CHAPTER_OUTPUT_CHARS = 1_000_000


PRIMARY_PROMPT = ChatPromptTemplate.from_messages(
    [
        (
            "system",
            "You are a deterministic film pre-production planner. Follow the complete "
            "contract in the user message. Return exactly one JSON object and no Markdown.",
        ),
        ("human", "{analysis_prompt}"),
    ]
)

REPAIR_PROMPT = ChatPromptTemplate.from_messages(
    [
        (
            "system",
            "Repair a structurally invalid chapter-analysis JSON response. Preserve the "
            "novel facts and intended shot plan, fix only the reported contract problems, "
            "and return the complete corrected JSON object without Markdown or explanation.",
        ),
        (
            "human",
            "ORIGINAL CONTRACT AND SOURCE:\n{analysis_prompt}\n\n"
            "VALIDATION ERROR:\n{validation_error}\n\n"
            "INVALID RESPONSE:\n<INVALID_RESPONSE>\n{invalid_response}\n"
            "</INVALID_RESPONSE>",
        ),
    ]
)


@dataclass(frozen=True)
class ChapterAnalysisChainResult:
    story_bible: dict[str, Any]
    raw_response: str
    repair_count: int


class ChapterAnalysisOutputError(ValueError):
    """The model responded, but both initial and repaired output were invalid."""


class ChapterAnalysisOutputTooLargeError(ValueError):
    """The provider stream exceeded the configured in-memory output boundary."""


class _ContractValidationError(ValueError):
    def __init__(self, message: str, raw_response: str):
        super().__init__(message)
        self.raw_response = raw_response


class ChapterAnalysisChain:
    """Generate and deterministically validate one chapter story bible.

    Provider/network exceptions intentionally escape without a repair attempt.
    Exactly one repair call is allowed, and only after JSON, Pydantic, or domain
    contract validation has failed.
    """

    def __init__(self, model: Runnable):
        self._json_parser = JsonOutputParser()
        self._primary_chain = PRIMARY_PROMPT | model | StrOutputParser()
        self._repair_chain = REPAIR_PROMPT | model | StrOutputParser()

    async def ainvoke(
        self,
        analysis_prompt: str,
        source_units: list[SourceUnit],
        request_id: str = "",
        video_model: str = "",
        timeout_seconds: float | None = None,
    ) -> ChapterAnalysisChainResult:
        config = self._run_config(request_id)
        raw_response = await self._collect_stream(
            self._primary_chain,
            {"analysis_prompt": analysis_prompt},
            config=config,
            timeout_seconds=timeout_seconds,
        )
        try:
            document = self._parse_and_validate(raw_response, source_units, video_model)
            return ChapterAnalysisChainResult(document, raw_response, 0)
        except _ContractValidationError as initial_error:
            repaired_response = await self._collect_stream(
                self._repair_chain,
                {
                    "analysis_prompt": analysis_prompt,
                    "validation_error": str(initial_error),
                    "invalid_response": raw_response,
                },
                config={
                    **config,
                    "run_name": "chapter_story_bible_repair",
                    "tags": ["ai-video", "chapter-analysis", "repair"],
                },
                timeout_seconds=timeout_seconds,
            )
            try:
                document = self._parse_and_validate(repaired_response, source_units, video_model)
                return ChapterAnalysisChainResult(document, repaired_response, 1)
            except _ContractValidationError as repair_error:
                raise ChapterAnalysisOutputError(
                    "章节分析结果修复后仍不符合契约：" + str(repair_error)
                ) from repair_error

    @staticmethod
    async def _collect_stream(
        chain: Runnable,
        input_value: dict[str, Any],
        *,
        config: RunnableConfig,
        timeout_seconds: float | None,
    ) -> str:
        """Consume provider chunks internally and return one complete JSON string.

        Chapter story bibles are large enough that a non-streaming compatible API
        can spend minutes generating before it sends response headers. Consuming
        ``astream`` lets the provider send headers and tokens as soon as generation
        starts, while keeping the public chapter endpoint a normal JSON response.
        Each invocation (primary or repair) gets its own wall-clock deadline in
        addition to the provider's per-read timeout. A provider that keeps a
        stream alive indefinitely therefore cannot exceed the stage budget.
        """
        chunks: list[str] = []
        total_chars = 0
        async with asyncio.timeout(timeout_seconds):
            async for chunk in chain.astream(input_value, config=config):
                total_chars += len(chunk)
                if total_chars > MAX_CHAPTER_OUTPUT_CHARS:
                    raise ChapterAnalysisOutputTooLargeError(
                        "章节分析模型输出超过服务端限制"
                    )
                chunks.append(chunk)
        return "".join(chunks)

    @staticmethod
    def _run_config(request_id: str) -> RunnableConfig:
        return {
            "run_name": "chapter_story_bible",
            "tags": ["ai-video", "chapter-analysis"],
            "metadata": {"request_id": request_id},
        }

    def _parse_and_validate(
        self,
        raw_response: str,
        source_units: list[SourceUnit],
        video_model: str,
    ) -> dict[str, Any]:
        try:
            parsed = self._json_parser.parse(raw_response)
            document = validate_story_bible_structure(parsed)
            return validate_document(document, source_units, video_model)
        except (ValidationError, ValueError, TypeError) as exc:
            raise _ContractValidationError(str(exc), raw_response) from exc


def build_chapter_analysis_chain(model: Runnable) -> ChapterAnalysisChain:
    return ChapterAnalysisChain(model)


__all__ = [
    "ChapterAnalysisChain",
    "ChapterAnalysisChainResult",
    "ChapterAnalysisOutputError",
    "ChapterAnalysisOutputTooLargeError",
    "build_chapter_analysis_chain",
]
