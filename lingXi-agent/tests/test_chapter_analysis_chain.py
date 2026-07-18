from __future__ import annotations

import asyncio
import json
import unittest
from collections.abc import AsyncIterator
from typing import Any
from unittest.mock import AsyncMock, patch

from langchain_core.messages import AIMessage, AIMessageChunk
from langchain_core.runnables import Runnable, RunnableConfig, RunnableLambda

from app.chains.chapter_analysis import (
    ChapterAnalysisOutputError,
    ChapterAnalysisOutputTooLargeError,
    build_chapter_analysis_chain,
)
from tests.chapter_fixtures import cloned_story_bible, source_units


class ResponseSequence:
    def __init__(self, *responses: Any):
        self.responses = list(responses)
        self.calls = 0

    async def __call__(self, _: Any) -> AIMessage:
        self.calls += 1
        response = self.responses.pop(0)
        if isinstance(response, BaseException):
            raise response
        return AIMessage(content=str(response))


class StreamingOnlyResponseSequence(Runnable[Any, AIMessageChunk]):
    """Offline model fake that fails if the chain falls back to non-streaming."""

    def __init__(self, *responses: str):
        self.responses = list(responses)
        self.stream_calls = 0
        self.invoke_calls = 0

    def invoke(
        self,
        input: Any,
        config: RunnableConfig | None = None,
        **kwargs: Any,
    ) -> AIMessageChunk:
        self.invoke_calls += 1
        raise AssertionError("chapter chain must not use invoke")

    async def ainvoke(
        self,
        input: Any,
        config: RunnableConfig | None = None,
        **kwargs: Any,
    ) -> AIMessageChunk:
        self.invoke_calls += 1
        raise AssertionError("chapter chain must not use ainvoke")

    async def astream(
        self,
        input: Any,
        config: RunnableConfig | None = None,
        **kwargs: Any,
    ):
        self.stream_calls += 1
        response = self.responses.pop(0)
        midpoint = max(1, len(response) // 2)
        yield AIMessageChunk(content=response[:midpoint])
        yield AIMessageChunk(content=response[midpoint:])


class EndlessStreamingModel(Runnable[Any, str]):
    """Continuously emits chunks until the chain's wall-clock deadline cancels it."""

    def __init__(self) -> None:
        self.stream_calls = 0

    def invoke(
        self,
        input: Any,
        config: RunnableConfig | None = None,
        **kwargs: Any,
    ) -> str:
        raise AssertionError("chapter chain must not use invoke")

    async def ainvoke(
        self,
        input: Any,
        config: RunnableConfig | None = None,
        **kwargs: Any,
    ) -> str:
        raise AssertionError("chapter chain must not use ainvoke")

    async def astream(
        self,
        input: Any,
        config: RunnableConfig | None = None,
        **kwargs: Any,
    ) -> AsyncIterator[str]:
        self.stream_calls += 1
        while True:
            await asyncio.sleep(0.005)
            yield " "


class ChapterAnalysisChainTests(unittest.IsolatedAsyncioTestCase):
    async def test_continuous_stream_is_cancelled_by_wall_clock_deadline(self) -> None:
        model = EndlessStreamingModel()
        chain = build_chapter_analysis_chain(model)
        loop = asyncio.get_running_loop()
        started = loop.time()

        with self.assertRaises(TimeoutError):
            await chain._collect_stream(
                model,
                {},
                config={},
                timeout_seconds=0.05,
            )

        self.assertEqual(1, model.stream_calls)
        self.assertLess(loop.time() - started, 0.5)

    async def test_primary_and_repair_receive_independent_deadlines(self) -> None:
        valid_response = json.dumps(cloned_story_bible(), ensure_ascii=False)
        chain = build_chapter_analysis_chain(StreamingOnlyResponseSequence(valid_response))
        collector = AsyncMock(side_effect=["not-json", valid_response])

        with patch.object(chain, "_collect_stream", collector):
            result = await chain.ainvoke(
                "contract",
                source_units(),
                timeout_seconds=417,
            )

        self.assertEqual(1, result.repair_count)
        self.assertEqual(2, collector.await_count)
        self.assertEqual(
            [417, 417],
            [call.kwargs["timeout_seconds"] for call in collector.await_args_list],
        )

    async def test_primary_and_repair_calls_are_streamed_and_aggregated(self) -> None:
        valid_response = json.dumps(cloned_story_bible(), ensure_ascii=False)
        model = StreamingOnlyResponseSequence("not-json", valid_response)
        chain = build_chapter_analysis_chain(model)

        result = await chain.ainvoke("contract", source_units())

        self.assertEqual(2, model.stream_calls)
        self.assertEqual(0, model.invoke_calls)
        self.assertEqual(valid_response, result.raw_response)
        self.assertEqual(1, result.repair_count)

    async def test_structural_failure_is_repaired_once(self) -> None:
        sequence = ResponseSequence(
            '{"summary": "缺少其余字段"}',
            json.dumps(cloned_story_bible(), ensure_ascii=False),
        )
        chain = build_chapter_analysis_chain(RunnableLambda(sequence.__call__))

        result = await chain.ainvoke("contract", source_units(), request_id="test-request")

        self.assertEqual(2, sequence.calls)
        self.assertEqual(1, result.repair_count)
        self.assertEqual(2, result.story_bible["videoPlan"]["shotCount"])

    async def test_provider_failure_does_not_trigger_repair(self) -> None:
        sequence = ResponseSequence(RuntimeError("provider unavailable"))
        chain = build_chapter_analysis_chain(RunnableLambda(sequence.__call__))

        with self.assertRaisesRegex(RuntimeError, "provider unavailable"):
            await chain.ainvoke("contract", source_units())
        self.assertEqual(1, sequence.calls)

    async def test_invalid_repair_is_not_retried_again(self) -> None:
        sequence = ResponseSequence("not-json", "still-not-json")
        chain = build_chapter_analysis_chain(RunnableLambda(sequence.__call__))

        with self.assertRaises(ChapterAnalysisOutputError):
            await chain.ainvoke("contract", source_units())
        self.assertEqual(2, sequence.calls)

    async def test_stream_output_is_cancelled_when_character_limit_is_exceeded(self) -> None:
        model = StreamingOnlyResponseSequence("x" * 101)
        chain = build_chapter_analysis_chain(model)

        with (
            patch("app.chains.chapter_analysis.MAX_CHAPTER_OUTPUT_CHARS", 100),
            self.assertRaises(ChapterAnalysisOutputTooLargeError),
        ):
            await chain.ainvoke("contract", source_units())

        self.assertEqual(1, model.stream_calls)


if __name__ == "__main__":
    unittest.main()
