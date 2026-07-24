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
from app.chains.promt import SCENE_PROMPT, SCENE_REPAIR_PROMPT
from app.services.chapter_analysis import SourceUnit
from tests.chapter_fixtures import (
    chapter_plan,
    cloned_story_bible,
    generated_scene,
    source_units,
)


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
    def test_scene_prompts_treat_dialogue_timing_as_guidance(self) -> None:
        prompt_values = {
            "chapter_context": "{}",
            "characters": "[]",
            "scene_plan": "{}",
            "scene_source_units": "[]",
            "scene_source_unit_count": 1,
            "minimum_shot_count": 2,
            "repair_attempt": 1,
            "validation_errors": "[]",
            "invalid_response": "{}",
        }
        prompt_texts = [
            "\n".join(
                str(message.content)
                for message in prompt.format_messages(**prompt_values)
            )
            for prompt in (SCENE_PROMPT, SCENE_REPAIR_PROMPT)
        ]

        for prompt_text in prompt_texts:
            self.assertIn("soft pacing guideline, not an output contract", prompt_text)
            self.assertIn("Never truncate or distort important dialogue", prompt_text)
            self.assertIn("multiple references in narrative order are allowed", prompt_text)
            self.assertNotIn("hard contract", prompt_text)

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
        plan["sceneBreaks"] = ["U2", "U4"]

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

    async def test_scene_fanout_respects_configured_concurrency_and_output_order(self) -> None:
        chain = build_chapter_analysis_chain(StreamingOnlyResponseSequence("unused"))
        active_calls = 0
        max_active_calls = 0

        async def generate_scene(*args: Any, **_kwargs: Any):
            nonlocal active_calls, max_active_calls
            scene_index = args[3]
            active_calls += 1
            max_active_calls = max(max_active_calls, active_calls)
            try:
                await asyncio.sleep(0.02 if scene_index % 2 else 0.01)
                return {"sceneNo": scene_index}, 0
            finally:
                active_calls -= 1

        analysis_plan = {
            "scenes": [
                {"sceneNo": index, "sourceUnits": []}
                for index in range(1, 6)
            ]
        }
        with patch.object(chain, "_generate_scene", side_effect=generate_scene):
            scenes, repairs = await chain._generate_scenes_with_graph(
                analysis_plan,
                {},
                "",
                None,
                None,
                2,
            )

        self.assertEqual(2, max_active_calls)
        self.assertEqual([1, 2, 3, 4, 5], [scene["sceneNo"] for scene in scenes])
        self.assertEqual(0, repairs)

    async def test_missing_final_scene_break_is_completed_without_plan_repair(self) -> None:
        incomplete_plan = chapter_plan()
        incomplete_plan["sceneBreaks"] = []
        valid_scene = generated_scene()
        model = StreamingOnlyResponseSequence(
            json.dumps(incomplete_plan, ensure_ascii=False),
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

        self.assertEqual(0, result.repair_count)
        self.assertNotIn("PLANNING_REPAIR", [stage for stage, _, _ in events])
        self.assertEqual(2, model.stream_calls)
        self.assertEqual(2, result.story_bible["videoPlan"]["sourceUnitCount"])

    async def test_final_dialogue_duration_error_repairs_only_the_target_scene(self) -> None:
        valid_plan = json.dumps(chapter_plan(), ensure_ascii=False)
        valid_scene = json.dumps(generated_scene(), ensure_ascii=False)
        chain = build_chapter_analysis_chain(StreamingOnlyResponseSequence("unused"))
        collector = AsyncMock(side_effect=[valid_plan, valid_scene, valid_scene])
        dialogue_error = chapter_analysis_module._ContractValidationError(
            "场景1-镜头2 对白无法在镜头时长内自然说完：中文/日韩字符 23，"
            "其他语言词数 0，粗估需要 5750ms，可用 4500ms",
            "assembled-response",
        )

        with (
            patch.object(chain, "_collect_stream", collector),
            patch.object(
                chain,
                "_parse_and_validate",
                side_effect=[dialogue_error, cloned_story_bible()],
            ),
        ):
            result = await chain.ainvoke("contract", source_units())

        self.assertEqual(1, result.repair_count)
        self.assertEqual(3, collector.await_count)
        self.assertIs(chain._scene_repair_chain, collector.await_args_list[2].args[0])
        self.assertTrue(
            all(call.args[0] is not chain._repair_chain for call in collector.await_args_list)
        )
        invalid_scene = json.loads(
            collector.await_args_list[2].args[1]["invalid_response"]
        )
        self.assertIn("shots", invalid_scene)
        self.assertNotIn("videoPlan", invalid_scene)

    async def test_long_dialogue_does_not_trigger_repair(self) -> None:
        valid_plan = json.dumps(chapter_plan(), ensure_ascii=False)
        overlong_scene = generated_scene()
        long_line = "这是一句按照粗略语速估算无法在五秒镜头中自然完整说完但仍应保留的超长对白内容"
        overlong_scene["dialogues"][0]["line"] = long_line
        overlong_response = json.dumps(overlong_scene, ensure_ascii=False)
        chain = build_chapter_analysis_chain(StreamingOnlyResponseSequence("unused"))
        collector = AsyncMock(side_effect=[valid_plan, overlong_response])

        with patch.object(chain, "_collect_stream", collector):
            result = await chain.ainvoke("contract", source_units())

        self.assertEqual(0, result.repair_count)
        self.assertEqual(2, collector.await_count)
        self.assertEqual(
            long_line,
            result.story_bible["scenes"][0]["shots"][1]["dialogues"][0]["line"],
        )

    async def test_multiple_dialogues_in_one_shot_do_not_trigger_repair(self) -> None:
        valid_plan = json.dumps(chapter_plan(), ensure_ascii=False)
        scene = generated_scene()
        scene["dialogues"].append(
            {
                "dialogueId": "model-dialogue-2",
                "speaker": "陈默",
                "line": "欢迎。",
                "emotion": "友好",
                "action": "回应",
            }
        )
        scene["shots"][1]["dialogues"] = [
            {"dialogueId": "model-dialogue-1"},
            {"dialogueId": "model-dialogue-2"},
        ]
        collector = AsyncMock(
            side_effect=[valid_plan, json.dumps(scene, ensure_ascii=False)]
        )
        chain = build_chapter_analysis_chain(StreamingOnlyResponseSequence("unused"))

        with patch.object(chain, "_collect_stream", collector):
            result = await chain.ainvoke("contract", source_units())

        self.assertEqual(0, result.repair_count)
        self.assertEqual(2, collector.await_count)
        self.assertEqual(
            ["S1D1", "S1D2"],
            [
                dialogue["dialogueId"]
                for dialogue in result.story_bible["scenes"][0]["shots"][1]["dialogues"]
            ],
        )

    async def test_named_scene_character_missing_from_plan_does_not_trigger_repair(self) -> None:
        valid_plan = json.dumps(chapter_plan(), ensure_ascii=False)
        scene = generated_scene()
        scene["shots"][0]["characters"] = ["林夏", "周元"]
        collector = AsyncMock(
            side_effect=[valid_plan, json.dumps(scene, ensure_ascii=False)]
        )
        chain = build_chapter_analysis_chain(StreamingOnlyResponseSequence("unused"))

        with patch.object(chain, "_collect_stream", collector):
            result = await chain.ainvoke("contract", source_units())

        inferred = next(
            character
            for character in result.story_bible["characters"]
            if character["name"] == "周元"
        )
        self.assertEqual(0, result.repair_count)
        self.assertEqual(2, collector.await_count)
        self.assertTrue(inferred["inferredFromSceneReferences"])
        self.assertEqual(
            ["林夏", "周元"],
            result.story_bible["scenes"][0]["shots"][0]["characterReferenceOrder"],
        )

    def test_plan_splits_an_oversized_semantic_scene_deterministically(self) -> None:
        units = [
            SourceUnit(id=f"U{index}", order=index, paragraph_no=index, text=f"事件{index}")
            for index in range(1, 14)
        ]
        plan = chapter_plan()
        plan["sceneBreaks"] = ["U13"]
        chain = build_chapter_analysis_chain(StreamingOnlyResponseSequence("unused"))

        parsed = chain._parse_and_validate_plan(
            json.dumps(plan, ensure_ascii=False),
            units,
        )

        self.assertEqual([12, 1], [len(scene["sourceUnitIds"]) for scene in parsed["scenes"]])
        self.assertEqual(
            [unit.id for unit in units],
            [source_id for scene in parsed["scenes"] for source_id in scene["sourceUnitIds"]],
        )

    def test_plan_that_stops_at_u62_still_covers_through_u70(self) -> None:
        units = [
            SourceUnit(id=f"U{index}", order=index, paragraph_no=index, text=f"事件{index}")
            for index in range(1, 71)
        ]
        plan = chapter_plan()
        plan["sceneBreaks"] = ["U12", "U24", "U36", "U48", "U62"]
        chain = build_chapter_analysis_chain(StreamingOnlyResponseSequence("unused"))

        parsed = chain._parse_and_validate_plan(
            json.dumps(plan, ensure_ascii=False),
            units,
        )

        assigned_ids = [
            source_id
            for scene in parsed["scenes"]
            for source_id in scene["sourceUnitIds"]
        ]
        self.assertEqual([unit.id for unit in units], assigned_ids)
        self.assertEqual("U70", parsed["scenes"][-1]["sourceUnitIds"][-1])
        self.assertTrue(
            all(len(scene["sourceUnitIds"]) <= 12 for scene in parsed["scenes"])
        )

    def test_local_scene_validation_reports_the_actual_scene_number(self) -> None:
        units = source_units()
        chain = build_chapter_analysis_chain(StreamingOnlyResponseSequence("unused"))
        plan = chain._parse_and_validate_plan(
            json.dumps(chapter_plan(), ensure_ascii=False),
            units,
        )
        scene_plan = deepcopy(plan["scenes"][0])
        scene_plan["sceneNo"] = 2
        invalid_scene = generated_scene()
        invalid_scene["shots"][1]["dialogues"] = [
            {"dialogueId": "does-not-exist"}
        ]

        with self.assertRaisesRegex(
            chapter_analysis_module._ContractValidationError,
            "场景2-镜头2",
        ):
            chain._parse_and_validate_scene(
                json.dumps(invalid_scene, ensure_ascii=False),
                plan,
                scene_plan,
                units,
                "",
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
                "SCENE_COMPLETED",
                "VALIDATING",
                "FINALIZING",
            ],
            [stage for stage, _, _ in events],
        )
        self.assertEqual([20, 30, 30, 30, 70, 76, 90], [progress for _, progress, _ in events])
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
            [
                "PLANNING",
                "SCENE_GENERATING",
                "SCENE_COMPLETED",
                "VALIDATING",
                "FINALIZING",
            ],
            [stage for stage, _, _ in events],
        )
        self.assertEqual([20, 30, 70, 76, 90], [progress for _, progress, _ in events])

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
