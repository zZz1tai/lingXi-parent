"""使用 LangGraph 编排、LCEL 执行模型节点的结构化章节分析工作流。"""

from __future__ import annotations

import asyncio
import inspect
import json
import operator
import re
from copy import deepcopy
from dataclasses import dataclass
from collections.abc import Awaitable, Callable
from typing import Annotated, Any, TypedDict

from langchain_core.output_parsers import JsonOutputParser, StrOutputParser
from langchain_core.runnables import Runnable, RunnableConfig
from langgraph.graph import END, START, StateGraph
from langgraph.types import Send
from pydantic import ValidationError

from app.chains.promt import (
    PLAN_REPAIR_PROMPT,
    PLANNING_PROMPT,
    REPAIR_PROMPT,
    SCENE_PROMPT,
    SCENE_REPAIR_PROMPT,
)
from app.schemas.chapter import (
    MAX_SCENE_SOURCE_UNITS,
    validate_chapter_plan_structure,
    validate_story_bible_structure,
)
from app.services.chapter_analysis import (
    SourceUnit,
    build_scene_segments,
    validate_document,
)


MAX_CHAPTER_OUTPUT_CHARS = 1_000_000
MAX_CONTRACT_REPAIR_ATTEMPTS = 2
MAX_PLAN_REPAIR_ATTEMPTS = 1
MAX_SCENE_REPAIR_ATTEMPTS = 2
MAX_REPAIR_ERROR_CHARS = 4_000
DEFAULT_SCENE_CONCURRENCY = 2
MAX_SCENE_CONCURRENCY = 8

ProgressCallback = Callable[[str, int, str], Awaitable[None] | None]


@dataclass(frozen=True)
class ChapterAnalysisChainResult:
    """章节分析链的结果数据类。"""
    story_bible: dict[str, Any]
    raw_response: str
    repair_count: int


class _SceneFanoutResult(TypedDict):
    """单个并行场景节点的确定性输出。"""

    scene_index: int
    scene: dict[str, Any]
    repair_count: int


class _SceneFanoutState(TypedDict, total=False):
    """LangGraph 场景 Map-Reduce 子图状态。"""

    scene_tasks: list[dict[str, Any]]
    scene_task: dict[str, Any]
    scene_results: Annotated[list[_SceneFanoutResult], operator.add]
    ordered_scenes: list[dict[str, Any]]
    repair_count: int


class ChapterAnalysisOutputError(ValueError):
    """模型已响应，但其初始和修复后的输出均无效。"""


class ChapterAnalysisOutputTooLargeError(ValueError):
    """提供商流超出了配置的内存输出边界。"""


class _ContractValidationError(ValueError):
    """合同验证错误，包含原始响应。"""
    def __init__(self, message: str, raw_response: str):
        super().__init__(message)
        self.raw_response = raw_response


