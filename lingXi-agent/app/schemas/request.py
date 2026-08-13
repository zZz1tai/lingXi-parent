"""面向Agent端点的严格Pydantic v2请求契约。"""

from __future__ import annotations

import json
from enum import Enum
from typing import Annotated, Any, Literal

from pydantic import (
    AliasChoices,
    BaseModel,
    ConfigDict,
    Field,
    SecretStr,
    field_validator,
    model_validator,
)


MAX_CHAT_MESSAGE_CHARS = 32_000
MAX_CONTEXT_JSON_BYTES = 256 * 1024
MAX_EXTRACT_TEXT_CHARS = 32_000
MAX_USER_PERMISSIONS = 256
MAX_CHAT_ATTACHMENTS = 5
MAX_ATTACHMENT_TEXT_CHARS = 60_000
MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024


MemoryPreferenceName = Annotated[
    str,
    Field(pattern=r"^(answer_length|answer_structure|number_format)$"),
]


class StrictRequestModel(BaseModel):
    """严格请求模型基类，禁止额外字段。"""
    model_config = ConfigDict(
        extra="forbid",
        str_strip_whitespace=True,
        populate_by_name=True,
    )


class LLMConfig(StrictRequestModel):
    """来自Java服务的白名单OpenAI兼容模型配置。"""

    api_key: str = Field(..., min_length=1, max_length=8192, repr=False)
    model: str = Field(..., min_length=1, max_length=128)
    base_url: str | None = Field(default=None, min_length=8, max_length=2048)
    timeout_seconds: int | None = Field(default=None, ge=1, le=1800)
    tavily_api_key: SecretStr | None = Field(
        default=None,
        min_length=8,
        max_length=256,
        repr=False,
        description="Tavily Search API key managed by the Java security config page",
    )

    @field_validator("api_key", "model")
    @classmethod
    def reject_blank_values(cls, value: str) -> str:
        """拒绝空白值。"""
        if not value.strip():
            raise ValueError("value must not be blank")
        return value.strip()


class ChatMode(str, Enum):
    CHAT = "chat"
    CONTEXT_ANALYSIS = "context_analysis"


PermissionCode = Annotated[
    str,
    Field(
        min_length=1,
        max_length=128,
        pattern=r"^[A-Za-z0-9*:_./-]+$",
    ),
]


class UserContext(StrictRequestModel):
    """由受信任 Java 服务从当前登录态生成的用户上下文。"""

    user_name: str = Field(..., min_length=1, max_length=128)
    role_code: str | None = Field(default=None, min_length=1, max_length=128)
    role_name: str | None = Field(default=None, min_length=1, max_length=128)
    region_id: int | None = Field(default=None, ge=1)
    region_name: str | None = Field(default=None, min_length=1, max_length=128)
    permissions: list[PermissionCode] = Field(
        default_factory=list,
        max_length=MAX_USER_PERMISSIONS,
    )

    @field_validator("user_name", "role_code", "role_name", "region_name")
    @classmethod
    def reject_control_characters(cls, value: str | None) -> str | None:
        """身份标签只能是单行数据，不能成为自由提示词片段。"""
        if value is None:
            return None
        if any(character in value for character in ("\r", "\n", "\x00")):
            raise ValueError("user context values must be single-line labels")
        return value

    @field_validator("permissions")
    @classmethod
    def require_unique_permissions(cls, values: list[str]) -> list[str]:
        """拒绝重复权限，避免无意义地扩大请求与提示词。"""
        if len(set(values)) != len(values):
            raise ValueError("permissions must be unique")
        return values


