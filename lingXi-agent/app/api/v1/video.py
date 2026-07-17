"""
Video generation API endpoints.

Provides text-to-image and image-to-video generation via DashScope.
Migrated from Java aiVedio module to centralize DashScope API calls in Python.
"""

from __future__ import annotations

import json
from typing import Any

import httpx
from fastapi import APIRouter, HTTPException

from app.schemas.video import (
    GenerateImageRequest,
    GenerateImageResponse,
    QueryVideoRequest,
    QueryVideoResponse,
    SubmitVideoRequest,
    SubmitVideoResponse,
    TaskStatus,
)
from app.utils.logger import logger

router = APIRouter(prefix="/api/v1/video", tags=["video"])


# ── Constants ────────────────────────────────────────────────────────────────

# Image generation endpoint
IMAGE_GENERATION_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation"

# Video generation endpoints
VIDEO_SYNTHESIS_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/video-generation/video-synthesis"
TASK_QUERY_URL_PREFIX = "https://dashscope.aliyuncs.com/api/v1/tasks/"

# Model-specific prompt limits
IMAGE_PROMPT_LIMIT = 2000
VIDEO_PROMPT_LIMITS = {
    "2.5": 1500,
    "2.6": 1500,
}
VIDEO_NEGATIVE_PROMPT_LIMIT = 500

# Model-specific duration ranges (in seconds)
DURATION_RANGES = {
    "2.1-turbo": (3, 5),
    "2.1": (5, 5),
    "2.2": (5, 5),
    "2.5": (5, 10),
    "2.6": (2, 10),
}

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


def _get_model_version(model: str) -> str:
    """Extract model version for duration/limit lookup."""
    model_lower = model.lower()
    if "2.6" in model_lower:
        return "2.6"
    if "2.5" in model_lower:
        return "2.5"
    if "2.1" in model_lower:
        if "turbo" in model_lower:
            return "2.1-turbo"
        return "2.1"
    if "2.2" in model_lower:
        return "2.2"
    return "unknown"


def _normalize_duration_ms(duration_ms: int, model: str) -> int:
    """Normalize duration based on model capabilities.

    Args:
        duration_ms: Requested duration in milliseconds.
        model: Model name string.

    Returns:
        Normalized duration in milliseconds.
    """
    version = _get_model_version(model)
    duration_s = duration_ms / 1000

    if version in DURATION_RANGES:
        min_s, max_s = DURATION_RANGES[version]
        if version == "2.1-turbo":
            duration_s = max(min_s, min(max_s, duration_s))
        elif version in ("2.1", "2.2"):
            duration_s = min_s  # Fixed duration
        else:
            # 2.5, 2.6: clamp to range
            duration_s = max(min_s, min(max_s, duration_s))

    # Global cap at 10 seconds
    duration_s = min(10, duration_s)
    return int(duration_s * 1000)


def _get_prompt_limit(model: str) -> int:
    """Get prompt character limit for model."""
    version = _get_model_version(model)
    return VIDEO_PROMPT_LIMITS.get(version, 800)


def _should_include_duration(model: str) -> bool:
    """Check if duration parameter should be included in request body."""
    version = _get_model_version(model)
    return version in ("2.1-turbo", "2.5", "2.6")


def _truncate(text: str, limit: int) -> str:
    """Truncate text to character limit."""
    if len(text) <= limit:
        return text
    return text[:limit]


def _is_retryable_status(status_code: int) -> bool:
    """Check if HTTP status code indicates a retryable error."""
    return status_code in (408, 429) or status_code >= 500


# ── Image Generation Endpoint ────────────────────────────────────────────────

