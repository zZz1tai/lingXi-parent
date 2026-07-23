"""小说章节分析结果的结构化契约。

模型面向的模式仅处理结构性问题。依赖于源单元覆盖、对话身份和镜头排序的
确定性检查位于:mod:`app.services.chapter_analysis`中。
"""

from __future__ import annotations

from typing import Annotated, Any, Optional, Union

from pydantic import BaseModel, ConfigDict, Field, StringConstraints, model_validator

from app.schemas.request import LLMConfig


NonBlankText = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1)]
MAX_SCENE_SOURCE_UNITS = 12
ShortText = Annotated[
    str,
    StringConstraints(strip_whitespace=True, max_length=512),
]
CharacterText = Annotated[
    str,
    StringConstraints(strip_whitespace=True, max_length=8000),
]


class ChapterProjectCharacter(BaseModel):
    """从Java传输的有界项目身份，用于跨章节重用。"""

    model_config = ConfigDict(extra="ignore", populate_by_name=True)

    character_code: ShortText = Field(default="", alias="characterCode")
    name: ShortText = ""
    aliases: list[ShortText] = Field(default_factory=list, max_length=64)
    gender: ShortText = ""
    age_range: ShortText = Field(default="", alias="ageRange")
    appearance: CharacterText = ""
    speaking_style: CharacterText = Field(default="", alias="speakingStyle")
    visual_prompt_base: CharacterText = Field(default="", alias="visualPromptBase")


class AnalyzeChapterRequest(BaseModel):
    """``POST /api/v1/video/analyze-chapter``端点的请求体。"""

    chapter_title: str = Field(default="", max_length=512)
    source_text: str = Field(..., min_length=1, max_length=100000)
    project_characters: Optional[list[ChapterProjectCharacter]] = Field(
        default=None,
        max_length=500,
    )
    video_model: str = Field(..., min_length=1, max_length=256)
    llm_config: Optional[LLMConfig] = None

    @model_validator(mode="after")
    def validate_runtime_config_bounds(self) -> "AnalyzeChapterRequest":
        """验证运行时配置边界。"""
        if self.llm_config is None:
            return self
        if len(self.llm_config.api_key) > 8192:
            raise ValueError("llm_config.api_key exceeds the service limit")
        if len(self.llm_config.model) > 256:
            raise ValueError("llm_config.model exceeds the service limit")
        if self.llm_config.base_url and len(self.llm_config.base_url) > 4096:
            raise ValueError("llm_config.base_url exceeds the service limit")
        return self


class AnalyzeChapterResponse(BaseModel):
    """章节分析的精简生产响应。"""

    success: bool
    story_bible: Optional[dict[str, Any]] = None
    repair_count: int = 0
    request_id: str = ""
    error: Optional[str] = None
    error_code: Optional[str] = None
    retryable: bool = False


class ChapterContractModel(BaseModel):
    """保留后续审计所需提供商字段的基础模型。"""

    model_config = ConfigDict(
        extra="allow",
        populate_by_name=True,
    )


def _first_non_blank(data: dict[str, Any], *names: str) -> Optional[str]:
    """从字典中获取第一个非空白值。"""
    for name in names:
        value = data.get(name)
        if value is not None and str(value).strip():
            return str(value).strip()
    return None


class ChapterDialogue(ChapterContractModel):
    """规范场景对话；``dialogueId``可能在之后生成。"""

    dialogue_id: Optional[str] = Field(default=None, alias="dialogueId")
    speaker: NonBlankText
    line: NonBlankText
    emotion: NonBlankText
    action: NonBlankText

    @model_validator(mode="before")
    @classmethod
    def normalize_provider_aliases(cls, value: Any) -> Any:
        """规范化提供商别名。"""
        if not isinstance(value, dict):
            return value
        normalized = dict(value)
        speaker = _first_non_blank(normalized, "speaker", "character", "characterName", "name")
        line = _first_non_blank(normalized, "line", "text", "content")
        if speaker is not None:
            normalized["speaker"] = speaker
        if line is not None:
            normalized["line"] = line
        return normalized


class ShotDialogue(ChapterContractModel):
    """嵌入在镜头中的对话引用。

    镜头可以仅使用``dialogueId``引用现有的场景对话。
    当场景列表省略对话时，``speaker``加上``line``为确定性规范化器
    提供足够的信息来添加它。
    """

    dialogue_id: Optional[str] = Field(default=None, alias="dialogueId")
    speaker: Optional[str] = None
    line: Optional[str] = None
    emotion: str = ""
    action: str = ""

    @model_validator(mode="before")
    @classmethod
    def normalize_provider_aliases(cls, value: Any) -> Any:
        """规范化提供商别名。"""
        if not isinstance(value, dict):
            return value
        normalized = dict(value)
        speaker = _first_non_blank(normalized, "speaker", "character", "characterName", "name")
        line = _first_non_blank(normalized, "line", "text", "content")
        if speaker is not None:
            normalized["speaker"] = speaker
        if line is not None:
            normalized["line"] = line
        return normalized


ShotDialogueInput = Union[list[Union[ShotDialogue, str]], ShotDialogue, str, None]
SceneDialogueInput = Union[list[ChapterDialogue], ChapterDialogue, None]


