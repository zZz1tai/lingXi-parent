"""Offline tests for the Java-to-Python chapter-analysis transport contract."""

from __future__ import annotations

import asyncio
import json
import unittest
from types import SimpleNamespace
from unittest.mock import patch

import httpx
from fastapi import Response
from openai import APIConnectionError, APIStatusError, APITimeoutError, RateLimitError

from app.api.v1 import chapter
from app.chains.chapter_analysis import ChapterAnalysisOutputError
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


class _ConnectionFailureChain:
    async def ainvoke(self, *_args, **_kwargs):
        request = httpx.Request(
            "POST",
            "https://provider.invalid/v1/chat/completions",
        )
        raise APIConnectionError(request=request)


class _RateLimitChain:
    async def ainvoke(self, *_args, **_kwargs):
        request = httpx.Request(
            "POST",
            "https://provider.invalid/v1/chat/completions",
        )
        response = httpx.Response(429, request=request)
        raise RateLimitError(
            "provider rate limit",
            response=response,
            body=None,
        )


class _ProviderBodyFailureChain:
    async def ainvoke(self, *_args, **_kwargs):
        sentinel = "SENTINEL_PROVIDER_BODY_MUST_NOT_BE_LOGGED"
        request = httpx.Request(
            "POST",
            "https://provider.invalid/v1/chat/completions",
        )
        response = httpx.Response(
            500,
            request=request,
            headers={"x-provider-debug": sentinel},
            json={"error": {"message": sentinel}},
        )
        raise APIStatusError(
            "provider returned sensitive diagnostics",
            response=response,
            body={"error": {"message": sentinel}},
        )


class _SuccessChain:
    async def ainvoke(self, *_args, **_kwargs):
        callback = _kwargs.get("progress_callback")
        if callback is not None:
            await callback("PLANNING", 20, "正在提取章节事实")
            await callback("FINALIZING", 90, "正在整理最终结果")
        return SimpleNamespace(
            story_bible={"scenes": [], "characters": []},
            raw_response='{"scenes": [], "characters": []}',
            repair_count=1,
        )


