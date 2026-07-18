"""
Video generation API endpoints.

Provides text-to-image and image-to-video generation via DashScope.
Migrated from Java aiVedio module to centralize DashScope API calls in Python.
"""

from __future__ import annotations

from typing import Any
from urllib.parse import quote, urlsplit, urlunsplit

import httpx
from fastapi import APIRouter

from app.schemas.video import (
    GenerateImageRequest,
    GenerateImageResponse,
    ImageAspectRatio,
    QueryVideoRequest,
    QueryVideoResponse,
    SubmitVideoRequest,
    SubmitVideoResponse,
    TaskStatus,
)
from app.services.video_capabilities import (
    get_model_version as _get_model_version,
    get_video_prompt_limit as _get_prompt_limit,
    normalize_video_duration_ms as _normalize_duration_ms,
    should_include_duration as _should_include_duration,
)
from app.utils.logger import logger

router = APIRouter(prefix="/api/v1/video", tags=["video"])


# ── Constants ────────────────────────────────────────────────────────────────

# Native DashScope paths. The origin and optional gateway prefix come from
# request.base_url so Java can select the provider endpoint without changing
# this service.
IMAGE_GENERATION_PATH = "/services/aigc/multimodal-generation/generation"
VIDEO_SYNTHESIS_PATH = "/services/aigc/video-generation/video-synthesis"
TASK_QUERY_PATH = "/tasks"
MAX_REFERENCE_IMAGE_COUNT = 5
CHARACTER_REFERENCE_ASSET_TYPE = "CHARACTER_REFERENCE"

# Model-specific prompt limits
IMAGE_PROMPT_LIMIT = 2000
VIDEO_NEGATIVE_PROMPT_LIMIT = 500

# Timeout settings (seconds)
HTTP_CONNECT_TIMEOUT = 10
HTTP_READ_TIMEOUT_IMAGE = 180  # 3 minutes for image generation
HTTP_READ_TIMEOUT_VIDEO_SUBMIT = 30
HTTP_READ_TIMEOUT_VIDEO_QUERY = 30


# ── Helper Functions ─────────────────────────────────────────────────────────

def _aspect_ratio_to_size(aspect_ratio: str) -> str:
    """Convert aspect ratio string to pixel size for DashScope."""
    mapping = {
        "9:16": "720*1280",
        "1:1": "1024*1024",
        "16:9": "1280*720",
    }
    return mapping.get(aspect_ratio, "1280*720")


def _resolve_image_aspect_ratio(request: GenerateImageRequest) -> ImageAspectRatio:
    """Apply asset-specific image layout rules only in the Python AI layer."""

    if request.asset_type == CHARACTER_REFERENCE_ASSET_TYPE:
        return ImageAspectRatio.LANDSCAPE_16_9
    return request.aspect_ratio or ImageAspectRatio.LANDSCAPE_16_9


def _is_retryable_status(status_code: int) -> bool:
    """Check if HTTP status code indicates a retryable error."""
    return status_code in (408, 429) or status_code >= 500


def _normalize_api_base_url(base_url: str) -> str:
    """Normalize a configured provider URL to a native DashScope API base.

    Older Java configuration commonly supplies DashScope's OpenAI-compatible
    ``/compatible-mode/v1`` base. Image and video APIs live under the native
    ``/api/v1`` tree, so that well-known suffix is converted deterministically.
    Custom gateway prefixes are otherwise preserved.
    """
    raw_url = (base_url or "").strip().rstrip("/")
    if not raw_url:
        raise ValueError("base_url must not be blank")

    parsed = urlsplit(raw_url)
    if parsed.scheme.lower() not in ("http", "https") or not parsed.netloc:
        raise ValueError("base_url must be an absolute HTTP(S) URL")
    if parsed.query or parsed.fragment:
        raise ValueError("base_url must not contain query parameters or fragments")

    path = parsed.path.rstrip("/")
    compatible_suffix = "/compatible-mode/v1"
    if path.endswith(compatible_suffix):
        path = path[: -len(compatible_suffix)] + "/api/v1"
    elif not path:
        path = "/api/v1"

    return urlunsplit((parsed.scheme.lower(), parsed.netloc, path, "", ""))


def _build_api_url(base_url: str, endpoint_path: str) -> str:
    """Join a normalized API base and one native endpoint path."""
    normalized_base = _normalize_api_base_url(base_url)
    normalized_path = "/" + endpoint_path.lstrip("/")
    return normalized_base + normalized_path


def _build_task_query_url(base_url: str, task_id: str) -> str:
    """Build a task URL without allowing a task ID to alter the URL path."""
    encoded_task_id = quote(task_id.strip(), safe="")
    if not encoded_task_id:
        raise ValueError("task_id must not be blank")
    return _build_api_url(base_url, f"{TASK_QUERY_PATH}/{encoded_task_id}")


