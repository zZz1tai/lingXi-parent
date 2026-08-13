"""
所有API端点的Pydantic v2响应模型。

所有响应遵循统一的信封格式：
{
    "success": bool,
    "message": str,
    "data": <payload> | null,
    "error": <error_info> | null
}
"""

from __future__ import annotations

from typing import Any, Literal, Optional

from pydantic import AliasChoices, BaseModel, Field


# ── 基础响应信封 ────────────────────────────────────────────────────────────

class BaseResponse(BaseModel):
    """通用响应信封。"""

    success: bool = True
    message: str = "ok"


# ── 健康检查 ────────────────────────────────────────────────────────────────

class HealthData(BaseModel):
    """健康检查端点的有效负载。"""

    status: str = "running"
    version: str = "1.0.0"
    model: str = ""
    search_tool: str = "tavily"
    knowledge_tool: str = "disabled"


class HealthResponse(BaseResponse):
    """``GET /health``端点的响应。"""

    data: HealthData


# ── 对话响应 ────────────────────────────────────────────────────────────────

class MemoryPreferenceData(BaseModel):
    """用户可查看的规范化长期回答偏好。"""

    preference: str
    value: str
    updated_at: str


class ChatData(BaseModel):
    """聊天响应的有效负载。"""

    response: str = Field(..., description="Agent's final answer")
    tool_calls: list["ToolCallRecord"] = Field(
        default_factory=list,
        description="List of tool invocations made during the agent run",
    )
    iterations: int = Field(default=0, description="Number of agent loop iterations")
    request_id: str = Field(default="", description="Request trace ID")
    thread_id: str = Field(default="", description="Conversation checkpoint ID")
    memory_saved: list[MemoryPreferenceData] = Field(default_factory=list)


class ToolCallRecord(BaseModel):
    """返回给API客户端的规范化已完成工具调用记录。"""

    tool: str = "unknown"
    tool_call_id: str = ""
    output: str = ""
    artifact: Any | None = None
    status: str = "success"


class ChatResponse(BaseResponse):
    """``POST /api/v1/chat/invoke``端点的响应。"""

    data: Optional[ChatData] = None


class ImageOcrData(BaseModel):
    """图片 OCR 的有界纯文本结果。"""

    text: str | None = None
    truncated: bool = False
    request_id: str = ""


class ImageOcrResponse(BaseResponse):
    """``POST /api/v1/chat/ocr``端点的响应。"""

    data: ImageOcrData


class NovelSynopsisData(BaseModel):
    """根据书名生成的自动故事梗概。"""

    synopsis: str


class NovelSynopsisResponse(BaseResponse):
    """``POST /api/v1/novel/synopsis/generate``端点的响应。"""

    data: NovelSynopsisData


class NovelOutlineData(BaseModel):
    """生成的小说三层大纲树与断链检查报告。

    ``tree`` 为 BOOK → VOLUME → CHAPTER 嵌套结构；``gaps`` 列出
    大纲与现有章节不一致的条目及修复建议，由 Java 侧持久化。
    """

    tree: list[dict[str, Any]]
    gaps: list[dict[str, Any]] = Field(default_factory=list)


class NovelOutlineResponse(BaseResponse):
    """``POST /api/v1/novel/outline/generate``端点的响应。"""

    data: NovelOutlineData


class NovelPacingData(BaseModel):
    """章节节奏评分与建议。

    ``score`` 为 1~100 总分；``level`` 为模型判断的实际节奏档位；
    ``dimensions`` 为四个维度评分；``issues`` 为问题清单；
    ``suggestions`` 为总体建议（与精修模板能力呼应）。
    """

    score: int
    score_note: str = Field(validation_alias=AliasChoices("score_note", "scoreNote"))
    level: str
    level_note: str = Field(validation_alias=AliasChoices("level_note", "levelNote"))
    summary: str
    dimensions: list[dict[str, Any]] = Field(default_factory=list)
    issues: list[dict[str, Any]] = Field(default_factory=list)
    suggestions: list[str] = Field(default_factory=list)


class NovelPacingResponse(BaseResponse):
    """``POST /api/v1/novel/pacing/analyze``端点的响应。"""

    data: NovelPacingData


class MemoryListData(BaseModel):
    """长期记忆功能状态与当前用户偏好。"""

    enabled: bool
    items: list[MemoryPreferenceData] = Field(default_factory=list)


class MemoryListResponse(BaseResponse):
    """长期记忆列表响应。"""

    data: MemoryListData


