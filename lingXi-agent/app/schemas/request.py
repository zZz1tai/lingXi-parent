"""Strict Pydantic v2 request contracts for Agent-facing endpoints."""

from __future__ import annotations

import json
from enum import Enum
from typing import Annotated, Any

from pydantic import (
    AliasChoices,
    BaseModel,
    ConfigDict,
    Field,
    field_validator,
    model_validator,
)


MAX_CHAT_MESSAGE_CHARS = 32_000
MAX_CONTEXT_JSON_BYTES = 256 * 1024
MAX_EXTRACT_TEXT_CHARS = 32_000


class StrictRequestModel(BaseModel):
    model_config = ConfigDict(
        extra="forbid",
        str_strip_whitespace=True,
        populate_by_name=True,
    )


class LLMConfig(StrictRequestModel):
    """Allowlisted OpenAI-compatible model configuration from the Java service."""

    api_key: str = Field(..., min_length=1, max_length=8192, repr=False)
    model: str = Field(..., min_length=1, max_length=128)
    base_url: str | None = Field(default=None, min_length=8, max_length=2048)
    timeout_seconds: int | None = Field(default=None, ge=1, le=1800)

    @field_validator("api_key", "model")
    @classmethod
    def reject_blank_values(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("value must not be blank")
        return value.strip()


class ChatMode(str, Enum):
    CHAT = "chat"
    CONTEXT_ANALYSIS = "context_analysis"


class ChatRequest(StrictRequestModel):
    """Request body for synchronous and streaming chat."""

    message: str = Field(..., min_length=1, max_length=MAX_CHAT_MESSAGE_CHARS)
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
    llm_config: LLMConfig | None = None

    @field_validator("message")
    @classmethod
    def reject_blank_message(cls, value: str) -> str:
        normalized = value.strip()
        if not normalized:
            raise ValueError("message must not be blank")
        return normalized

    @field_validator("business_tag")
    @classmethod
    def validate_business_tag(cls, value: str | None) -> str | None:
        if value is None:
            return None
        if any(character in value for character in ("\r", "\n", "\x00")):
            raise ValueError("business_tag must be a single-line label")
        return value

    @model_validator(mode="after")
    def validate_mode_payload(self) -> "ChatRequest":
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


class DeleteChatThreadRequest(StrictRequestModel):
    """Trusted Java request to delete durable short-term memory."""

    user_id: str = Field(..., min_length=1, max_length=128)
    thread_id: str = Field(
        ...,
        min_length=1,
        max_length=128,
        pattern=r"^[A-Za-z0-9][A-Za-z0-9._:@/-]{0,127}$",
        validation_alias=AliasChoices("thread_id", "session_id"),
    )


class SmartQuestionHistoryItem(StrictRequestModel):
    """One conversation item transported from the Java history store."""

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
        if self.role is not None:
            return self.role
        if self.is_user is not None:
            return "user" if self.is_user else "assistant"
        return self.message_type or "user"


class SmartQuestionsRequest(StrictRequestModel):
    chat_history: list[SmartQuestionHistoryItem] = Field(
        ...,
        min_length=1,
        max_length=100,
    )
    user_id: str | None = Field(default=None, max_length=128)
    llm_config: LLMConfig | None = None


class SmartQuestionsOutput(BaseModel):
    model_config = ConfigDict(extra="forbid")

    questions: list[str] = Field(..., min_length=3, max_length=3)

    @field_validator("questions")
    @classmethod
    def validate_questions(cls, questions: list[str]) -> list[str]:
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
        if fields is None:
            return None
        if len(set(fields)) != len(fields):
            raise ValueError("custom_fields must be unique")
        return fields
