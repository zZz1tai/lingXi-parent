"""
Pydantic v2 request models for all API endpoints.

Every incoming request body is validated against these schemas
before reaching the business logic.
"""

from __future__ import annotations

from enum import Enum
from typing import Any, Optional

from pydantic import AliasChoices, BaseModel, Field, field_validator, model_validator


# ── Chat Endpoints ──────────────────────────────────────────────────────────

class LLMConfig(BaseModel):
    """LLM configuration passed from Java backend."""

    api_key: str = Field(
        ...,
        min_length=1,
        description="API key for the LLM provider",
    )
    model: str = Field(
        ...,
        min_length=1,
        description="Model name to use",
    )
    base_url: Optional[str] = Field(
        default=None,
        description="Custom API base URL (for DashScope, Doubao, etc.)",
    )
    timeout_seconds: Optional[int] = Field(
        default=None,
        ge=1,
        le=1800,
        description=(
            "Provider read timeout in seconds. Long-running workloads such as "
            "chapter analysis must supply this explicitly."
        ),
    )


class ChatMode(str, Enum):
    """Explicit chat workflow selected by the Java transport layer."""

    CHAT = "chat"
    CONTEXT_ANALYSIS = "context_analysis"


class ChatRequest(BaseModel):
    """Request body for ``POST /api/v1/chat/invoke`` and ``/stream``."""

    message: str = Field(
        ...,
        min_length=1,
        max_length=100000,
        description="User message to send to the agent",
    )
    mode: ChatMode = Field(
        default=ChatMode.CHAT,
        description="Python-owned prompt workflow to execute",
    )
    context_data: Optional[Any] = Field(
        default=None,
        description="Structured business data for context_analysis mode",
    )
    style: str = Field(
        default="professional",
        pattern=r"^(professional|casual)$",
        description="Response style: 'professional' or 'casual'",
    )
    user_id: Optional[str] = Field(
        default=None,
        max_length=128,
        description="Optional user identifier for tracking",
    )
    business_tag: Optional[str] = Field(
        default=None,
        max_length=256,
        description="Optional business context tag injected into the system prompt",
    )
    max_iterations: Optional[int] = Field(
        default=None,
        ge=1,
        le=20,
        description="Override default max agent iterations for this request",
    )
    llm_config: Optional[LLMConfig] = Field(
        default=None,
        description="LLM configuration passed from Java backend. If provided, overrides env settings.",
    )

    @model_validator(mode="after")
    def validate_mode_payload(self) -> "ChatRequest":
        if self.mode == ChatMode.CONTEXT_ANALYSIS and self.context_data is None:
            raise ValueError("context_data is required for context_analysis mode")
        return self


class SmartQuestionHistoryItem(BaseModel):
    """One conversation item transported from the Java history store."""

    content: str = Field(..., min_length=1, max_length=100000)
    role: Optional[str] = Field(default=None, pattern=r"^(user|assistant)$")
    is_user: Optional[bool] = Field(
        default=None,
        validation_alias=AliasChoices("is_user", "isUser"),
    )
    message_type: Optional[str] = Field(
        default=None,
        validation_alias=AliasChoices("message_type", "messageType"),
    )

    def resolved_role(self) -> str:
        if self.role in ("user", "assistant"):
            return self.role
        if self.is_user is not None:
            return "user" if self.is_user else "assistant"
        return "user" if self.message_type == "user" else "assistant"


class SmartQuestionsRequest(BaseModel):
    """Structured input for the Python-owned smart-question prompt chain."""

    chat_history: list[SmartQuestionHistoryItem] = Field(
        ...,
        min_length=1,
        max_length=100,
    )
    user_id: Optional[str] = Field(default=None, max_length=128)
    llm_config: Optional[LLMConfig] = None


class SmartQuestionsOutput(BaseModel):
    """Strict structured output produced by the smart-question chain."""

    questions: list[str] = Field(..., min_length=3, max_length=3)

    @field_validator("questions")
    @classmethod
    def validate_questions(cls, questions: list[str]) -> list[str]:
        normalized = [question.strip() for question in questions]
        if any(not question for question in normalized):
            raise ValueError("questions must not be blank")
        if len({question.casefold() for question in normalized}) != len(normalized):
            raise ValueError("questions must be unique")
        return normalized


# ── Extract Endpoint ────────────────────────────────────────────────────────

class ExtractRequest(BaseModel):
    """Request body for ``POST /api/v1/extract``."""

    text: str = Field(
        ...,
        min_length=1,
        max_length=50000,
        description="Source text to extract structured information from",
    )
    schema_name: str = Field(
        default="general",
        description="Predefined extraction schema name (e.g. 'general', 'person', 'event')",
    )
    strategy: str = Field(
        default="tool",
        pattern=r"^(tool|provider)$",
        description=(
            "Structured output strategy: "
            "'tool' = ToolStrategy (agent tool-calling), "
            "'provider' = ProviderStrategy (LLM native structured output)"
        ),
    )
    custom_fields: Optional[list[str]] = Field(
        default=None,
        description="Custom field names to extract (overrides schema_name)",
    )