class MemoryMutationData(BaseModel):
    """长期记忆修改或清空结果。"""

    enabled: bool
    affected: int = 0
    item: MemoryPreferenceData | None = None


class MemoryMutationResponse(BaseResponse):
    """长期记忆修改响应。"""

    data: MemoryMutationData


class SmartQuestionsData(BaseModel):
    """从结构化聊天历史生成的已验证智能问题。"""

    questions: list[str] = Field(..., min_length=3, max_length=3)
    request_id: str = Field(default="", description="Request trace ID")


class SmartQuestionsResponse(BaseResponse):
    """``POST /api/v1/chat/smart-questions``端点的响应。"""

    data: Optional[SmartQuestionsData] = None


# ── 结构化提取 ──────────────────────────────────────────────────────────────

class ExtractData(BaseModel):
    """信息提取响应的有效负载。"""

    result: dict[str, Any] = Field(
        ..., description="Extracted structured data"
    )
    strategy: str = Field(
        ..., description="Strategy used: 'tool' or 'provider'"
    )
    schema_name: str = Field(default="", description="Schema name used")
    request_id: str = Field(default="", description="Request trace ID")


class ExtractResponse(BaseResponse):
    """``POST /api/v1/extract``端点的响应。"""

    data: Optional[ExtractData] = None


# ── 错误响应 ────────────────────────────────────────────────────────────────

class ErrorDetail(BaseModel):
    """结构化错误信息。"""

    code: str
    message: str


class ErrorResponse(BaseModel):
    """统一的错误响应信封。"""

    success: bool = False
    error: ErrorDetail


# ── SSE 流式事件 ───────────────────────────────────────────────────────────

class StreamEvent(BaseModel):
    """单个SSE事件负载的模式。"""

    type: Literal[
        "token",
        "update",
        "custom",
        "heartbeat",
        "tool_start",
        "tool_progress",
        "tool_end",
        "ui_start",
        "ui_delta",
        "ui_complete",
        "ui_error",
        "citation",
        "clarification",
        "memory_saved",
        "approval_required",
        "action_completed",
        "action_rejected",
        "done",
        "error",
    ] = Field(
        ...,
        description=(
            "Event type: token / tool_start / tool_progress / tool_end / ui_start / "
            "ui_delta / ui_complete / ui_error / citation / done / error"
        ),
    )
    content: Optional[str] = Field(default=None, description="Text content (for token/done events)")
    tool: Optional[str] = Field(default=None, description="Tool name (for tool_start/tool_end)")
    tool_input: Optional[dict[str, Any]] = Field(default=None, description="Tool input params")
    tool_output: Optional[str] = Field(default=None, description="Tool output (for tool_end)")
    call_id: Optional[str] = Field(
        default=None,
        max_length=64,
        description="Unique tool call id, links tool_start/tool_progress/tool_end",
    )
    sequence: Optional[int] = Field(
        default=None,
        ge=1,
        le=200,
        description="Ordinal of the tool call within the stream",
    )
    elapsed_ms: Optional[int] = Field(
        default=None,
        ge=0,
        le=3_600_000,
        description="Tool execution duration in milliseconds",
    )
    input_summary: Optional[str] = Field(
        default=None,
        max_length=256,
        description="Safe, user-friendly summary of the tool call input",
    )
    data: Any | None = Field(default=None, description="Structured update/custom payload")
    render_id: Optional[str] = Field(
        default=None,
        max_length=64,
        description="Unique OpenUI render id, links ui_start/ui_delta/ui_complete",
    )
    schema_version: Optional[int] = Field(
        default=None,
        ge=1,
        le=99,
        description="OpenUI spec schema version",
    )
    spec: Any | None = Field(
        default=None,
        description="Validated OpenUI section list (for ui_complete)",
    )
    delta: Any | None = Field(
        default=None,
        description="Partial OpenUI section list (for ui_delta)",
    )
    fallback_markdown: Optional[str] = Field(
        default=None,
        max_length=200_000,
        description="Markdown fallback kept when OpenUI cannot render",
    )
    code: Optional[str] = Field(
        default=None,
        max_length=64,
        description="Error code (for ui_error)",
    )
    content_blocks: list[dict[str, Any]] | None = Field(
        default=None,
        description="Normalized LangChain v1 content blocks",
    )
    request_id: Optional[str] = Field(default=None, description="Request trace ID")
    thread_id: Optional[str] = Field(default=None, description="Conversation checkpoint ID")