def _http_timeout(read_timeout: float) -> httpx.Timeout:
    """Use the dedicated connect timeout instead of applying read time to all phases."""
    return httpx.Timeout(float(read_timeout), connect=float(HTTP_CONNECT_TIMEOUT))


# ── Image Generation Endpoint ────────────────────────────────────────────────

@router.post("/generate-image", response_model=GenerateImageResponse)
async def generate_image(request: GenerateImageRequest) -> GenerateImageResponse:
    """Generate an image from text prompt using DashScope QwenImage.

    This is a synchronous endpoint that waits for the image to be generated.
    """
    aspect_ratio = _resolve_image_aspect_ratio(request)
    logger.info(
        "Generating image | model=%s | aspect_ratio=%s | prompt_len=%d | refs=%d",
        request.model,
        aspect_ratio.value,
        len(request.prompt),
        len(request.reference_image_urls or []),
    )

    if len(request.prompt) > IMAGE_PROMPT_LIMIT:
        return GenerateImageResponse(
            success=False,
            error=(
                f"Image prompt exceeds the provider limit of "
                f"{IMAGE_PROMPT_LIMIT} characters"
            ),
            status_code=400,
            error_code="IMAGE_PROMPT_TOO_LONG",
        )

    reference_image_urls = request.reference_image_urls or []
    if len(reference_image_urls) > MAX_REFERENCE_IMAGE_COUNT:
        return GenerateImageResponse(
            success=False,
            error=f"At most {MAX_REFERENCE_IMAGE_COUNT} reference images are allowed",
            status_code=400,
        )

    # Build content array (reference images first, then text)
    content: list[dict[str, str]] = []
    if reference_image_urls:
        # Preserve all validated references and their order. Java sends up to
        # four character references followed by the scene reference, which
        # must remain last because it controls the output aspect ratio.
        for url in reference_image_urls:
            content.append({"image": url})
    content.append({"text": request.prompt})

    # Build request body
    body: dict[str, Any] = {
        "model": request.model,
        "input": {
            "messages": [
                {
                    "role": "user",
                    "content": content,
                }
            ]
        },
        "parameters": {
            "negative_prompt": request.negative_prompt or "",
            "prompt_extend": request.prompt_extend,
            "watermark": False,
            "size": _aspect_ratio_to_size(aspect_ratio.value),
        },
    }

    headers = {
        "Authorization": f"Bearer {request.api_key}",
        "Content-Type": "application/json; charset=UTF-8",
    }

    try:
        url = _build_api_url(request.base_url, IMAGE_GENERATION_PATH)
        async with httpx.AsyncClient(timeout=_http_timeout(HTTP_READ_TIMEOUT_IMAGE)) as client:
            response = await client.post(
                url,
                json=body,
                headers=headers,
            )

        if response.status_code != 200:
            error_msg = f"DashScope API error: {response.status_code} - {response.text}"
            logger.error(error_msg)
            retryable = _is_retryable_status(response.status_code)
            return GenerateImageResponse(
                success=False,
                error=error_msg,
                status_code=response.status_code,
                error_code=(
                    "IMAGE_PROVIDER_RETRYABLE_HTTP_ERROR"
                    if retryable else "IMAGE_PROVIDER_HTTP_ERROR"
                ),
                retryable=retryable,
            )

        data = response.json()
        image_url = (
            data.get("output", {})
            .get("choices", [{}])[0]
            .get("message", {})
            .get("content", [{}])[0]
            .get("image")
        )

        if not image_url:
            return GenerateImageResponse(
                success=False,
                error="No image URL in response",
                status_code=502,
                error_code="IMAGE_PROVIDER_INVALID_RESPONSE",
            )

        logger.info("Image generated successfully | url=%s", image_url[:100])
        return GenerateImageResponse(success=True, image_url=image_url)

    except httpx.TimeoutException as e:
        error_msg = f"Timeout waiting for image generation: {e}"
        logger.error(error_msg)
        return GenerateImageResponse(
            success=False,
            error=error_msg,
            status_code=504,
            error_code="IMAGE_PROVIDER_TIMEOUT",
            retryable=True,
        )
    except httpx.HTTPError as e:
        error_msg = f"HTTP error during image generation: {e}"
        logger.error(error_msg)
        return GenerateImageResponse(
            success=False,
            error=error_msg,
            status_code=503,
            error_code="IMAGE_PROVIDER_TRANSPORT_ERROR",
            retryable=True,
        )
    except ValueError as e:
        error_msg = f"Invalid image provider configuration: {e}"
        logger.error(error_msg)
        return GenerateImageResponse(
            success=False,
            error=error_msg,
            status_code=400,
            error_code="IMAGE_PROVIDER_CONFIG_ERROR",
        )
    except Exception as e:
        error_msg = f"Unexpected error during image generation: {e}"
        logger.error(error_msg)
        return GenerateImageResponse(
            success=False,
            error=error_msg,
            status_code=500,
            error_code="IMAGE_GENERATION_INTERNAL_ERROR",
        )


