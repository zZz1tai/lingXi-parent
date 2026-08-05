"""
视频生成API端点的Pydantic v2请求/响应模型。

通过提供商适配器处理图像和视频生成。
"""

from __future__ import annotations

from enum import Enum
from typing import Annotated, Optional

from pydantic import BaseModel, ConfigDict, Field, StringConstraints


ApiKeyText = Annotated[
    str,
    StringConstraints(strip_whitespace=True, min_length=1, max_length=8192),
]
ModelNameText = Annotated[
    str,
    StringConstraints(strip_whitespace=True, min_length=1, max_length=256),
]
HttpUrlText = Annotated[
    str,
    StringConstraints(strip_whitespace=True, min_length=1, max_length=4096),
]
IdentifierText = Annotated[
    str,
    StringConstraints(strip_whitespace=True, min_length=1, max_length=256),
]


# ── 枚举定义 ────────────────────────────────────────────────────────────────

class ImageAspectRatio(str, Enum):
    """支持的图像宽高比。"""
    LANDSCAPE_16_9 = "16:9"
    PORTRAIT_9_16 = "9:16"
    SQUARE_1_1 = "1:1"


class TaskStatus(str, Enum):
    """视频生成任务状态。"""
    PENDING = "PENDING"
    RUNNING = "RUNNING"
    SUCCEEDED = "SUCCEEDED"
    FAILED = "FAILED"
    CANCELED = "CANCELED"
    UNKNOWN = "UNKNOWN"


# ── 图片生成 ────────────────────────────────────────────────────────────────

class GenerateImageRequest(BaseModel):
    """``POST /api/v1/video/generate-image``端点的请求体。"""

    api_key: ApiKeyText = Field(
        ...,
        description="DashScope API key",
    )
    model: ModelNameText = Field(
        ...,
        description="Image generation model name",
    )
    base_url: HttpUrlText = Field(
        ...,
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
    reference_image_urls: Optional[list[HttpUrlText]] = Field(
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
    """``POST /api/v1/video/generate-image``端点的响应。"""

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


# ── 视频生成 ────────────────────────────────────────────────────────────────

class SubmitVideoRequest(BaseModel):
    """``POST /api/v1/video/submit-video``端点的请求体。"""

    api_key: ApiKeyText = Field(
        ...,
        description="Video provider API key",
    )
    provider: IdentifierText = Field(
        default="happyhorse",
        description="Video provider adapter code",
    )
    model: ModelNameText = Field(
        ...,
        description="Video generation model name",
    )
    base_url: HttpUrlText = Field(
        ...,
        description="Video provider API base URL",
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
    image_url: Optional[HttpUrlText] = Field(
        default=None,
        description="Optional public URL of the keyframe image",
    )
    character_reference_image_urls: list[HttpUrlText] = Field(
        default_factory=list,
        max_length=4,
        description=(
            "Bound character turnaround image URLs. Provider adapters that support "
            "multi-reference video generation must forward these without reordering."
        ),
    )
    scene_reference_image_url: Optional[HttpUrlText] = Field(
        default=None,
        description="Bound scene reference image URL",
    )
    resolution: str = Field(
        ...,
        min_length=1,
        description="Video resolution",
    )
    ratio: str = Field(
        default="16:9",
        min_length=3,
        max_length=8,
        description="Requested output aspect ratio",
    )
    watermark: bool = Field(
        default=False,
        description="Whether the provider should add its video watermark",
    )
    duration_ms: int = Field(
        ...,
        ge=1000,
        le=15000,
        description="Video duration in milliseconds (1000-15000)",
    )
    prompt_extend: bool = Field(
        default=False,
        description="Whether to let the model extend the prompt",
    )
    idempotency_key: Optional[IdentifierText] = Field(
        default=None,
        description=(
            "Stable submission key propagated to the provider gateway so an "
            "ambiguous response can be reconciled without creating a duplicate task"
        ),
    )


class SubmitVideoResponse(BaseModel):
    """``POST /api/v1/video/submit-video``端点的响应。"""

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
        le=15000,
        description="Actual duration accepted by the selected video model",
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
            "True when the video provider may have accepted the request but no task ID was "
            "received; callers must not submit again automatically"
        ),
    )


class QueryVideoRequest(BaseModel):
    """``POST /api/v1/video/query-video``端点的请求体。"""

    api_key: ApiKeyText = Field(
        ...,
        description="DashScope API key",
    )
    base_url: HttpUrlText = Field(
        ...,
        description="DashScope API base URL",
    )
    task_id: IdentifierText = Field(
        ...,
        description="DashScope task ID to query",
    )


class QueryVideoResponse(BaseModel):
    """``POST /api/v1/video/query-video``端点的响应。"""

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
    provider_status: Optional[str] = Field(
        default=None,
        max_length=128,
        description="Original provider status when it is not part of the known status enum",
    )


# ── 模型提供方响应契约 ──────────────────────────────────────────────────────

class ProviderContractModel(BaseModel):
    """足够严格的提供商DTO，忽略无害的附加元数据。"""

    model_config = ConfigDict(extra="ignore")


class ProviderImageContent(ProviderContractModel):
    image: HttpUrlText


class ProviderImageMessage(ProviderContractModel):
    content: list[ProviderImageContent] = Field(min_length=1)


class ProviderImageChoice(ProviderContractModel):
    message: ProviderImageMessage


class ProviderImageOutput(ProviderContractModel):
    choices: list[ProviderImageChoice] = Field(min_length=1)


class GenerateImageProviderResponse(ProviderContractModel):
    output: ProviderImageOutput

    @property
    def image_url(self) -> str:
        return self.output.choices[0].message.content[0].image


class SubmitVideoProviderOutput(ProviderContractModel):
    task_id: IdentifierText


class SubmitVideoProviderResponse(ProviderContractModel):
    output: SubmitVideoProviderOutput


class QueryVideoProviderResult(ProviderContractModel):
    url: Optional[HttpUrlText] = None


class QueryVideoProviderOutput(ProviderContractModel):
    task_status: IdentifierText
    video_url: Optional[HttpUrlText] = None
    message: str = Field(default="", max_length=4000)
    results: list[QueryVideoProviderResult] = Field(default_factory=list)

    @property
    def resolved_video_url(self) -> Optional[str]:
        if self.video_url:
            return self.video_url
        for result in self.results:
            if result.url:
                return result.url
        return None


class QueryVideoProviderResponse(ProviderContractModel):
    output: QueryVideoProviderOutput