@router.post("/generate-image", response_model=GenerateImageResponse)
async def generate_image(request: GenerateImageRequest) -> GenerateImageResponse:
    """Generate an image from text prompt using DashScope QwenImage.

    This is a synchronous endpoint that waits for the image to be generated.
    """
    logger.info(
        "Generating image | model=%s | aspect_ratio=%s | prompt_len=%d | refs=%d",
        request.model,
        request.aspect_ratio.value,
        len(request.prompt),
        len(request.reference_image_urls or []),
    )

    # Build content array (reference images first, then text)
    content: list[dict[str, str]] = []
    if request.reference_image_urls:
        for url in request.reference_image_urls[:3]:  # Max 3 references
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
            "size": _aspect_ratio_to_size(request.aspect_ratio.value),
        },
    }

    headers = {
        "Authorization": f"Bearer {request.api_key}",
        "Content-Type": "application/json; charset=UTF-8",
    }

    try:
        async with httpx.AsyncClient(timeout=HTTP_READ_TIMEOUT_IMAGE) as client:
            response = await client.post(
                IMAGE_GENERATION_URL,
                json=body,
                headers=headers,
            )

        if response.status_code != 200:
            error_msg = f"DashScope API error: {response.status_code} - {response.text}"
            logger.error(error_msg)
            return GenerateImageResponse(
                success=False,
                error=error_msg,
                status_code=response.status_code,
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
                status_code=200,
            )

        logger.info("Image generated successfully | url=%s", image_url[:100])
        return GenerateImageResponse(success=True, image_url=image_url)

    except httpx.TimeoutException as e:
        error_msg = f"Timeout waiting for image generation: {e}"
        logger.error(error_msg)
        return GenerateImageResponse(success=False, error=error_msg)
    except httpx.HTTPError as e:
        error_msg = f"HTTP error during image generation: {e}"
        logger.error(error_msg)
        return GenerateImageResponse(success=False, error=error_msg)
    except Exception as e:
        error_msg = f"Unexpected error during image generation: {e}"
        logger.error(error_msg)
        return GenerateImageResponse(success=False, error=error_msg)


# ── Video Submission Endpoint ────────────────────────────────────────────────

@router.post("/submit-video", response_model=SubmitVideoResponse)
async def submit_video(request: SubmitVideoRequest) -> SubmitVideoResponse:
    """Submit an image-to-video generation task to DashScope WanxVideo.

    This is an async endpoint that returns a task ID for polling.
    """
    logger.info(
        "Submitting video task | model=%s | duration_ms=%d | prompt_len=%d",
        request.model,
        request.duration_ms or 4000,
        len(request.prompt),
    )

    # Normalize duration
    duration_ms = _normalize_duration_ms(request.duration_ms or 4000, request.model)

    # Truncate prompts to model limits
    prompt_limit = _get_prompt_limit(request.model)
    prompt = _truncate(request.prompt, prompt_limit)
    negative_prompt = _truncate(request.negative_prompt or "", VIDEO_NEGATIVE_PROMPT_LIMIT)

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
        async with httpx.AsyncClient(timeout=HTTP_READ_TIMEOUT_VIDEO_SUBMIT) as client:
            response = await client.post(
                VIDEO_SYNTHESIS_URL,
                json=body,
                headers=headers,
            )

        if response.status_code != 200:
            error_msg = f"DashScope API error: {response.status_code} - {response.text}"
            logger.error(error_msg)
            return SubmitVideoResponse(
                success=False,
                error=error_msg,
                status_code=response.status_code,
            )

        data = response.json()
        task_id = data.get("output", {}).get("task_id")

        if not task_id:
            return SubmitVideoResponse(
                success=False,
                error="No task_id in response",
                status_code=200,
            )

        logger.info("Video task submitted | task_id=%s", task_id)
        return SubmitVideoResponse(success=True, task_id=task_id)

    except httpx.TimeoutException as e:
        error_msg = f"Timeout submitting video task: {e}"
        logger.error(error_msg)
        return SubmitVideoResponse(success=False, error=error_msg)
    except httpx.HTTPError as e:
        error_msg = f"HTTP error submitting video task: {e}"
        logger.error(error_msg)
        return SubmitVideoResponse(success=False, error=error_msg)
    except Exception as e:
        error_msg = f"Unexpected error submitting video task: {e}"
        logger.error(error_msg)
        return SubmitVideoResponse(success=False, error=error_msg)


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

    url = f"{TASK_QUERY_URL_PREFIX}{request.task_id}"
    headers = {
        "Authorization": f"Bearer {request.api_key}",
    }

    try:
        async with httpx.AsyncClient(timeout=HTTP_READ_TIMEOUT_VIDEO_QUERY) as client:
            response = await client.get(url, headers=headers)

        if response.status_code != 200:
            error_msg = f"DashScope API error: {response.status_code} - {response.text}"
            logger.error(error_msg)
            return QueryVideoResponse(
                success=False,
                error=error_msg,
                status_code=response.status_code,
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
        return QueryVideoResponse(success=False, error=error_msg)
    except httpx.HTTPError as e:
        error_msg = f"HTTP error querying video task: {e}"
        logger.error(error_msg)
        return QueryVideoResponse(success=False, error=error_msg)
    except Exception as e:
        error_msg = f"Unexpected error querying video task: {e}"
        logger.error(error_msg)
        return QueryVideoResponse(success=False, error=error_msg)