# ── Video Submission Endpoint ────────────────────────────────────────────────

@router.post("/submit-video", response_model=SubmitVideoResponse)
async def submit_video(request: SubmitVideoRequest) -> SubmitVideoResponse:
    """Submit an image-to-video generation task to DashScope WanxVideo.

    This is an async endpoint that returns a task ID for polling.
    """
    logger.info(
        "Submitting video task | model=%s | duration_ms=%d | prompt_len=%d",
        request.model,
        request.duration_ms,
        len(request.prompt),
    )

    # Normalize duration
    duration_ms = _normalize_duration_ms(request.duration_ms, request.model)

    # Reject edited prompts that exceed the selected model's limits. Never
    # silently mutate content after the user has reviewed and confirmed it.
    prompt_limit = _get_prompt_limit(request.model)
    if len(request.prompt) > prompt_limit:
        return SubmitVideoResponse(
            success=False,
            normalized_duration_ms=duration_ms,
            error=f"Video prompt exceeds the provider limit of {prompt_limit} characters",
            status_code=400,
            error_code="VIDEO_PROMPT_TOO_LONG",
        )
    negative_prompt = request.negative_prompt or ""
    if len(negative_prompt) > VIDEO_NEGATIVE_PROMPT_LIMIT:
        return SubmitVideoResponse(
            success=False,
            normalized_duration_ms=duration_ms,
            error=(
                "Video negative prompt exceeds the provider limit of "
                f"{VIDEO_NEGATIVE_PROMPT_LIMIT} characters"
            ),
            status_code=400,
            error_code="VIDEO_NEGATIVE_PROMPT_TOO_LONG",
        )
    prompt = request.prompt

    # Build request body
    input_data: dict[str, str] = {
        "prompt": prompt,
        "img_url": request.image_url,
    }
    if negative_prompt:
        input_data["negative_prompt"] = negative_prompt

    parameters: dict[str, Any] = {
        "resolution": request.resolution,
        "prompt_extend": request.prompt_extend,
    }
    if _should_include_duration(request.model):
        parameters["duration"] = duration_ms // 1000  # Convert to seconds

    body: dict[str, Any] = {
        "model": request.model,
        "input": input_data,
        "parameters": parameters,
    }

    headers = {
        "Authorization": f"Bearer {request.api_key}",
        "Content-Type": "application/json; charset=UTF-8",
        "X-DashScope-Async": "enable",
    }

    try:
        url = _build_api_url(request.base_url, VIDEO_SYNTHESIS_PATH)
        async with httpx.AsyncClient(timeout=_http_timeout(HTTP_READ_TIMEOUT_VIDEO_SUBMIT)) as client:
            response = await client.post(
                url,
                json=body,
                headers=headers,
            )

        if response.status_code != 200:
            submission_uncertain = response.status_code >= 500
            prefix = "WANX_SUBMISSION_UNCERTAIN: " if submission_uncertain else ""
            error_msg = prefix + f"DashScope API error: {response.status_code} - {response.text}"
            logger.error(error_msg)
            return SubmitVideoResponse(
                success=False,
                normalized_duration_ms=duration_ms,
                error=error_msg,
                status_code=response.status_code,
                error_code=(
                    "WANX_SUBMISSION_UNCERTAIN"
                    if submission_uncertain else "WANX_SUBMISSION_REJECTED"
                ),
                retryable=_is_retryable_status(response.status_code) and not submission_uncertain,
                submission_uncertain=submission_uncertain,
            )

        data = response.json()
        task_id = data.get("output", {}).get("task_id")

        if not task_id:
            return SubmitVideoResponse(
                success=False,
                normalized_duration_ms=duration_ms,
                error="WANX_SUBMISSION_UNCERTAIN: provider response did not contain task_id",
                status_code=502,
                error_code="WANX_SUBMISSION_UNCERTAIN",
                submission_uncertain=True,
            )

        logger.info("Video task submitted | task_id=%s", task_id)
        return SubmitVideoResponse(
            success=True,
            task_id=task_id,
            normalized_duration_ms=duration_ms,
        )

    except httpx.TimeoutException as e:
        error_msg = f"WANX_SUBMISSION_UNCERTAIN: timeout submitting video task: {e}"
        logger.error(error_msg)
        return SubmitVideoResponse(
            success=False,
            normalized_duration_ms=duration_ms,
            error=error_msg,
            status_code=504,
            error_code="WANX_SUBMISSION_UNCERTAIN",
            submission_uncertain=True,
        )
    except httpx.HTTPError as e:
        error_msg = f"WANX_SUBMISSION_UNCERTAIN: transport error submitting video task: {e}"
        logger.error(error_msg)
        return SubmitVideoResponse(
            success=False,
            normalized_duration_ms=duration_ms,
            error=error_msg,
            status_code=503,
            error_code="WANX_SUBMISSION_UNCERTAIN",
            submission_uncertain=True,
        )
    except ValueError as e:
        error_msg = f"Invalid video provider configuration: {e}"
        logger.error(error_msg)
        return SubmitVideoResponse(
            success=False,
            normalized_duration_ms=duration_ms,
            error=error_msg,
            status_code=400,
            error_code="WANX_PROVIDER_CONFIG_ERROR",
        )
    except Exception as e:
        error_msg = f"WANX_SUBMISSION_UNCERTAIN: unexpected submission error: {e}"
        logger.error(error_msg)
        return SubmitVideoResponse(
            success=False,
            normalized_duration_ms=duration_ms,
            error=error_msg,
            status_code=500,
            error_code="WANX_SUBMISSION_UNCERTAIN",
            submission_uncertain=True,
        )


