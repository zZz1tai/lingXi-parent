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

from pydantic import BaseModel, Field


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


class HealthResponse(BaseResponse):
    """``GET /health``端点的响应。"""

    data: HealthData


# ── 对话响应 ────────────────────────────────────────────────────────────────

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
        "tool_end",
        "done",
        "error",
    ] = Field(
        ...,
        description="Event type: token / tool_start / tool_end / done / error",
    )
    content: Optional[str] = Field(default=None, description="Text content (for token/done events)")
    tool: Optional[str] = Field(default=None, description="Tool name (for tool_start/tool_end)")
    tool_input: Optional[dict[str, Any]] = Field(default=None, description="Tool input params")
    tool_output: Optional[str] = Field(default=None, description="Tool output (for tool_end)")
    data: Any | None = Field(default=None, description="Structured update/custom payload")
    content_blocks: list[dict[str, Any]] | None = Field(
        default=None,
        description="Normalized LangChain v1 content blocks",
    )
    request_id: Optional[str] = Field(default=None, description="Request trace ID")
    thread_id: Optional[str] = Field(default=None, description="Conversation checkpoint ID")
