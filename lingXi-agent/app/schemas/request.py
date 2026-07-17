"""
Pydantic v2 request models for all API endpoints.

Every incoming request body is validated against these schemas
before reaching the business logic.
"""

from __future__ import annotations

from typing import Optional

from pydantic import BaseModel, Field


# ── Chat Endpoints ──────────────────────────────────────────────────────────

class LLMConfig(BaseModel):
    """LLM configuration passed from Java backend."""

    api_key: str = Field(
        ...,
        description="API key for the LLM provider",
    )
    model: str = Field(
        default="deepseek-v4-flash",
        description="Model name to use",
    )
    base_url: Optional[str] = Field(
        default=None,
        description="Custom API base URL (for DashScope, Doubao, etc.)",
    )


class ChatRequest(BaseModel):
    """Request body for ``POST /api/v1/chat/invoke`` and ``/stream``."""

    message: str = Field(
        ...,
        min_length=1,
        max_length=10000,
        description="User message to send to the agent",
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