# ── Video Query Endpoint ─────────────────────────────────────────────────────

@router.post("/query-video", response_model=QueryVideoResponse)
async def query_video(request: QueryVideoRequest) -> QueryVideoResponse:
    """Query the status of a video generation task.

    Args:
        request: Query request with API key and task ID.

    Returns:
        Task status and video URL if completed.
    """
    logger.info("Querying video task | task_id=%s", request.task_id)

    headers = {
        "Authorization": f"Bearer {request.api_key}",
    }

    try:
        url = _build_task_query_url(request.base_url, request.task_id)
        async with httpx.AsyncClient(timeout=_http_timeout(HTTP_READ_TIMEOUT_VIDEO_QUERY)) as client:
            response = await client.get(url, headers=headers)

        if response.status_code != 200:
            error_msg = f"DashScope API error: {response.status_code} - {response.text}"
            logger.error(error_msg)
            retryable = _is_retryable_status(response.status_code)
            return QueryVideoResponse(
                success=False,
                error=error_msg,
                status_code=response.status_code,
                error_code=(
                    "WANX_QUERY_RETRYABLE_HTTP_ERROR"
                    if retryable else "WANX_QUERY_HTTP_ERROR"
                ),
                retryable=retryable,
            )

        data = response.json()
        output = data.get("output", {})
        status_str = output.get("task_status", "UNKNOWN")
        video_url = output.get("video_url")
        error_msg = output.get("message", "")

        # Fallback: check results array if video_url is empty
        if not video_url and output.get("results"):
            video_url = output["results"][0].get("url")

        # Map status string to enum
        try:
            status = TaskStatus(status_str)
        except ValueError:
            status = TaskStatus.RUNNING
            logger.warning("Unknown task status: %s", status_str)

        logger.info(
            "Video task queried | task_id=%s | status=%s | has_video=%s",
            request.task_id,
            status.value,
            bool(video_url),
        )

        return QueryVideoResponse(
            success=True,
            status=status,
            video_url=video_url,
            error=error_msg if status == TaskStatus.FAILED else None,
        )

    except httpx.TimeoutException as e:
        error_msg = f"Timeout querying video task: {e}"
        logger.error(error_msg)
        return QueryVideoResponse(
            success=False,
            error=error_msg,
            status_code=504,
            error_code="WANX_QUERY_TIMEOUT",
            retryable=True,
        )
    except httpx.HTTPError as e:
        error_msg = f"HTTP error querying video task: {e}"
        logger.error(error_msg)
        return QueryVideoResponse(
            success=False,
            error=error_msg,
            status_code=503,
            error_code="WANX_QUERY_TRANSPORT_ERROR",
            retryable=True,
        )
    except ValueError as e:
        error_msg = f"Invalid video provider configuration: {e}"
        logger.error(error_msg)
        return QueryVideoResponse(
            success=False,
            error=error_msg,
            status_code=400,
            error_code="WANX_PROVIDER_CONFIG_ERROR",
        )
    except Exception as e:
        error_msg = f"Unexpected error querying video task: {e}"
        logger.error(error_msg)
        return QueryVideoResponse(
            success=False,
            error=error_msg,
            status_code=500,
            error_code="WANX_QUERY_INTERNAL_ERROR",
            retryable=True,
        )