class ChatAttachment(StrictRequestModel):
    """由可信 Java 服务解析后的会话附件，不接受浏览器直传地址。"""

    attachment_id: str = Field(
        ...,
        pattern=(
            r"^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-"
            r"[89ab][0-9a-f]{3}-[0-9a-f]{12}$"
        ),
    )
    name: str = Field(..., min_length=1, max_length=255)
    mime_type: str = Field(
        ...,
        min_length=3,
        max_length=128,
        pattern=r"^[a-z0-9][a-z0-9.+-]*/[a-z0-9][a-z0-9.+-]*$",
    )
    size: int = Field(..., ge=1, le=MAX_ATTACHMENT_BYTES)
    kind: Literal["image", "document"]
    image_url: str | None = Field(
        default=None,
        min_length=8,
        max_length=4096,
        pattern=r"^https?://[^\s]+$",
    )
    extracted_text: str | None = Field(
        default=None,
        min_length=1,
        max_length=MAX_ATTACHMENT_TEXT_CHARS,
    )
    truncated: bool = False

    @model_validator(mode="after")
    def validate_content(self) -> "ChatAttachment":
        if self.kind == "image":
            if not self.mime_type.startswith("image/") or not self.image_url:
                raise ValueError("image attachment requires image mime type and image_url")
        else:
            if self.image_url is not None or not self.extracted_text:
                raise ValueError(
                    "document attachment requires extracted_text and no image_url"
                )
        return self


class ImageOcrRequest(StrictRequestModel):
    """由可信 Java 服务提交的私有 OSS 图片 OCR 请求。"""

    name: str = Field(..., min_length=1, max_length=255)
    mime_type: str = Field(
        ...,
        min_length=3,
        max_length=128,
        pattern=r"^image/[a-z0-9][a-z0-9.+-]*$",
    )
    image_url: str = Field(
        ...,
        min_length=8,
        max_length=4096,
        pattern=r"^https?://[^\s]+$",
    )
    llm_config: LLMConfig | None = None


class ChatRequest(StrictRequestModel):
    """同步和流式聊天的请求体。"""

    message: str = Field(default="", max_length=MAX_CHAT_MESSAGE_CHARS)
    attachments: list[ChatAttachment] = Field(
        default_factory=list,
        max_length=MAX_CHAT_ATTACHMENTS,
    )
    mode: ChatMode = ChatMode.CHAT
    context_data: Any | None = None
    style: str = Field(default="professional", pattern=r"^(professional|casual)$")
    user_id: str | None = Field(default=None, min_length=1, max_length=128)
    thread_id: str | None = Field(
        default=None,
        min_length=1,
        max_length=128,
        pattern=r"^[A-Za-z0-9][A-Za-z0-9._:@/-]{0,127}$",
        validation_alias=AliasChoices("thread_id", "session_id"),
        description=(
            "Conversation identifier used by the checkpointer; distinct from user_id"
        ),
    )
    business_tag: str | None = Field(default=None, max_length=128)
    max_iterations: int | None = Field(default=None, ge=1, le=20)
    user_context: UserContext | None = None
    agent_request_id: str | None = Field(
        default=None,
        min_length=1,
        max_length=128,
        pattern=r"^req-[a-f0-9]{32}$",
    )
    tool_access_token: SecretStr | None = Field(
        default=None,
        min_length=32,
        max_length=256,
        repr=False,
    )
    llm_config: LLMConfig | None = None

    @field_validator("message")
    @classmethod
    def normalize_message(cls, value: str) -> str:
        """标准化消息；只有存在附件时才允许空文本。"""
        return value.strip()

    @field_validator("business_tag")
    @classmethod
    def validate_business_tag(cls, value: str | None) -> str | None:
        """验证业务标签，确保为单行文本。"""
        if value is None:
            return None
        if any(character in value for character in ("\r", "\n", "\x00")):
            raise ValueError("business_tag must be a single-line label")
        return value

    @model_validator(mode="after")
    def validate_mode_payload(self) -> "ChatRequest":
        """验证模式负载，确保上下文分析模式有上下文数据。"""
        if not self.message and not self.attachments:
            raise ValueError("message or attachments must be provided")
        if self.mode == ChatMode.CONTEXT_ANALYSIS and self.attachments:
            raise ValueError("attachments are only supported in chat mode")
        attachment_ids = [item.attachment_id for item in self.attachments]
        if len(set(attachment_ids)) != len(attachment_ids):
            raise ValueError("attachment ids must be unique")
        if self.user_context is not None and self.user_id is None:
            raise ValueError("user_id is required when user_context is provided")
        if (self.agent_request_id is None) != (self.tool_access_token is None):
            raise ValueError(
                "agent_request_id and tool_access_token must be provided together"
            )
        if self.tool_access_token is not None and self.user_context is None:
            raise ValueError("user_context is required when tool access is provided")
        if self.mode == ChatMode.CONTEXT_ANALYSIS and self.context_data is None:
            raise ValueError("context_data is required for context_analysis mode")
        if self.context_data is not None:
            try:
                encoded = json.dumps(
                    self.context_data,
                    ensure_ascii=False,
                    separators=(",", ":"),
                ).encode("utf-8")
            except (TypeError, ValueError) as exc:
                raise ValueError("context_data must be JSON serializable") from exc
            if len(encoded) > MAX_CONTEXT_JSON_BYTES:
                raise ValueError(
                    f"context_data exceeds {MAX_CONTEXT_JSON_BYTES} encoded bytes"
                )
        return self


