"""
Pydantic v2 request/response models for video generation API endpoints.

Handles text-to-image (QwenImage) and image-to-video (WanxVideo) via DashScope.
"""

from __future__ import annotations

from enum import Enum
from typing import Optional

from pydantic import BaseModel, Field


# ── Enums ────────────────────────────────────────────────────────────────────

class ImageAspectRatio(str, Enum):
    """Supported image aspect ratios."""
    LANDSCAPE_16_9 = "16:9"
    PORTRAIT_9_16 = "9:16"
    SQUARE_1_1 = "1:1"


class TaskStatus(str, Enum):
    """Video generation task status."""
    PENDING = "PENDING"
    RUNNING = "RUNNING"
    SUCCEEDED = "SUCCEEDED"
    FAILED = "FAILED"
    CANCELED = "CANCELED"


# ── Image Generation ─────────────────────────────────────────────────────────

class GenerateImageRequest(BaseModel):
    """Request body for POST /api/v1/video/generate-image."""

    api_key: str = Field(
        ...,
        description="DashScope API key",
    )
    model: str = Field(
        ...,
        min_length=1,
        description="Image generation model name",
    )
    base_url: str = Field(
        ...,
        min_length=1,
        description="DashScope native API base URL",
    )
    asset_type: Optional[str] = Field(
        default=None,
        max_length=64,
        description="Business asset type used by Python to select image model rules",
    )
    prompt: str = Field(
        ...,
        min_length=1,
        max_length=12000,
        description="Text prompt for image generation",
    )
    negative_prompt: Optional[str] = Field(
        default=None,
        max_length=4000,
        description="Negative prompt to exclude certain elements",
    )
    aspect_ratio: Optional[ImageAspectRatio] = Field(
        default=None,
        description="Project-requested image aspect ratio; Python applies asset rules",
    )
    reference_image_urls: Optional[list[str]] = Field(
        default=None,
        max_length=5,
        description=(
            "Up to 5 reference image URLs in input order: "
            "at most 4 character references followed by 1 scene reference"
        ),
    )
    prompt_extend: bool = Field(
        default=True,
        description="Whether to let the model extend the prompt",
    )


class GenerateImageResponse(BaseModel):
    """Response for POST /api/v1/video/generate-image."""

    success: bool = Field(
        ...,
        description="Whether the request succeeded",
    )
    image_url: Optional[str] = Field(
        default=None,
        description="URL of the generated image",
    )
    error: Optional[str] = Field(
        default=None,
        description="Error message if failed",
    )
    status_code: Optional[int] = Field(
        default=None,
        description="HTTP status code from DashScope",
    )
    error_code: Optional[str] = Field(
        default=None,
        description="Stable machine-readable failure code",
    )
    retryable: bool = Field(
        default=False,
        description="Whether a later manual retry is safe",
    )


# ── Video Generation ─────────────────────────────────────────────────────────

class SubmitVideoRequest(BaseModel):
    """Request body for POST /api/v1/video/submit-video."""

    api_key: str = Field(
        ...,
        description="DashScope API key",
    )
    model: str = Field(
        ...,
        min_length=1,
        description="Video generation model name",
    )
    base_url: str = Field(
        ...,
        min_length=1,
        description="DashScope API base URL",
    )
    prompt: str = Field(
        ...,
        min_length=1,
        max_length=12000,
        description=(
            "Unmodified video prompt. The route applies the configured "
            "model's provider limit immediately before submission."
        ),
    )
    negative_prompt: Optional[str] = Field(
        default=None,
        max_length=4000,
        description=(
            "Unmodified negative prompt. The route applies the provider "
            "limit immediately before submission."
        ),
    )
    image_url: str = Field(
        ...,
        description="Public URL of the keyframe image",
    )
    resolution: str = Field(
        ...,
        min_length=1,
        description="Video resolution",
    )
    duration_ms: int = Field(
        ...,
        ge=1000,
        le=10000,
        description="Video duration in milliseconds (1000-10000)",
    )
    prompt_extend: bool = Field(
        default=False,
        description="Whether to let the model extend the prompt",
    )


class SubmitVideoResponse(BaseModel):
    """Response for POST /api/v1/video/submit-video."""

    success: bool = Field(
        ...,
        description="Whether the submission succeeded",
    )
    task_id: Optional[str] = Field(
        default=None,
        description="DashScope task ID for polling",
    )
    normalized_duration_ms: Optional[int] = Field(
        default=None,
        ge=1000,
        le=10000,
        description="Actual duration accepted by the selected Wanx model",
    )
    error: Optional[str] = Field(
        default=None,
        description="Error message if failed",
    )
    status_code: Optional[int] = Field(
        default=None,
        description="HTTP status code from DashScope",
    )
    error_code: Optional[str] = Field(
        default=None,
        description="Stable machine-readable failure code",
    )
    retryable: bool = Field(
        default=False,
        description="Whether a later submission retry is safe",
    )
    submission_uncertain: bool = Field(
        default=False,
        description=(
            "True when Wanx may have accepted the request but no task ID was "
            "received; callers must not submit again automatically"
        ),
    )


class QueryVideoRequest(BaseModel):
    """Request body for POST /api/v1/video/query-video."""

    api_key: str = Field(
        ...,
        description="DashScope API key",
    )
    base_url: str = Field(
        ...,
        min_length=1,
        description="DashScope API base URL",
    )
    task_id: str = Field(
        ...,
        description="DashScope task ID to query",
    )


class QueryVideoResponse(BaseModel):
    """Response for POST /api/v1/video/query-video."""

    success: bool = Field(
        ...,
        description="Whether the query succeeded",
    )
    status: Optional[TaskStatus] = Field(
        default=None,
        description="Task status",
    )
    video_url: Optional[str] = Field(
        default=None,
        description="URL of the generated video (available when SUCCEEDED)",
    )
    error: Optional[str] = Field(
        default=None,
        description="Error message if failed",
    )
    status_code: Optional[int] = Field(
        default=None,
        description="HTTP status code from DashScope",
    )
    error_code: Optional[str] = Field(
        default=None,
        description="Stable machine-readable failure code",
    )
    retryable: bool = Field(
        default=False,
        description="Whether polling may be retried",
    )
