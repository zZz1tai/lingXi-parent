"""Offline tests for the Java-to-Python chapter-analysis transport contract."""

from __future__ import annotations

import unittest
from types import SimpleNamespace
from unittest.mock import patch

import httpx
from openai import APITimeoutError

from app.api.v1 import chapter
from app.schemas.request import LLMConfig


class _TimeoutChain:
    def __init__(self) -> None:
        self.kwargs = {}

    async def ainvoke(self, *_args, **_kwargs):
        self.kwargs = _kwargs
        request = httpx.Request(
            "POST",
            "https://provider.invalid/v1/chat/completions",
        )
        raise APITimeoutError(request=request)


class _NestedHttpxTimeoutChain:
    async def ainvoke(self, *_args, **_kwargs):
        request = httpx.Request(
            "POST",
            "https://provider.invalid/v1/chat/completions",
        )
        try:
            raise httpx.ReadTimeout("provider read timed out", request=request)
        except httpx.ReadTimeout as exc:
            raise RuntimeError("wrapped provider failure") from exc


class _WallClockTimeoutChain:
    async def ainvoke(self, *_args, **_kwargs):
        raise TimeoutError("chapter stage wall-clock deadline exceeded")


class _SuccessChain:
    async def ainvoke(self, *_args, **_kwargs):
        return SimpleNamespace(
            story_bible={"scenes": [], "characters": []},
            raw_response='{"scenes": [], "characters": []}',
            repair_count=1,
        )


class ChapterApiContractTests(unittest.IsolatedAsyncioTestCase):
    @staticmethod
    def _request(*, timeout_seconds: int | None) -> chapter.AnalyzeChapterRequest:
        return chapter.AnalyzeChapterRequest(
            chapter_title="离线测试章节",
            source_text="林夏推门而入。",
            project_characters=[],
            video_model="wanx2.1-i2v-turbo",
            llm_config=LLMConfig(
                api_key="offline-secret",
                model="offline-model",
                base_url="https://provider.invalid/v1",
                timeout_seconds=timeout_seconds,
            ),
        )

    async def test_missing_timeout_fails_before_model_creation(self) -> None:
        with patch.object(chapter, "create_llm") as create_llm:
            response = await chapter.analyze_chapter(
                self._request(timeout_seconds=None),
                request_id="offline-request",
            )

        create_llm.assert_not_called()
        self.assertFalse(response.success)
        self.assertEqual("CHAPTER_CONFIGURATION_INVALID", response.error_code)
        self.assertFalse(response.retryable)

    async def test_timeout_is_retryable_and_model_is_forced_to_stream(self) -> None:
        timeout_chain = _TimeoutChain()
        with (
            patch.object(chapter, "create_llm", return_value=object()) as create_llm,
            patch.object(
                chapter,
                "build_chapter_analysis_chain",
                return_value=timeout_chain,
            ),
        ):
            response = await chapter.analyze_chapter(
                self._request(timeout_seconds=417),
                request_id="offline-request",
            )

        supplied_config = create_llm.call_args.args[0]
        self.assertEqual(417, supplied_config.timeout_seconds)
        self.assertIs(create_llm.call_args.kwargs["streaming"], True)
        self.assertEqual(0, create_llm.call_args.kwargs["max_retries"])
        self.assertEqual(417, timeout_chain.kwargs["timeout_seconds"])
        self.assertFalse(response.success)
        self.assertEqual("CHAPTER_LLM_TIMEOUT", response.error_code)
        self.assertTrue(response.retryable)
        self.assertNotEqual("Request timed out.", response.error)

    async def test_nested_httpx_timeout_is_also_retryable(self) -> None:
        with (
            patch.object(chapter, "create_llm", return_value=object()),
            patch.object(
                chapter,
                "build_chapter_analysis_chain",
                return_value=_NestedHttpxTimeoutChain(),
            ),
        ):
            response = await chapter.analyze_chapter(
                self._request(timeout_seconds=417),
                request_id="offline-request",
            )

        self.assertEqual("CHAPTER_LLM_TIMEOUT", response.error_code)
        self.assertTrue(response.retryable)

    async def test_wall_clock_deadline_is_mapped_to_retryable_timeout(self) -> None:
        with (
            patch.object(chapter, "create_llm", return_value=object()),
            patch.object(
                chapter,
                "build_chapter_analysis_chain",
                return_value=_WallClockTimeoutChain(),
            ),
        ):
            response = await chapter.analyze_chapter(
                self._request(timeout_seconds=417),
                request_id="offline-request",
            )

        self.assertEqual("CHAPTER_LLM_TIMEOUT", response.error_code)
        self.assertTrue(response.retryable)

    async def test_success_response_preserves_chain_repair_count(self) -> None:
        with (
            patch.object(chapter, "create_llm", return_value=object()),
            patch.object(
                chapter,
                "build_chapter_analysis_chain",
                return_value=_SuccessChain(),
            ),
        ):
            response = await chapter.analyze_chapter(
                self._request(timeout_seconds=417),
                request_id="offline-request",
            )

        self.assertTrue(response.success)
        self.assertEqual(1, response.repair_count)
        self.assertIsNone(response.error_code)
        self.assertFalse(response.retryable)


if __name__ == "__main__":
    unittest.main()
