"""
Pydantic v2 response models for all API endpoints.

All responses follow a uniform envelope:
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


# ── Base Envelope ───────────────────────────────────────────────────────────

class BaseResponse(BaseModel):
    """Common response envelope."""

    success: bool = True
    message: str = "ok"


# ── Health ──────────────────────────────────────────────────────────────────

class HealthData(BaseModel):
    """Payload for the health endpoint."""

    status: str = "running"
    version: str = "1.0.0"
    model: str = ""
    search_tool: str = "tavily"


class HealthResponse(BaseResponse):
    """Response for ``GET /health``."""

    data: HealthData


# ── Chat ────────────────────────────────────────────────────────────────────

class ChatData(BaseModel):
    """Payload for chat responses."""

    response: str = Field(..., description="Agent's final answer")
    tool_calls: list["ToolCallRecord"] = Field(
        default_factory=list,
        description="List of tool invocations made during the agent run",
    )
    iterations: int = Field(default=0, description="Number of agent loop iterations")
    request_id: str = Field(default="", description="Request trace ID")
    thread_id: str = Field(default="", description="Conversation checkpoint ID")


class ToolCallRecord(BaseModel):
    """Normalized completed tool invocation returned to API clients."""

    tool: str = "unknown"
    tool_call_id: str = ""
    output: str = ""
    artifact: Any | None = None
    status: str = "success"


class ChatResponse(BaseResponse):
    """Response for ``POST /api/v1/chat/invoke``."""

    data: Optional[ChatData] = None


class SmartQuestionsData(BaseModel):
    """Validated smart questions generated from structured chat history."""

    questions: list[str] = Field(..., min_length=3, max_length=3)
    request_id: str = Field(default="", description="Request trace ID")


class SmartQuestionsResponse(BaseResponse):
    """Response for ``POST /api/v1/chat/smart-questions``."""

    data: Optional[SmartQuestionsData] = None


# ── Extract ─────────────────────────────────────────────────────────────────

class ExtractData(BaseModel):
    """Payload for extraction responses."""

    result: dict[str, Any] = Field(
        ..., description="Extracted structured data"
    )
    strategy: str = Field(
        ..., description="Strategy used: 'tool' or 'provider'"
    )
    schema_name: str = Field(default="", description="Schema name used")
    request_id: str = Field(default="", description="Request trace ID")


class ExtractResponse(BaseResponse):
    """Response for ``POST /api/v1/extract``."""

    data: Optional[ExtractData] = None


# ── Error ───────────────────────────────────────────────────────────────────

class ErrorDetail(BaseModel):
    """Structured error information."""

    code: str
    message: str


class ErrorResponse(BaseModel):
    """Uniform error response envelope."""

    success: bool = False
    error: ErrorDetail


# ── SSE Stream Events ──────────────────────────────────────────────────────

class StreamEvent(BaseModel):
    """Schema for a single SSE event payload."""

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