class ChapterApiContractTests(unittest.IsolatedAsyncioTestCase):
    def test_contract_error_detail_is_redacted_and_truncated(self) -> None:
        detail = chapter._safe_contract_error_detail(
            ChapterAnalysisOutputError(
                "api_key=secret-value bearer: token-value " + "x" * 1_000
            )
        )

        self.assertIn("api_key=[REDACTED]", detail)
        self.assertIn("bearer=[REDACTED]", detail)
        self.assertNotIn("secret-value", detail)
        self.assertNotIn("token-value", detail)
        self.assertLessEqual(len(detail), chapter.CONTRACT_ERROR_LOG_LIMIT + 1)

    def test_contract_error_detail_redacts_pydantic_input_value(self) -> None:
        detail = chapter._safe_contract_error_detail(
            ValueError("field invalid; input_value='张三 13800138000 test@example.com', input_type=str")
        )

        self.assertIn("input_value=[REDACTED]", detail)
        self.assertNotIn("张三", detail)
        self.assertNotIn("13800138000", detail)
        self.assertNotIn("test@example.com", detail)

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
                base_url="https://dashscope.aliyuncs.com/compatible-mode/v1",
                timeout_seconds=timeout_seconds,
            ),
        )

    async def test_missing_timeout_fails_before_model_creation(self) -> None:
        with patch.object(chapter, "create_llm") as create_llm:
            response = await chapter.analyze_chapter(
                self._request(timeout_seconds=None),
                response=Response(),
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
            http_response = Response()
            response = await chapter.analyze_chapter(
                self._request(timeout_seconds=417),
                response=http_response,
                request_id="offline-request",
            )

        supplied_config = create_llm.call_args.args[0]
        self.assertEqual(417, supplied_config.timeout_seconds)
        self.assertIs(create_llm.call_args.kwargs["streaming"], True)
        self.assertEqual(0, create_llm.call_args.kwargs["max_retries"])
        self.assertEqual(417, timeout_chain.kwargs["timeout_seconds"])
        self.assertEqual(2, timeout_chain.kwargs["scene_concurrency"])
        self.assertIn("SOURCE UNITS", timeout_chain.kwargs["planning_context"])
        self.assertNotIn("videoPlan", timeout_chain.kwargs["planning_context"])
        self.assertFalse(response.success)
        self.assertEqual("CHAPTER_LLM_TIMEOUT", response.error_code)
        self.assertTrue(response.retryable)
        self.assertNotEqual("Request timed out.", response.error)
        self.assertEqual(504, http_response.status_code)

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
                response=Response(),
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
                response=Response(),
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
            http_response = Response()
            response = await chapter.analyze_chapter(
                self._request(timeout_seconds=417),
                response=http_response,
                request_id="offline-request",
            )

        self.assertTrue(response.success)
        self.assertEqual(1, response.repair_count)
        self.assertIsNone(response.error_code)
        self.assertFalse(response.retryable)
        self.assertEqual(200, http_response.status_code)
        self.assertNotIn("prompt", response.model_dump())
        self.assertNotIn("source_units", response.model_dump())
        self.assertNotIn("raw_llm_response", response.model_dump())

    async def test_stream_endpoint_emits_progress_before_terminal_result(self) -> None:
        with (
            patch.object(chapter, "create_llm", return_value=object()),
            patch.object(
                chapter,
                "build_chapter_analysis_chain",
                return_value=_SuccessChain(),
            ),
        ):
            stream = await chapter.analyze_chapter_stream(
                self._request(timeout_seconds=417),
                request_id="offline-request",
            )
            events = [
                json.loads(chunk.decode() if isinstance(chunk, bytes) else chunk)
                async for chunk in stream.body_iterator
            ]

        self.assertEqual(
            ["progress", "progress", "progress", "result"],
            [event["type"] for event in events],
        )
        self.assertEqual(
            ["PREPARING", "PLANNING", "FINALIZING"],
            [event["stage"] for event in events[:-1]],
        )
        self.assertTrue(events[-1]["response"]["success"])

    async def test_connection_failure_is_retryable(self) -> None:
        with (
            patch.object(chapter, "create_llm", return_value=object()),
            patch.object(
                chapter,
                "build_chapter_analysis_chain",
                return_value=_ConnectionFailureChain(),
            ),
        ):
            http_response = Response()
            result = await chapter.analyze_chapter(
                self._request(timeout_seconds=417),
                response=http_response,
                request_id="offline-request",
            )

        self.assertFalse(result.success)
        self.assertTrue(result.retryable)
        self.assertEqual("CHAPTER_LLM_CONNECTION_ERROR", result.error_code)
        self.assertEqual(503, http_response.status_code)

    async def test_rate_limit_is_retryable_and_uses_429(self) -> None:
        with (
            patch.object(chapter, "create_llm", return_value=object()),
            patch.object(
                chapter,
                "build_chapter_analysis_chain",
                return_value=_RateLimitChain(),
            ),
        ):
            http_response = Response()
            result = await chapter.analyze_chapter(
                self._request(timeout_seconds=417),
                response=http_response,
                request_id="offline-request",
            )

        self.assertFalse(result.success)
        self.assertTrue(result.retryable)
        self.assertEqual("CHAPTER_LLM_RATE_LIMITED", result.error_code)
        self.assertEqual(429, http_response.status_code)

    async def test_capacity_wait_queue_is_bounded(self) -> None:
        semaphore = asyncio.BoundedSemaphore(1)
        await semaphore.acquire()
        try:
            with (
                patch.object(chapter, "_chapter_slots", semaphore),
                patch.object(chapter, "CHAPTER_SLOT_WAIT_SECONDS", 0.01),
                self.assertRaises(chapter._ChapterCapacityError),
            ):
                await chapter._invoke_with_capacity(_SuccessChain(), "prompt", [])
        finally:
            semaphore.release()

    async def test_chapter_title_is_not_written_to_logs(self) -> None:
        sentinel = "SENTINEL_CHAPTER_TITLE\nFORGED_LOG_LINE"
        request = self._request(timeout_seconds=417).model_copy(
            update={"chapter_title": sentinel}
        )
        with (
            patch.object(chapter, "create_llm", return_value=object()),
            patch.object(
                chapter,
                "build_chapter_analysis_chain",
                return_value=_SuccessChain(),
            ),
            self.assertLogs(chapter.logger, level="INFO") as captured,
        ):
            result = await chapter.analyze_chapter(
                request,
                response=Response(),
                request_id="offline-request",
            )

        self.assertTrue(result.success)
        logs = "\n".join(captured.output)
        self.assertIn(f"title_length={len(sentinel)}", logs)
        self.assertNotIn(sentinel, logs)

    async def test_provider_body_and_headers_are_not_logged(self) -> None:
        sentinel = "SENTINEL_PROVIDER_BODY_MUST_NOT_BE_LOGGED"
        with (
            patch.object(chapter, "create_llm", return_value=object()),
            patch.object(
                chapter,
                "build_chapter_analysis_chain",
                return_value=_ProviderBodyFailureChain(),
            ),
            self.assertLogs(chapter.logger, level="ERROR") as captured,
        ):
            result = await chapter.analyze_chapter(
                self._request(timeout_seconds=417),
                response=Response(),
                request_id="offline-request",
            )

        self.assertFalse(result.success)
        self.assertTrue(result.retryable)
        self.assertEqual("CHAPTER_LLM_PROVIDER_UNAVAILABLE", result.error_code)
        self.assertNotIn(sentinel, "\n".join(captured.output))


if __name__ == "__main__":
    unittest.main()