class ChapterCharacter(ChapterContractModel):
    name: NonBlankText
    aliases: list[str] = Field(default_factory=list)
    gender: str = ""
    age_range: str = Field(default="", alias="ageRange")
    appearance: str = ""
    # 历史故事圣经可能在此处使用自由文本，也可能使用结构化对象或数组；
    # Java 会将它作为 JSON 保存，而不是强制转换为标量。
    personality: Any = ""
    speaking_style: str = Field(default="", alias="speakingStyle")
    visual_prompt_base: NonBlankText = Field(alias="visualPromptBase")
    character_reference_prompt: Optional[str] = Field(
        default=None,
        alias="characterReferencePrompt",
    )
    character_reference_negative_prompt: Optional[str] = Field(
        default=None,
        alias="characterReferenceNegativePrompt",
    )


class ChapterShot(ChapterContractModel):
    shot_no: Optional[int] = Field(default=None, alias="shotNo")
    duration_ms: int = Field(alias="durationMs")
    source_unit_ids: list[NonBlankText] = Field(alias="sourceUnitIds", min_length=1, max_length=2)
    characters: Optional[list[Any]] = Field(default=None, max_length=4)
    narrative_beat: NonBlankText = Field(alias="narrativeBeat")
    shot_size: NonBlankText = Field(alias="shotSize")
    camera_movement: NonBlankText = Field(alias="cameraMovement")
    composition: NonBlankText
    action: NonBlankText
    emotion: NonBlankText
    dialogues: ShotDialogueInput = Field(default_factory=list)
    keyframe_prompt: NonBlankText = Field(alias="keyframePrompt")
    image_negative_prompt: NonBlankText = Field(alias="imageNegativePrompt")
    video_prompt: Annotated[
        str,
        StringConstraints(strip_whitespace=True, min_length=1, max_length=400),
    ] = Field(alias="videoPrompt")
    video_negative_prompt: Annotated[
        str,
        StringConstraints(strip_whitespace=True, min_length=1, max_length=300),
    ] = Field(alias="videoNegativePrompt")


class ChapterScene(ChapterContractModel):
    scene_no: Optional[int] = Field(default=None, alias="sceneNo")
    title: NonBlankText
    time: NonBlankText
    location: NonBlankText
    atmosphere: NonBlankText
    dramatic_goal: NonBlankText = Field(alias="dramaticGoal")
    characters: list[Any] = Field(default_factory=list)
    dialogues: SceneDialogueInput = Field(default_factory=list)
    shots: list[ChapterShot] = Field(min_length=1)
    scene_image_prompt: Optional[str] = Field(default=None, alias="sceneImagePrompt")
    scene_image_negative_prompt: Optional[str] = Field(
        default=None,
        alias="sceneImageNegativePrompt",
    )


class ChapterVideoPlan(ChapterContractModel):
    source_unit_count: int = Field(alias="sourceUnitCount", ge=1)
    minimum_shot_count: int = Field(alias="minimumShotCount", ge=1)
    shot_count: int = Field(alias="shotCount", ge=1)
    estimated_total_duration_ms: int = Field(alias="estimatedTotalDurationMs", ge=1)
    segmentation_rationale: NonBlankText = Field(alias="segmentationRationale")


class ChapterStoryBible(ChapterContractModel):
    summary: NonBlankText
    world_setting: NonBlankText = Field(alias="worldSetting")
    timeline: list[Any]
    relationships: list[Any]
    immutable_facts: list[Any] = Field(alias="immutableFacts")
    video_plan: ChapterVideoPlan = Field(alias="videoPlan")
    characters: list[ChapterCharacter]
    scenes: list[ChapterScene] = Field(min_length=1)


class ChapterAnalysisPlan(ChapterContractModel):
    """全局章节事实加上模型提供的语义场景结束建议。

    源单元成员关系故意不由模型拥有。服务将这些可选的分段建议
    转换为完整的、有序的分区。
    """

    summary: NonBlankText
    world_setting: NonBlankText = Field(alias="worldSetting")
    timeline: list[Any]
    relationships: list[Any]
    immutable_facts: list[Any] = Field(alias="immutableFacts")
    segmentation_rationale: NonBlankText = Field(alias="segmentationRationale")
    characters: list[ChapterCharacter]
    scene_breaks: list[NonBlankText] = Field(default_factory=list, alias="sceneBreaks")


def validate_chapter_plan_structure(document: Any) -> dict[str, Any]:
    """验证提供商章节骨架并返回可变的驼峰命名数据。"""

    model = ChapterAnalysisPlan.model_validate(document)
    return model.model_dump(by_alias=True, exclude_none=True)


def validate_story_bible_structure(document: Any) -> dict[str, Any]:
    """验证提供商JSON并返回可变的驼峰命名字典。"""

    model = ChapterStoryBible.model_validate(document)
    return model.model_dump(by_alias=True, exclude_none=True)


__all__ = [
    "AnalyzeChapterRequest",
    "AnalyzeChapterResponse",
    "ChapterAnalysisPlan",
    "ChapterDialogue",
    "ChapterProjectCharacter",
    "ChapterScene",
    "ChapterStoryBible",
    "MAX_SCENE_SOURCE_UNITS",
    "validate_chapter_plan_structure",
    "validate_story_bible_structure",
]