MAX_NOVEL_SETTINGS = 60
MAX_NOVEL_SETTING_CHARS = 4_000
MAX_NOVEL_FORESHADOWS = 40
MAX_NOVEL_FORESHADOW_DESCRIPTION_CHARS = 1_000
MAX_NOVEL_WORK_CONTEXT_JSON_BYTES = 256 * 1024


class NovelSettingItem(StrictRequestModel):
    """作品设定卡条目，由 Java 从设定表加载后提交。"""

    setting_type: str = Field(
        ...,
        min_length=1,
        max_length=32,
        pattern=r"^(character|world|outline|item|organization|event|style|other)$",
        validation_alias=AliasChoices("setting_type", "settingType"),
    )
    title: str = Field(..., min_length=1, max_length=128)
    content: str = Field(..., min_length=1, max_length=MAX_NOVEL_SETTING_CHARS)


class NovelForeshadowItem(StrictRequestModel):
    """作品伏笔条目，由 Java 从伏笔表加载后提交，仅含未解（已埋/待解）伏笔。"""

    title: str = Field(..., min_length=1, max_length=128)
    description: str | None = Field(
        default=None,
        min_length=1,
        max_length=MAX_NOVEL_FORESHADOW_DESCRIPTION_CHARS,
    )
    status: str = Field(
        default="buried",
        pattern=r"^(buried|pending|resolved)$",
        description="buried-已埋, pending-待解, resolved-已解",
    )
    priority: str = Field(
        default="medium",
        pattern=r"^(high|medium|low)$",
        description="high-高, medium-中, low-低",
    )
    keyword: str | None = Field(default=None, min_length=1, max_length=128)
    resolve_chapter_no: int | None = Field(
        default=None,
        ge=1,
        description="计划回收章节号",
        validation_alias=AliasChoices("resolve_chapter_no", "resolveChapterNo"),
    )


