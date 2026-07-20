from __future__ import annotations

import asyncio
import json
import unittest
from copy import deepcopy
from collections.abc import AsyncIterator
from typing import Any
from unittest.mock import AsyncMock, patch

from langchain_core.messages import AIMessage, AIMessageChunk
from langchain_core.runnables import Runnable, RunnableConfig, RunnableLambda

from app.chains import chapter_analysis as chapter_analysis_module
from app.chains.chapter_analysis import (
    ChapterAnalysisOutputError,
    ChapterAnalysisOutputTooLargeError,
    build_chapter_analysis_chain,
)
from app.services.chapter_analysis import SourceUnit
from tests.chapter_fixtures import chapter_plan, generated_scene, source_units


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

    async def test_all_provider_calls_receive_independent_deadlines(self) -> None:
        valid_plan = json.dumps(chapter_plan(), ensure_ascii=False)
        valid_response = json.dumps(generated_scene(), ensure_ascii=False)
        chain = build_chapter_analysis_chain(StreamingOnlyResponseSequence(valid_response))
        collector = AsyncMock(
            side_effect=[valid_plan, "not-json", "still-not-json", valid_response]
        )

        with patch.object(chain, "_collect_stream", collector):
            result = await chain.ainvoke(
                "contract",
                source_units(),
                timeout_seconds=417,
            )

        self.assertEqual(2, result.repair_count)
        self.assertEqual(4, collector.await_count)
        self.assertEqual(
            [417, 417, 417, 417],
            [call.kwargs["timeout_seconds"] for call in collector.await_args_list],
        )

    async def test_second_repair_uses_latest_response_and_accumulated_errors(self) -> None:
        valid_plan = json.dumps(chapter_plan(), ensure_ascii=False)
        valid_response = json.dumps(generated_scene(), ensure_ascii=False)
        chain = build_chapter_analysis_chain(StreamingOnlyResponseSequence(valid_response))
        collector = AsyncMock(
            side_effect=[
                valid_plan,
                "primary-invalid-json",
                "first-repair-invalid-json",
                valid_response,
            ]
        )

        with patch.object(chain, "_collect_stream", collector):
            result = await chain.ainvoke("contract", source_units())

        first_repair_input = collector.await_args_list[2].args[1]
        second_repair_input = collector.await_args_list[3].args[1]
        first_errors = json.loads(first_repair_input["validation_errors"])
        second_errors = json.loads(second_repair_input["validation_errors"])

        self.assertEqual(2, result.repair_count)
        self.assertEqual("primary-invalid-json", first_repair_input["invalid_response"])
        self.assertEqual("first-repair-invalid-json", second_repair_input["invalid_response"])
        self.assertEqual(1, len(first_errors))
        self.assertEqual(2, len(second_errors))
        self.assertEqual(first_errors[0], second_errors[0])
        self.assertEqual(1, first_repair_input["repair_attempt"])
        self.assertEqual(2, second_repair_input["repair_attempt"])

    def test_repair_error_does_not_duplicate_the_invalid_response(self) -> None:
        chain = build_chapter_analysis_chain(StreamingOnlyResponseSequence("unused"))
        invalid_response = "sensitive-model-payload-" + "x" * 10_000
        error = chapter_analysis_module._ContractValidationError(
            "Invalid JSON output: " + invalid_response,
            invalid_response,
        )

        detail = chain._compact_validation_error(error)

        self.assertIn("[LATEST_INVALID_RESPONSE]", detail)
        self.assertNotIn("sensitive-model-payload", detail)

    async def test_scene_and_repair_calls_are_streamed_and_aggregated(self) -> None:
        valid_plan = json.dumps(chapter_plan(), ensure_ascii=False)
        valid_response = json.dumps(generated_scene(), ensure_ascii=False)
        model = StreamingOnlyResponseSequence(valid_plan, "not-json", valid_response)
        chain = build_chapter_analysis_chain(model)

        result = await chain.ainvoke("contract", source_units())

        self.assertEqual(3, model.stream_calls)
        self.assertEqual(0, model.invoke_calls)
        self.assertEqual(2, result.story_bible["videoPlan"]["shotCount"])
        self.assertEqual(1, result.repair_count)

    async def test_multiple_scenes_receive_only_their_assigned_source_units(self) -> None:
        units = source_units() + [
            SourceUnit(id="U3", order=3, paragraph_no=2, text="陈默打开窗户。"),
            SourceUnit(id="U4", order=4, paragraph_no=2, text="阳光照进房间。"),
        ]
        plan = chapter_plan()
        first_scene_plan = deepcopy(plan["scenes"][0])
        first_scene_plan["sourceUnitIds"] = ["U1", "U2"]
        second_scene_plan = deepcopy(first_scene_plan)
        second_scene_plan.update(
            {
                "sceneNo": 2,
                "title": "窗边晨光",
                "time": "清晨",
                "sourceUnitIds": ["U3", "U4"],
            }
        )
        plan["scenes"] = [first_scene_plan, second_scene_plan]

        first_scene = generated_scene()
        second_scene = deepcopy(first_scene)
        second_scene["sceneNo"] = 2
        second_scene["title"] = "窗边晨光"
        second_scene["time"] = "清晨"
        second_scene["shots"][0]["sourceUnitIds"] = ["U3"]
        second_scene["shots"][1]["sourceUnitIds"] = ["U4"]

        chain = build_chapter_analysis_chain(StreamingOnlyResponseSequence("unused"))
        collector = AsyncMock(
            side_effect=[
                json.dumps(plan, ensure_ascii=False),
                json.dumps(first_scene, ensure_ascii=False),
                json.dumps(second_scene, ensure_ascii=False),
            ]
        )

        with patch.object(chain, "_collect_stream", collector):
            result = await chain.ainvoke("contract", units)

        first_payload = json.loads(
            collector.await_args_list[1].args[1]["scene_source_units"]
        )
        second_payload = json.loads(
            collector.await_args_list[2].args[1]["scene_source_units"]
        )
        self.assertEqual(["U1", "U2"], [item["id"] for item in first_payload])
        self.assertEqual(["U3", "U4"], [item["id"] for item in second_payload])
        self.assertEqual(2, len(result.story_bible["scenes"]))
        self.assertEqual(4, result.story_bible["videoPlan"]["shotCount"])

    async def test_invalid_source_partition_repairs_the_plan_before_scene_generation(self) -> None:
        invalid_plan = chapter_plan()
        invalid_plan["scenes"][0]["sourceUnitIds"] = ["U1"]
        valid_plan = chapter_plan()
        valid_scene = generated_scene()
        model = StreamingOnlyResponseSequence(
            json.dumps(invalid_plan, ensure_ascii=False),
            json.dumps(valid_plan, ensure_ascii=False),
            json.dumps(valid_scene, ensure_ascii=False),
        )
        chain = build_chapter_analysis_chain(model)
        events: list[tuple[str, int, str]] = []

        async def report(stage: str, progress: int, message: str) -> None:
            events.append((stage, progress, message))

        result = await chain.ainvoke(
            "contract",
            source_units(),
            progress_callback=report,
        )

        self.assertEqual(1, result.repair_count)
        self.assertIn("PLANNING_REPAIR", [stage for stage, _, _ in events])
        self.assertEqual(3, model.stream_calls)

    def test_plan_rejects_a_scene_that_is_too_large_to_split_safely(self) -> None:
        units = [
            SourceUnit(id=f"U{index}", order=index, paragraph_no=index, text=f"事件{index}")
            for index in range(1, 14)
        ]
        plan = chapter_plan()
        plan["scenes"][0]["sourceUnitIds"] = [unit.id for unit in units]
        chain = build_chapter_analysis_chain(StreamingOnlyResponseSequence("unused"))

        with self.assertRaises(chapter_analysis_module._ContractValidationError):
            chain._parse_and_validate_plan(
                json.dumps(plan, ensure_ascii=False),
                units,
            )

    async def test_structural_failure_is_repaired_once(self) -> None:
        sequence = ResponseSequence(
            json.dumps(chapter_plan(), ensure_ascii=False),
            '{"summary": "缺少其余字段"}',
            json.dumps(generated_scene(), ensure_ascii=False),
        )
        chain = build_chapter_analysis_chain(RunnableLambda(sequence.__call__))

        result = await chain.ainvoke("contract", source_units(), request_id="test-request")

        self.assertEqual(3, sequence.calls)
        self.assertEqual(1, result.repair_count)
        self.assertEqual(2, result.story_bible["videoPlan"]["shotCount"])

    async def test_provider_failure_does_not_trigger_repair(self) -> None:
        sequence = ResponseSequence(RuntimeError("provider unavailable"))
        chain = build_chapter_analysis_chain(RunnableLambda(sequence.__call__))

        with self.assertRaisesRegex(RuntimeError, "provider unavailable"):
            await chain.ainvoke("contract", source_units())
        self.assertEqual(1, sequence.calls)

    async def test_invalid_second_repair_stops_after_configured_limit(self) -> None:
        sequence = ResponseSequence(
            json.dumps(chapter_plan(), ensure_ascii=False),
            "not-json",
            "still-not-json",
            "also-not-json",
        )
        chain = build_chapter_analysis_chain(RunnableLambda(sequence.__call__))

        with self.assertRaisesRegex(ChapterAnalysisOutputError, "经过2次修复"):
            await chain.ainvoke("contract", source_units())
        self.assertEqual(4, sequence.calls)

    async def test_two_repairs_report_distinct_progress(self) -> None:
        valid_plan = json.dumps(chapter_plan(), ensure_ascii=False)
        valid_response = json.dumps(generated_scene(), ensure_ascii=False)
        model = StreamingOnlyResponseSequence(
            valid_plan,
            "not-json",
            "still-not-json",
            valid_response,
        )
        chain = build_chapter_analysis_chain(model)
        events: list[tuple[str, int, str]] = []

        async def report(stage: str, progress: int, message: str) -> None:
            events.append((stage, progress, message))

        result = await chain.ainvoke(
            "contract",
            source_units(),
            progress_callback=report,
        )

        self.assertEqual(2, result.repair_count)
        self.assertEqual(
            [
                "PLANNING",
                "SCENE_GENERATING",
                "SCENE_REPAIRING",
                "SCENE_REPAIRING",
                "VALIDATING",
                "FINALIZING",
            ],
            [stage for stage, _, _ in events],
        )
        self.assertEqual([20, 30, 30, 30, 76, 90], [progress for _, progress, _ in events])
        self.assertIn("第1次", events[2][2])
        self.assertIn("第2次", events[3][2])

    async def test_progress_reports_distinct_pipeline_stages(self) -> None:
        valid_plan = json.dumps(chapter_plan(), ensure_ascii=False)
        valid_response = json.dumps(generated_scene(), ensure_ascii=False)
        model = StreamingOnlyResponseSequence(valid_plan, valid_response)
        chain = build_chapter_analysis_chain(model)
        events: list[tuple[str, int, str]] = []

        async def report(stage: str, progress: int, message: str) -> None:
            events.append((stage, progress, message))

        await chain.ainvoke(
            "contract",
            source_units(),
            progress_callback=report,
        )

        self.assertEqual(
            ["PLANNING", "SCENE_GENERATING", "VALIDATING", "FINALIZING"],
            [stage for stage, _, _ in events],
        )
        self.assertEqual([20, 30, 76, 90], [progress for _, progress, _ in events])

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