class ChapterAnalysisChain:
    """生成并确定性验证一个章节故事圣经。

    提供商/网络异常故意不进行修复尝试。
    允许最多两次修复调用，且仅在 JSON、Pydantic 或领域合同验证失败后进行。
    """

    def __init__(self, model: Runnable):
        """初始化章节分析链，设置各种提示链。"""
        self._json_parser = JsonOutputParser()
        self._planning_chain = PLANNING_PROMPT | model | StrOutputParser()
        self._plan_repair_chain = PLAN_REPAIR_PROMPT | model | StrOutputParser()
        self._scene_chain = SCENE_PROMPT | model | StrOutputParser()
        self._scene_repair_chain = SCENE_REPAIR_PROMPT | model | StrOutputParser()
        self._repair_chain = REPAIR_PROMPT | model | StrOutputParser()

    async def ainvoke(
        self,
        analysis_prompt: str,
        source_units: list[SourceUnit],
        planning_context: str | None = None,
        request_id: str = "",
        video_model: str = "",
        timeout_seconds: float | None = None,
        progress_callback: ProgressCallback | None = None,
        scene_concurrency: int = DEFAULT_SCENE_CONCURRENCY,
        callbacks: list[Any] | None = None,
        trace_metadata: dict[str, Any] | None = None,
    ) -> ChapterAnalysisChainResult:
        """异步调用章节分析链，生成并验证章节故事圣经。"""
        if scene_concurrency < 1 or scene_concurrency > MAX_SCENE_CONCURRENCY:
            raise ValueError("章节场景并发数必须在1到8之间")
        config = self._run_config(request_id, callbacks, trace_metadata)
        await self._emit_progress(
            progress_callback,
            "PLANNING",
            20,
            "正在提取章节事实、人物和场景结构",
        )
        skeleton_context = planning_context or analysis_prompt
        raw_plan = await self._collect_stream(
            self._planning_chain,
            {"analysis_prompt": skeleton_context},
            config={
                **config,
                "run_name": "chapter_story_bible_planning",
                "tags": ["ai-video", "chapter-analysis", "planning"],
            },
            timeout_seconds=timeout_seconds,
        )
        analysis_plan, plan_repair_count = await self._validate_and_repair_plan(
            raw_plan,
            skeleton_context,
            source_units,
            config,
            timeout_seconds,
            progress_callback,
        )

        generated_scenes, scene_repair_count = await self._generate_scenes_with_graph(
            analysis_plan,
            config,
            video_model,
            timeout_seconds,
            progress_callback,
            scene_concurrency,
        )

        assembled_document = self._assemble_document(analysis_plan, generated_scenes)
        raw_response = json.dumps(assembled_document, ensure_ascii=False)
        await self._emit_progress(
            progress_callback,
            "VALIDATING",
            76,
            "正在组装整章并校验人物、剧情覆盖和镜头契约",
        )
        document, final_repair_count = await self._validate_and_repair_document(
            raw_response,
            analysis_prompt,
            analysis_plan,
            source_units,
            config,
            video_model,
            timeout_seconds,
            progress_callback,
        )
        total_repairs = plan_repair_count + scene_repair_count + final_repair_count
        return ChapterAnalysisChainResult(
            document,
            json.dumps(document, ensure_ascii=False),
            total_repairs,
        )

    async def _generate_scenes_with_graph(
        self,
        analysis_plan: dict[str, Any],
        config: RunnableConfig,
        video_model: str,
        timeout_seconds: float | None,
        progress_callback: ProgressCallback | None,
        scene_concurrency: int,
    ) -> tuple[list[dict[str, Any]], int]:
        """使用 LangGraph 动态 Fan-out/Fan-in 并发生成全部场景。"""

        scene_count = len(analysis_plan["scenes"])
        progress_lock = asyncio.Lock()
        completed_scenes = 0

        def prepare_node(_state: _SceneFanoutState) -> dict[str, Any]:
            return {}

        def dispatch_scenes(state: _SceneFanoutState) -> list[Send]:
            return [
                Send("generate_scene", {"scene_task": scene_task})
                for scene_task in state["scene_tasks"]
            ]

        async def generate_scene_node(
            state: _SceneFanoutState,
        ) -> dict[str, list[_SceneFanoutResult]]:
            nonlocal completed_scenes
            task = state["scene_task"]
            scene_index = int(task["scene_index"])
            scene_plan = task["scene_plan"]

            async with progress_lock:
                current_progress = 30 + (
                    completed_scenes * 40 // max(1, scene_count)
                )
                await self._emit_progress(
                    progress_callback,
                    "SCENE_GENERATING",
                    current_progress,
                    f"正在并行生成第{scene_index}/{scene_count}个场景的分镜和对白",
                )

            async def report_local_repair(
                stage: str,
                _progress: int,
                message: str,
            ) -> None:
                async with progress_lock:
                    repair_progress = 30 + (
                        completed_scenes * 40 // max(1, scene_count)
                    )
                    await self._emit_progress(
                        progress_callback,
                        stage,
                        repair_progress,
                        message,
                    )

            scene, repairs = await self._generate_scene(
                analysis_plan,
                scene_plan,
                scene_plan["sourceUnits"],
                scene_index,
                scene_count,
                config,
                video_model,
                timeout_seconds,
                report_local_repair,
            )

            async with progress_lock:
                completed_scenes += 1
                completed_progress = 30 + (
                    completed_scenes * 40 // max(1, scene_count)
                )
                await self._emit_progress(
                    progress_callback,
                    "SCENE_COMPLETED",
                    completed_progress,
                    f"已完成场景{scene_index}，当前{completed_scenes}/{scene_count}",
                )

            return {
                "scene_results": [
                    {
                        "scene_index": scene_index,
                        "scene": scene,
                        "repair_count": repairs,
                    }
                ]
            }

        def collect_scenes_node(state: _SceneFanoutState) -> dict[str, Any]:
            ordered_results = sorted(
                state["scene_results"],
                key=lambda item: item["scene_index"],
            )
            return {
                "ordered_scenes": [item["scene"] for item in ordered_results],
                "repair_count": sum(
                    item["repair_count"] for item in ordered_results
                ),
            }

        graph = StateGraph(_SceneFanoutState)
        graph.add_node("prepare", prepare_node)
        graph.add_node("generate_scene", generate_scene_node)
        graph.add_node("collect_scenes", collect_scenes_node)
        graph.add_edge(START, "prepare")
        graph.add_conditional_edges(
            "prepare",
            dispatch_scenes,
            ["generate_scene"],
        )
        graph.add_edge("generate_scene", "collect_scenes")
        graph.add_edge("collect_scenes", END)
        scene_graph = graph.compile()

        scene_tasks = [
            {"scene_index": index, "scene_plan": scene_plan}
            for index, scene_plan in enumerate(analysis_plan["scenes"], start=1)
        ]
        graph_result = await scene_graph.ainvoke(
            {
                "scene_tasks": scene_tasks,
                "scene_results": [],
            },
            config={
                **config,
                "max_concurrency": scene_concurrency,
                "run_name": "chapter_scene_fanout",
                "tags": ["ai-video", "chapter-analysis", "scene-fanout"],
            },
        )
        return graph_result["ordered_scenes"], graph_result["repair_count"]

    async def _validate_and_repair_plan(
        self,
        raw_plan: str,
        analysis_prompt: str,
        source_units: list[SourceUnit],
        config: RunnableConfig,
        timeout_seconds: float | None,
        progress_callback: ProgressCallback | None,
    ) -> tuple[dict[str, Any], int]:
        """验证并修复章节计划，返回验证后的计划和修复次数。"""
        current_response = raw_plan
        repair_count = 0
        while True:
            try:
                return self._parse_and_validate_plan(current_response, source_units), repair_count
            except _ContractValidationError as validation_error:
                compact_error = self._compact_validation_error(validation_error)
                if repair_count >= MAX_PLAN_REPAIR_ATTEMPTS:
                    raise ChapterAnalysisOutputError(
                        "章节骨架修复后仍不符合契约；最新错误：" + compact_error
                    ) from validation_error
                repair_count += 1
                await self._emit_progress(
                    progress_callback,
                    "PLANNING_REPAIR",
                    25,
                    "章节骨架或源内容分配不完整，正在修复规划",
                )
                current_response = await self._collect_stream(
                    self._plan_repair_chain,
                    {
                        "analysis_prompt": analysis_prompt,
                        "validation_error": compact_error,
                        "invalid_response": current_response,
                    },
                    config={
                        **config,
                        "run_name": "chapter_story_bible_plan_repair",
                        "tags": ["ai-video", "chapter-analysis", "plan-repair"],
                    },
                    timeout_seconds=timeout_seconds,
                )

    async def _generate_scene(
        self,
        analysis_plan: dict[str, Any],
        scene_plan: dict[str, Any],
        scene_units: list[SourceUnit],
        scene_index: int,
        scene_count: int,
        config: RunnableConfig,
        video_model: str,
        timeout_seconds: float | None,
        progress_callback: ProgressCallback | None,
    ) -> tuple[dict[str, Any], int]:
        """生成单个场景并验证，返回验证后的场景和修复次数。"""
        minimum_shot_count = max(2, (len(scene_units) + 1) // 2)
        prompt_input = self._scene_prompt_input(
            analysis_plan,
            scene_plan,
            scene_units,
            minimum_shot_count,
        )
        current_response = await self._collect_stream(
            self._scene_chain,
            prompt_input,
            config={
                **config,
                "run_name": f"chapter_scene_{scene_index}",
                "tags": ["ai-video", "chapter-analysis", "scene-generation"],
                "metadata": {
                    **config.get("metadata", {}),
                    "scene_index": scene_index,
                    "scene_count": scene_count,
                },
            },
            timeout_seconds=timeout_seconds,
        )
        validation_errors: list[str] = []
        repair_count = 0
        while True:
            try:
                scene = self._parse_and_validate_scene(
                    current_response,
                    analysis_plan,
                    scene_plan,
                    scene_units,
                    video_model,
                )
                return scene, repair_count
            except _ContractValidationError as validation_error:
                compact_error = self._compact_validation_error(validation_error)
                validation_errors.append(compact_error)
                if repair_count >= MAX_SCENE_REPAIR_ATTEMPTS:
                    raise ChapterAnalysisOutputError(
                        f"场景{scene_index}经过{MAX_SCENE_REPAIR_ATTEMPTS}次修复仍不符合契约；"
                        f"最新错误：{compact_error}"
                    ) from validation_error
                repair_count += 1
                progress = 30 + ((scene_index - 1) * 40 // max(1, scene_count))
                await self._emit_progress(
                    progress_callback,
                    "SCENE_REPAIRING",
                    progress,
                    f"第{scene_index}/{scene_count}个场景校验失败，正在进行第{repair_count}次局部修复",
                )
                current_response = await self._collect_stream(
                    self._scene_repair_chain,
                    {
                        **prompt_input,
                        "repair_attempt": repair_count,
                        "validation_errors": json.dumps(
                            validation_errors,
                            ensure_ascii=False,
                        ),
                        "invalid_response": current_response,
                    },
                    config={
                        **config,
                        "run_name": f"chapter_scene_{scene_index}_repair_{repair_count}",
                        "tags": [
                            "ai-video",
                            "chapter-analysis",
                            "scene-repair",
                            f"attempt-{repair_count}",
                        ],
                    },
                    timeout_seconds=timeout_seconds,
                )

    async def _validate_and_repair_document(
        self,
        raw_response: str,
        analysis_prompt: str,
        analysis_plan: dict[str, Any],
        source_units: list[SourceUnit],
        config: RunnableConfig,
        video_model: str,
        timeout_seconds: float | None,
        progress_callback: ProgressCallback | None,
    ) -> tuple[dict[str, Any], int]:
        """验证并修复整个文档，返回验证后的文档和修复次数。"""
        current_response = raw_response
        validation_errors: list[str] = []
        global_repair_count = 0
        total_repair_count = 0
        while True:
            try:
                document = self._parse_and_validate(current_response, source_units, video_model)
                await self._emit_progress(
                    progress_callback,
                    "FINALIZING",
                    90,
                    (
                        "章节结构校验通过，正在整理最终结果"
                        if total_repair_count == 0
                        else f"修复结果校验通过，正在整理最终结果"
                    ),
                )
                return document, total_repair_count
            except _ContractValidationError as validation_error:
                compact_error = self._compact_validation_error(validation_error)
                validation_errors.append(compact_error)
                scene_index = self._dialogue_duration_scene_index(
                    compact_error,
                    len(analysis_plan["scenes"]),
                )
                if scene_index is not None:
                    parsed_document = self._json_parser.parse(current_response)
                    if not isinstance(parsed_document, dict):
                        raise ChapterAnalysisOutputError(
                            "最终场景局部修复时无法读取已组装的章节 JSON"
                        ) from validation_error
                    scenes = parsed_document.get("scenes")
                    if not isinstance(scenes, list) or scene_index >= len(scenes):
                        raise ChapterAnalysisOutputError(
                            f"最终校验定位到场景{scene_index + 1}，但组装结果中不存在该场景"
                        ) from validation_error
                    scene_plan = analysis_plan["scenes"][scene_index]
                    scene_units = scene_plan["sourceUnits"]
                    repaired_scene, local_repairs = await self._repair_scene_after_final_validation(
                        analysis_plan,
                        scene_plan,
                        scene_units,
                        scenes[scene_index],
                        scene_index,
                        len(scenes),
                        compact_error,
                        config,
                        video_model,
                        timeout_seconds,
                        progress_callback,
                    )
                    scenes[scene_index] = repaired_scene
                    current_response = json.dumps(
                        self._assemble_document(analysis_plan, scenes),
                        ensure_ascii=False,
                    )
                    total_repair_count += local_repairs
                    continue

                if global_repair_count >= MAX_CONTRACT_REPAIR_ATTEMPTS:
                    raise ChapterAnalysisOutputError(
                        f"章节分析结果经过{MAX_CONTRACT_REPAIR_ATTEMPTS}次修复仍不符合契约；"
                        f"最新错误：{compact_error}"
                    ) from validation_error

                global_repair_count += 1
                total_repair_count += 1
                progress = 82 if global_repair_count == 1 else 86
                await self._emit_progress(
                    progress_callback,
                    "REPAIRING",
                    progress,
                    f"契约校验未通过，正在进行第{global_repair_count}次完整结构修复",
                )
                current_response = await self._collect_stream(
                    self._repair_chain,
                    {
                        "analysis_prompt": analysis_prompt,
                        "repair_attempt": global_repair_count,
                        "validation_errors": json.dumps(
                            validation_errors,
                            ensure_ascii=False,
                        ),
                        "invalid_response": current_response,
                    },
                    config={
                        **config,
                        "run_name": f"chapter_story_bible_repair_{global_repair_count}",
                        "tags": [
                            "ai-video",
                            "chapter-analysis",
                            "repair",
                            f"attempt-{global_repair_count}",
                        ],
                    },
                    timeout_seconds=timeout_seconds,
                )

    async def _repair_scene_after_final_validation(
        self,
        analysis_plan: dict[str, Any],
        scene_plan: dict[str, Any],
        scene_units: list[SourceUnit],
        invalid_scene: Any,
        scene_index: int,
        scene_count: int,
        validation_error: str,
        config: RunnableConfig,
        video_model: str,
        timeout_seconds: float | None,
        progress_callback: ProgressCallback | None,
    ) -> tuple[dict[str, Any], int]:
        """仅修复最终对话时长错误指定的场景。"""

        minimum_shot_count = max(2, (len(scene_units) + 1) // 2)
        prompt_input = self._scene_prompt_input(
            analysis_plan,
            scene_plan,
            scene_units,
            minimum_shot_count,
        )
        current_response = json.dumps(invalid_scene, ensure_ascii=False)
        validation_errors = [validation_error]
        for repair_attempt in range(1, MAX_SCENE_REPAIR_ATTEMPTS + 1):
            await self._emit_progress(
                progress_callback,
                "SCENE_REPAIRING",
                80 + repair_attempt * 2,
                f"最终校验发现第{scene_index + 1}/{scene_count}个场景对白过长，"
                f"正在进行第{repair_attempt}次局部拆句修复",
            )
            current_response = await self._collect_stream(
                self._scene_repair_chain,
                {
                    **prompt_input,
                    "repair_attempt": repair_attempt,
                    "validation_errors": json.dumps(
                        validation_errors,
                        ensure_ascii=False,
                    ),
                    "invalid_response": current_response,
                },
                config={
                    **config,
                    "run_name": (
                        f"chapter_scene_{scene_index + 1}_final_dialogue_repair_"
                        f"{repair_attempt}"
                    ),
                    "tags": [
                        "ai-video",
                        "chapter-analysis",
                        "scene-dialogue-repair",
                        f"attempt-{repair_attempt}",
                    ],
                },
                timeout_seconds=timeout_seconds,
            )
            try:
                repaired_scene = self._parse_and_validate_scene(
                    current_response,
                    analysis_plan,
                    scene_plan,
                    scene_units,
                    video_model,
                )
                return repaired_scene, repair_attempt
            except _ContractValidationError as repair_error:
                validation_errors.append(self._compact_validation_error(repair_error))

        raise ChapterAnalysisOutputError(
            f"场景{scene_index + 1}对白经过{MAX_SCENE_REPAIR_ATTEMPTS}次局部修复仍不符合契约；"
            f"最新错误：{validation_errors[-1]}"
        )

    @staticmethod
    def _dialogue_duration_scene_index(
        validation_error: str,
        scene_count: int,
    ) -> int | None:
        """从验证错误中提取对话时长场景索引。"""
        match = re.search(
            r"场景(\d+)-镜头\d+\s+对白无法在镜头时长内自然说完",
            validation_error,
        )
        if match is None:
            return None
        scene_index = int(match.group(1)) - 1
        if scene_index < 0 or scene_index >= scene_count:
            return None
        return scene_index

    def _parse_and_validate_plan(
        self,
        raw_response: str,
        source_units: list[SourceUnit],
    ) -> dict[str, Any]:
        """解析并验证章节计划。"""
        try:
            parsed = self._json_parser.parse(raw_response)
            plan = validate_chapter_plan_structure(parsed)
            segments = build_scene_segments(
                source_units,
                plan.get("sceneBreaks", []),
                MAX_SCENE_SOURCE_UNITS,
            )
            plan["sceneBreaks"] = [segment[-1].id for segment in segments]
            plan["scenes"] = [
                {
                    "sceneNo": scene_index,
                    "sourceUnitIds": [unit.id for unit in segment],
                    "sourceUnits": segment,
                }
                for scene_index, segment in enumerate(segments, start=1)
            ]
            self._validate_plan_domain(plan, source_units)
            return plan
        except (ValidationError, ValueError, TypeError) as exc:
            raise _ContractValidationError(str(exc), raw_response) from exc

    def _validate_plan_domain(
        self,
        analysis_plan: dict[str, Any],
        source_units: list[SourceUnit],
    ) -> None:
        """在场景生成前快速失败于全局角色身份验证。"""

        provisional_document = self._base_document_from_plan(analysis_plan)
        provisional_scenes: list[dict[str, Any]] = []
        for scene_plan in analysis_plan["scenes"]:
            source_ids = scene_plan["sourceUnitIds"]
            shots = [
                self._placeholder_shot(source_ids[index:index + 2])
                for index in range(0, len(source_ids), 2)
            ]
            provisional_scenes.append(
                {
                    "sceneNo": scene_plan["sceneNo"],
                    "title": "规划校验占位场景",
                    "time": "未指定时间",
                    "location": "未指定地点",
                    "atmosphere": "待场景生成阶段确定",
                    "dramaticGoal": "覆盖已分配的章节原文",
                    "characters": [],
                    "dialogues": [],
                    "shots": shots,
                }
            )
        total_shots = sum(len(scene["shots"]) for scene in provisional_scenes)
        if total_shots < 2:
            provisional_scenes[0]["shots"].append(
                self._placeholder_shot(
                    [analysis_plan["scenes"][0]["sourceUnitIds"][0]]
                )
            )
            total_shots += 1
        provisional_document["scenes"] = provisional_scenes
        provisional_document["videoPlan"] = {
            "sourceUnitCount": len(source_units),
            "minimumShotCount": max(2, (len(source_units) + 1) // 2),
            "shotCount": total_shots,
            "estimatedTotalDurationMs": total_shots * 3_000,
            "segmentationRationale": analysis_plan["segmentationRationale"],
        }
        document = validate_story_bible_structure(provisional_document)
        validate_document(document, source_units)

    @staticmethod
    def _placeholder_shot(source_unit_ids: list[str]) -> dict[str, Any]:
        """生成占位镜头数据。"""
        return {
            "durationMs": 3_000,
            "sourceUnitIds": source_unit_ids,
            "characters": [],
            "narrativeBeat": "规划校验占位镜头",
            "shotSize": "medium shot",
            "cameraMovement": "static camera",
            "composition": "balanced composition",
            "action": "continuous placeholder action",
            "emotion": "neutral",
            "dialogues": [],
            "keyframePrompt": "empty scene, medium shot, balanced composition",
            "imageNegativePrompt": "text, watermark, people",
            "videoPrompt": "A static empty scene remains visually consistent.",
            "videoNegativePrompt": "flicker, text, watermark, people",
        }

    @staticmethod
    def _scene_prompt_input(
        analysis_plan: dict[str, Any],
        scene_plan: dict[str, Any],
        scene_units: list[SourceUnit],
        minimum_shot_count: int,
    ) -> dict[str, Any]:
        """构建场景提示输入数据。"""
        chapter_context = {
            "summary": analysis_plan["summary"],
            "worldSetting": analysis_plan["worldSetting"],
            "timeline": analysis_plan["timeline"],
            "relationships": analysis_plan["relationships"],
            "immutableFacts": analysis_plan["immutableFacts"],
        }
        source_payload = [
            {
                "id": unit.id,
                "order": unit.order,
                "paragraphNo": unit.paragraph_no,
                "text": unit.text,
            }
            for unit in scene_units
        ]
        scene_assignment = {
            "sceneNo": scene_plan["sceneNo"],
            "sourceUnitIds": scene_plan["sourceUnitIds"],
        }
        return {
            "chapter_context": json.dumps(chapter_context, ensure_ascii=False),
            "characters": json.dumps(analysis_plan["characters"], ensure_ascii=False),
            "scene_plan": json.dumps(scene_assignment, ensure_ascii=False),
            "scene_source_units": json.dumps(source_payload, ensure_ascii=False),
            "scene_source_unit_count": len(scene_units),
            "minimum_shot_count": minimum_shot_count,
        }

    def _parse_and_validate_scene(
        self,
        raw_response: str,
        analysis_plan: dict[str, Any],
        scene_plan: dict[str, Any],
        scene_units: list[SourceUnit],
        video_model: str,
    ) -> dict[str, Any]:
        """解析并验证场景数据。"""
        try:
            parsed_scene = self._json_parser.parse(raw_response)
            if not isinstance(parsed_scene, dict):
                raise ValueError("场景模型未返回 JSON 对象")
            parsed_scene = dict(parsed_scene)
            parsed_scene["sceneNo"] = scene_plan["sceneNo"]

            provisional_document = self._base_document_from_plan(analysis_plan)
            provisional_document["scenes"] = [parsed_scene]
            minimum_shot_count = max(2, (len(scene_units) + 1) // 2)
            provisional_document["videoPlan"] = {
                "sourceUnitCount": len(scene_units),
                "minimumShotCount": minimum_shot_count,
                "shotCount": minimum_shot_count,
                "estimatedTotalDurationMs": minimum_shot_count * 3_000,
                "segmentationRationale": analysis_plan["segmentationRationale"],
            }
            document = validate_story_bible_structure(provisional_document)
            validated = validate_document(document, scene_units, video_model)
            return validated["scenes"][0]
        except (ValidationError, ValueError, TypeError) as exc:
            detail = str(exc)
            scene_no = int(scene_plan["sceneNo"])
            if scene_no != 1:
                detail = re.sub(
                    r"场景1(?=-|\s|$|的)",
                    f"场景{scene_no}",
                    detail,
                )
            raise _ContractValidationError(detail, raw_response) from exc

    @staticmethod
    def _base_document_from_plan(analysis_plan: dict[str, Any]) -> dict[str, Any]:
        """从分析计划构建基础文档结构。"""
        return {
            "summary": analysis_plan["summary"],
            "worldSetting": analysis_plan["worldSetting"],
            "timeline": deepcopy(analysis_plan["timeline"]),
            "relationships": deepcopy(analysis_plan["relationships"]),
            "immutableFacts": deepcopy(analysis_plan["immutableFacts"]),
            "characters": deepcopy(analysis_plan["characters"]),
        }

    def _assemble_document(
        self,
        analysis_plan: dict[str, Any],
        scenes: list[dict[str, Any]],
    ) -> dict[str, Any]:
        """组装完整文档，包含所有场景和视频计划。"""
        document = self._base_document_from_plan(analysis_plan)
        document["scenes"] = deepcopy(scenes)
        shot_count = sum(len(scene.get("shots", [])) for scene in scenes)
        total_duration_ms = sum(
            int(shot.get("durationMs", 0))
            for scene in scenes
            for shot in scene.get("shots", [])
        )
        source_unit_count = sum(
            len(scene_plan["sourceUnitIds"])
            for scene_plan in analysis_plan["scenes"]
        )
        document["videoPlan"] = {
            "sourceUnitCount": source_unit_count,
            "minimumShotCount": max(2, (source_unit_count + 1) // 2),
            "shotCount": max(1, shot_count),
            "estimatedTotalDurationMs": max(1, total_duration_ms),
            "segmentationRationale": analysis_plan["segmentationRationale"],
        }
        return document

    @staticmethod
    def _compact_validation_error(error: _ContractValidationError) -> str:
        """保持修复指令有用，不重复大型模型响应。"""

        detail = str(error)
        if error.raw_response and error.raw_response in detail:
            detail = detail.replace(error.raw_response, "[LATEST_INVALID_RESPONSE]")
        detail = detail.strip()
        if len(detail) > MAX_REPAIR_ERROR_CHARS:
            return detail[:MAX_REPAIR_ERROR_CHARS] + "…"
        return detail

    @staticmethod
    async def _emit_progress(
        callback: ProgressCallback | None,
        stage: str,
        progress: int,
        message: str,
    ) -> None:
        """发出进度回调。"""
        if callback is None:
            return
        result = callback(stage, progress, message)
        if inspect.isawaitable(result):
            await result

    @staticmethod
    async def _collect_stream(
        chain: Runnable,
        input_value: dict[str, Any],
        *,
        config: RunnableConfig,
        timeout_seconds: float | None,
    ) -> str:
        """内部消费提供商块并返回一个完整的 JSON 字符串。

        章节故事圣经足够大，非流式兼容 API 可能需要几分钟生成才能发送响应头。消费
        ``astream`` 让提供商在生成开始时立即发送头和令牌，同时保持公共章节端点为正常 JSON 响应。
        每次调用（主要或修复）都有自己的墙上时钟截止时间，
        加上提供商的每次读取超时。因此，无限期保持流的提供商不能超过阶段预算。
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
    def _run_config(
        request_id: str,
        callbacks: list[Any] | None = None,
        trace_metadata: dict[str, Any] | None = None,
    ) -> RunnableConfig:
        """生成运行配置。"""
        config: RunnableConfig = {
            "run_name": "chapter_story_bible",
            "tags": ["ai-video", "chapter-analysis"],
            "metadata": {
                "request_id": request_id,
                **(trace_metadata or {}),
            },
        }
        if callbacks:
            config["callbacks"] = callbacks
        return config

    def _parse_and_validate(
        self,
        raw_response: str,
        source_units: list[SourceUnit],
        video_model: str,
    ) -> dict[str, Any]:
        """解析并验证最终文档。"""
        try:
            parsed = self._json_parser.parse(raw_response)
            document = validate_story_bible_structure(parsed)
            return validate_document(document, source_units, video_model)
        except (ValidationError, ValueError, TypeError) as exc:
            raise _ContractValidationError(str(exc), raw_response) from exc


def build_chapter_analysis_chain(model: Runnable) -> ChapterAnalysisChain:
    """构建章节分析链实例。"""
    return ChapterAnalysisChain(model)


__all__ = [
    "ChapterAnalysisChain",
    "ChapterAnalysisChainResult",
    "ChapterAnalysisOutputError",
    "ChapterAnalysisOutputTooLargeError",
    "build_chapter_analysis_chain",
]