class NovelWorkContext(StrictRequestModel):
    """小说创作的作品上下文，由受信任 Java 服务从作品库组装。"""

    work_id: int | None = Field(
        default=None, ge=1, validation_alias=AliasChoices("work_id", "workId")
    )
    work_name: str = Field(
        ...,
        min_length=1,
        max_length=128,
        validation_alias=AliasChoices("work_name", "workName"),
    )
    work_type: Literal["short", "novel"] = Field(
        default="novel", validation_alias=AliasChoices("work_type", "workType")
    )
    genre: str | None = Field(default=None, min_length=1, max_length=64)
    synopsis: str | None = Field(default=None, min_length=1, max_length=4_000)
    chapter_title: str | None = Field(
        default=None,
        min_length=1,
        max_length=128,
        validation_alias=AliasChoices("chapter_title", "chapterTitle"),
    )
    chapter_synopsis: str | None = Field(
        default=None,
        min_length=1,
        max_length=4_000,
        validation_alias=AliasChoices("chapter_synopsis", "chapterSynopsis"),
    )
    manuscript_tail: str | None = Field(
        default=None,
        min_length=1,
        max_length=8_000,
        description="当前章节正文末尾片段，用于无缝续写",
        validation_alias=AliasChoices("manuscript_tail", "manuscriptTail"),
    )
    settings: list[NovelSettingItem] = Field(
        default_factory=list,
        max_length=MAX_NOVEL_SETTINGS,
    )
    foreshadows: list[NovelForeshadowItem] = Field(
        default_factory=list,
        max_length=MAX_NOVEL_FORESHADOWS,
        description="未解伏笔（已埋/待解），用于保持情节线索连续",
    )
    pacing_level: str | None = Field(
        default=None,
        min_length=1,
        max_length=16,
        pattern=r"^(relaxed|steady|balanced|intense|rapid)$",
        description="作品节奏档位：relaxed-舒缓, steady-平稳, balanced-均衡, intense-紧凑, rapid-激烈",
        validation_alias=AliasChoices("pacing_level", "pacingLevel"),
    )

    @model_validator(mode="after")
    def validate_total_bytes(self) -> "NovelWorkContext":
        """作品上下文整体受编码后字节上限约束，防止提示词膨胀。"""
        payload = self.model_dump(mode="json", exclude_none=True)
        if not payload.get("settings"):
            payload.pop("settings", None)
        if not payload.get("foreshadows"):
            payload.pop("foreshadows", None)
        substantive = set(payload) - {"work_id", "work_type", "work_name"}
        if not substantive:
            raise ValueError("work_context must carry at least one piece of work data")
        try:
            encoded = json.dumps(
                payload,
                ensure_ascii=False,
                separators=(",", ":"),
            ).encode("utf-8")
        except (TypeError, ValueError) as exc:
            raise ValueError("work_context must be JSON serializable") from exc
        if len(encoded) > MAX_NOVEL_WORK_CONTEXT_JSON_BYTES:
            raise ValueError(
                f"work_context exceeds {MAX_NOVEL_WORK_CONTEXT_JSON_BYTES} encoded bytes"
            )
        return self


class NovelSynopsisRequest(StrictRequestModel):
    """「根据书名自动编写故事梗概」的轻量请求，直接调用 LLM，不进入 Agent。"""

    work_name: str = Field(
        ...,
        min_length=1,
        max_length=128,
        validation_alias=AliasChoices("work_name", "workName"),
    )
    work_type: Literal["short", "novel"] = Field(
        default="novel", validation_alias=AliasChoices("work_type", "workType")
    )
    genre: str | None = Field(default=None, min_length=1, max_length=64)
    llm_config: LLMConfig | None = None

    @field_validator("work_name", "genre", mode="before")
    @classmethod
    def reject_blank_fields(cls, value: str | None) -> str | None:
        if value is None:
            return None
        normalized = value.strip()
        return normalized if normalized else None


class NovelOutlineChapterItem(BaseModel):
    """参与大纲生成的现有章节条目（Java 侧章节列表的轻量子集）。"""

    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    chapter_no: int = Field(validation_alias=AliasChoices("chapter_no", "chapterNo"))
    chapter_title: str | None = Field(
        default=None, max_length=128,
        validation_alias=AliasChoices("chapter_title", "chapterTitle"),
    )
    chapter_brief: str | None = Field(
        default=None, max_length=4000,
        validation_alias=AliasChoices("chapter_brief", "chapterBrief"),
    )


class NovelOutlineRequest(StrictRequestModel):
    """「生成小说三层大纲（全书→卷→章）」的请求，直接调用 LLM，不进入 Agent。

    携带作品上下文、现有章节列表与现有大纲树，输出结构化三层大纲
    及大纲-章节断链检查报告。
    """

    work_context: NovelWorkContext | None = None
    chapters: list[NovelOutlineChapterItem] = Field(default_factory=list)
    outline_tree: list[dict[str, Any]] = Field(default_factory=list)
    llm_config: LLMConfig | None = None

    @field_validator("outline_tree")
    @classmethod
    def bound_outline_tree(cls, value: list[dict[str, Any]]) -> list[dict[str, Any]]:
        """限制现有大纲树规模，避免请求体过大。"""
        if len(value) > 500:
            raise ValueError("outline_tree must not exceed 500 nodes")
        return value


MAX_NOVEL_PACING_CONTENT_CHARS = 100_000


class NovelPacingRequest(StrictRequestModel):
    """「章节节奏评分与建议」的请求，直接调用 LLM，不进入 Agent。

    携带章节正文（与可选的目标节奏档位），输出结构化节奏评分、
    问题清单与修改建议；建议与精修模板能力呼应。
    """

    work_name: str = Field(
        ...,
        min_length=1,
        max_length=128,
        validation_alias=AliasChoices("work_name", "workName"),
    )
    genre: str | None = Field(default=None, min_length=1, max_length=64)
    chapter_title: str | None = Field(
        default=None,
        min_length=1,
        max_length=128,
        validation_alias=AliasChoices("chapter_title", "chapterTitle"),
    )
    pacing_level: str | None = Field(
        default=None,
        min_length=1,
        max_length=16,
        pattern=r"^(relaxed|steady|balanced|intense|rapid)$",
        validation_alias=AliasChoices("pacing_level", "pacingLevel"),
    )
    content: str = Field(
        ...,
        min_length=1,
        max_length=MAX_NOVEL_PACING_CONTENT_CHARS,
        description="待分析章节正文",
    )
    llm_config: LLMConfig | None = None

    @field_validator("content")
    @classmethod
    def reject_blank_content(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("content must not be blank")
        return value


class NovelWriteRequest(StrictRequestModel):
    """小说创作智能体的流式创作请求。"""

    message: str = Field(..., min_length=1, max_length=MAX_CHAT_MESSAGE_CHARS)
    user_id: str = Field(..., min_length=1, max_length=128)
    thread_id: str = Field(
        ...,
        min_length=1,
        max_length=128,
        pattern=r"^[A-Za-z0-9][A-Za-z0-9._:@/-]{0,127}$",
        validation_alias=AliasChoices("thread_id", "session_id"),
        description=(
            "Per-work conversation identifier used by the checkpointer; "
            "distinct from user_id"
        ),
    )
    work_context: NovelWorkContext | None = None
    max_iterations: int | None = Field(default=None, ge=1, le=20)
    llm_config: LLMConfig | None = None

    @field_validator("message")
    @classmethod
    def reject_blank_message(cls, value: str) -> str:
        """拒绝空白创作指令。"""
        normalized = value.strip()
        if not normalized:
            raise ValueError("message must not be blank")
        return normalized


class ActionResumeRequest(StrictRequestModel):
    """Java 登录端确认后恢复同一 LangGraph checkpoint 的严格请求。"""

    action_id: str = Field(
        ..., min_length=1, max_length=64, pattern=r"^[A-Za-z0-9_-]+$"
    )
    decision: Literal["approve", "reject"]
    user_id: str = Field(..., min_length=1, max_length=128)
    thread_id: str = Field(
        ...,
        min_length=1,
        max_length=128,
        pattern=r"^[A-Za-z0-9][A-Za-z0-9._:@/-]{0,127}$",
    )
    style: str = Field(default="professional", pattern=r"^(professional|casual)$")
    max_iterations: int | None = Field(default=None, ge=1, le=20)
    user_context: UserContext
    agent_request_id: str = Field(
        ..., min_length=1, max_length=128, pattern=r"^req-[a-f0-9]{32}$"
    )
    tool_access_token: SecretStr = Field(
        ..., min_length=32, max_length=256, repr=False
    )
    llm_config: LLMConfig | None = None


class DeleteChatThreadRequest(StrictRequestModel):
    """来自受信任Java服务的删除持久短期记忆请求。"""

    user_id: str = Field(..., min_length=1, max_length=128)
    thread_id: str = Field(
        ...,
        min_length=1,
        max_length=128,
        pattern=r"^[A-Za-z0-9][A-Za-z0-9._:@/-]{0,127}$",
        validation_alias=AliasChoices("thread_id", "session_id"),
    )


class MemoryUserRequest(StrictRequestModel):
    """来自受信任 Java 服务的长期记忆用户范围。"""

    user_id: str = Field(..., min_length=1, max_length=128)


class MemoryPreferenceRequest(MemoryUserRequest):
    """用户在设置界面明确修改一个规范化回答偏好。"""

    preference: MemoryPreferenceName
    value: str = Field(..., min_length=1, max_length=64)

    @model_validator(mode="after")
    def validate_preference_value(self) -> "MemoryPreferenceRequest":
        from app.services.memory import validate_preference_value

        validate_preference_value(self.preference, self.value)
        return self


class SmartQuestionHistoryItem(StrictRequestModel):
    """从Java历史存储传输的单个对话项。"""

    content: str = Field(..., min_length=1, max_length=MAX_CHAT_MESSAGE_CHARS)
    role: str | None = Field(default=None, pattern=r"^(user|assistant)$")
    is_user: bool | None = Field(
        default=None,
        validation_alias=AliasChoices("is_user", "isUser"),
    )
    message_type: str | None = Field(
        default=None,
        pattern=r"^(user|assistant)$",
        validation_alias=AliasChoices("message_type", "messageType"),
    )

    @model_validator(mode="after")
    def require_unambiguous_role(self) -> "SmartQuestionHistoryItem":
        """验证角色标识符必须明确且一致。"""
        candidates = []
        if self.role is not None:
            candidates.append(self.role)
        if self.is_user is not None:
            candidates.append("user" if self.is_user else "assistant")
        if self.message_type is not None:
            candidates.append(self.message_type)
        if not candidates:
            raise ValueError("one role discriminator is required")
        if len(set(candidates)) > 1:
            raise ValueError("role discriminators must agree")
        return self

    def resolved_role(self) -> str:
        """解析并返回确定的角色。"""
        if self.role is not None:
            return self.role
        if self.is_user is not None:
            return "user" if self.is_user else "assistant"
        return self.message_type or "user"


class SmartQuestionsRequest(StrictRequestModel):
    """智能问题生成请求。"""
    chat_history: list[SmartQuestionHistoryItem] = Field(
        ...,
        min_length=1,
        max_length=100,
    )
    user_id: str | None = Field(default=None, max_length=128)
    llm_config: LLMConfig | None = None


class SmartQuestionsOutput(BaseModel):
    """智能问题输出结果。"""
    model_config = ConfigDict(extra="forbid")

    questions: list[str] = Field(..., min_length=3, max_length=3)

    @field_validator("questions")
    @classmethod
    def validate_questions(cls, questions: list[str]) -> list[str]:
        """验证问题列表，确保非空、长度限制和唯一性。"""
        normalized = [question.strip() for question in questions]
        if any(not question for question in normalized):
            raise ValueError("questions must not be blank")
        if any(len(question) > 256 for question in normalized):
            raise ValueError("questions must not exceed 256 characters")
        if len({question.casefold() for question in normalized}) != len(normalized):
            raise ValueError("questions must be unique")
        return normalized


class ExtractionSchemaName(str, Enum):
    GENERAL = "general"
    PERSON = "person"
    EVENT = "event"


class ExtractionStrategy(str, Enum):
    TOOL = "tool"
    PROVIDER = "provider"


CustomFieldName = Annotated[
    str,
    Field(min_length=1, max_length=64, pattern=r"^[A-Za-z_][A-Za-z0-9_]*$"),
]


class ExtractRequest(StrictRequestModel):
    """信息提取请求。"""
    text: str = Field(..., min_length=1, max_length=MAX_EXTRACT_TEXT_CHARS)
    schema_name: ExtractionSchemaName = ExtractionSchemaName.GENERAL
    strategy: ExtractionStrategy = ExtractionStrategy.TOOL
    custom_fields: list[CustomFieldName] | None = Field(
        default=None,
        min_length=1,
        max_length=20,
    )

    @field_validator("text")
    @classmethod
    def reject_blank_text(cls, value: str) -> str:
        """拒绝空白文本。"""
        normalized = value.strip()
        if not normalized:
            raise ValueError("text must not be blank")
        return normalized

    @field_validator("custom_fields")
    @classmethod
    def unique_custom_fields(
        cls,
        fields: list[str] | None,
    ) -> list[str] | None:
        """验证自定义字段必须唯一。"""
        if fields is None:
            return None
        if len(set(fields)) != len(fields):
            raise ValueError("custom_fields must be unique")
        return fields
