"""
Video generation API endpoints.

Provides text-to-image and image-to-video generation via DashScope.
Migrated from Java aiVedio module to centralize DashScope API calls in Python.
"""

from __future__ import annotations

from typing import Annotated, Any, TypeVar
from urllib.parse import quote, urlsplit, urlunsplit

import httpx
from fastapi import APIRouter, Depends, Response
from pydantic import BaseModel, ValidationError

from app.security.outbound import validate_outbound_http_url
from app.schemas.video import (
    GenerateImageRequest,
    GenerateImageProviderResponse,
    GenerateImageResponse,
    ImageAspectRatio,
    QueryVideoProviderResponse,
    QueryVideoRequest,
    QueryVideoResponse,
    SubmitVideoProviderResponse,
    SubmitVideoRequest,
    SubmitVideoResponse,
    TaskStatus,
)
from app.services.http_client import get_http_client
from app.services.video_capabilities import (
    get_video_prompt_limit as _get_prompt_limit,
    normalize_video_duration_ms as _normalize_duration_ms,
    should_include_duration as _should_include_duration,
)
from app.utils.exceptions import InputValidationError
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
ProviderResponseModel = TypeVar("ProviderResponseModel", bound=BaseModel)


class _ProviderResponseError(ValueError):
    """A 2xx provider response did not match the transport contract."""

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
    return httpx.Timeout(
        connect=float(HTTP_CONNECT_TIMEOUT),
        read=float(read_timeout),
        write=30.0,
        pool=10.0,
    )


def _validated_provider_url(base_url: str, endpoint_path: str) -> str:
    """Build and fail-closed validate one native provider endpoint."""

    return validate_outbound_http_url(_build_api_url(base_url, endpoint_path))


def _validated_task_query_url(base_url: str, task_id: str) -> str:
    return validate_outbound_http_url(_build_task_query_url(base_url, task_id))


def _provider_http_status(status_code: int) -> int:
    """Return a meaningful gateway status for a non-success provider response."""

    return status_code if 400 <= status_code <= 599 else 502


def _parse_provider_response(
    response: httpx.Response,
    response_model: type[ProviderResponseModel],
) -> ProviderResponseModel:
    """Decode and validate a successful provider response without leaking its body."""

    try:
        payload = response.json()
    except (TypeError, ValueError) as exc:
        raise _ProviderResponseError("provider returned invalid JSON") from exc
    try:
        return response_model.model_validate(payload)
    except ValidationError as exc:
        raise _ProviderResponseError("provider response failed validation") from exc


# ── Image Generation Endpoint ────────────────────────────────────────────────

@router.post("/generate-image", response_model=GenerateImageResponse)
async def generate_image(
    request: GenerateImageRequest,
    response: Response,
    client: Annotated[httpx.AsyncClient, Depends(get_http_client)],
) -> GenerateImageResponse:
    """Generate an image from text prompt using DashScope QwenImage.

    This is a synchronous endpoint that waits for the image to be generated.
    """
    aspect_ratio = _resolve_image_aspect_ratio(request)
    logger.info(
        "Generating image | model_length=%d | aspect_ratio=%s | prompt_len=%d | refs=%d",
        len(request.model),
        aspect_ratio.value,
        len(request.prompt),
        len(request.reference_image_urls or []),
    )

    if len(request.prompt) > IMAGE_PROMPT_LIMIT:
        response.status_code = 400
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
        response.status_code = 400
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
        url = _validated_provider_url(request.base_url, IMAGE_GENERATION_PATH)
        provider_response = await client.post(
            url,
            json=body,
            headers=headers,
            timeout=_http_timeout(HTTP_READ_TIMEOUT_IMAGE),
        )

        if not 200 <= provider_response.status_code < 300:
            provider_status = _provider_http_status(provider_response.status_code)
            logger.error(
                "Image provider HTTP failure | status=%d",
                provider_response.status_code,
            )
            retryable = _is_retryable_status(provider_response.status_code)
            response.status_code = provider_status
            return GenerateImageResponse(
                success=False,
                error="Image provider request failed",
                status_code=provider_response.status_code,
                error_code=(
                    "IMAGE_PROVIDER_RETRYABLE_HTTP_ERROR"
                    if retryable else "IMAGE_PROVIDER_HTTP_ERROR"
                ),
                retryable=retryable,
            )

        try:
            provider_result = _parse_provider_response(
                provider_response,
                GenerateImageProviderResponse,
            )
        except _ProviderResponseError:
            logger.error("Image provider returned an invalid success response")
            response.status_code = 502
            return GenerateImageResponse(
                success=False,
                error="Image provider returned an invalid response",
                status_code=502,
                error_code="IMAGE_PROVIDER_INVALID_RESPONSE",
            )

        logger.info("Image generated successfully")
        response.status_code = 200
        return GenerateImageResponse(success=True, image_url=provider_result.image_url)

    except httpx.TimeoutException:
        logger.error("Image provider request timed out")
        response.status_code = 504
        return GenerateImageResponse(
            success=False,
            error="Image provider request timed out",
            status_code=504,
            error_code="IMAGE_PROVIDER_TIMEOUT",
            retryable=True,
        )
    except httpx.HTTPError as exc:
        logger.error(
            "Image provider transport failed | error_type=%s",
            type(exc).__name__,
        )
        response.status_code = 503
        return GenerateImageResponse(
            success=False,
            error="Image provider is temporarily unavailable",
            status_code=503,
            error_code="IMAGE_PROVIDER_TRANSPORT_ERROR",
            retryable=True,
        )
    except (InputValidationError, ValueError):
        logger.error("Image provider configuration was rejected")
        response.status_code = 400
        return GenerateImageResponse(
            success=False,
            error="Invalid image provider configuration",
            status_code=400,
            error_code="IMAGE_PROVIDER_CONFIG_ERROR",
        )
    except Exception as exc:
        logger.error(
            "Unexpected image generation failure | error_type=%s",
            type(exc).__name__,
        )
        response.status_code = 500
        return GenerateImageResponse(
            success=False,
            error="Image generation failed unexpectedly",
            status_code=500,
            error_code="IMAGE_GENERATION_INTERNAL_ERROR",
        )


# ── Video Submission Endpoint ────────────────────────────────────────────────

@router.post("/submit-video", response_model=SubmitVideoResponse)
async def submit_video(
    request: SubmitVideoRequest,
    response: Response,
    client: Annotated[httpx.AsyncClient, Depends(get_http_client)],
) -> SubmitVideoResponse:
    """Submit an image-to-video generation task to DashScope WanxVideo.

    This is an async endpoint that returns a task ID for polling.
    """
    logger.info(
        "Submitting video task | model_length=%d | duration_ms=%d | prompt_len=%d",
        len(request.model),
        request.duration_ms,
        len(request.prompt),
    )

    # Normalize duration
    duration_ms = _normalize_duration_ms(request.duration_ms, request.model)

    # Reject edited prompts that exceed the selected model's limits. Never
    # silently mutate content after the user has reviewed and confirmed it.
    prompt_limit = _get_prompt_limit(request.model)
    if len(request.prompt) > prompt_limit:
        response.status_code = 400
        return SubmitVideoResponse(
            success=False,
            normalized_duration_ms=duration_ms,
            error=f"Video prompt exceeds the provider limit of {prompt_limit} characters",
            status_code=400,
            error_code="VIDEO_PROMPT_TOO_LONG",
        )
    negative_prompt = request.negative_prompt or ""
    if len(negative_prompt) > VIDEO_NEGATIVE_PROMPT_LIMIT:
        response.status_code = 400
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
    if request.idempotency_key:
        headers["Idempotency-Key"] = request.idempotency_key

    try:
        url = _validated_provider_url(request.base_url, VIDEO_SYNTHESIS_PATH)
        provider_response = await client.post(
            url,
            json=body,
            headers=headers,
            timeout=_http_timeout(HTTP_READ_TIMEOUT_VIDEO_SUBMIT),
        )

        if not 200 <= provider_response.status_code < 300:
            submission_uncertain = (
                provider_response.status_code == 408
                or provider_response.status_code >= 500
            )
            logger.error(
                "Video provider HTTP failure | status=%d | uncertain=%s",
                provider_response.status_code,
                submission_uncertain,
            )
            response.status_code = (
                202
                if submission_uncertain
                else _provider_http_status(provider_response.status_code)
            )
            return SubmitVideoResponse(
                success=False,
                normalized_duration_ms=duration_ms,
                error=(
                    "Video submission result is uncertain; do not resubmit automatically"
                    if submission_uncertain
                    else "Video provider rejected the submission"
                ),
                status_code=provider_response.status_code,
                error_code=(
                    "WANX_SUBMISSION_UNCERTAIN"
                    if submission_uncertain else "WANX_SUBMISSION_REJECTED"
                ),
                retryable=(
                    _is_retryable_status(provider_response.status_code)
                    and not submission_uncertain
                ),
                submission_uncertain=submission_uncertain,
            )

        try:
            provider_result = _parse_provider_response(
                provider_response,
                SubmitVideoProviderResponse,
            )
        except _ProviderResponseError:
            logger.error("Video provider returned an invalid success response")
            response.status_code = 202
            return SubmitVideoResponse(
                success=False,
                normalized_duration_ms=duration_ms,
                error="Video submission result is uncertain; do not resubmit automatically",
                status_code=502,
                error_code="WANX_SUBMISSION_UNCERTAIN",
                submission_uncertain=True,
            )

        task_id = provider_result.output.task_id
        logger.info("Video task submitted | task_id_length=%d", len(task_id))
        response.status_code = 200
        return SubmitVideoResponse(
            success=True,
            task_id=task_id,
            normalized_duration_ms=duration_ms,
        )

    except httpx.TimeoutException:
        logger.error("Video submission timed out; provider acceptance is uncertain")
        response.status_code = 202
        return SubmitVideoResponse(
            success=False,
            normalized_duration_ms=duration_ms,
            error="Video submission result is uncertain; do not resubmit automatically",
            status_code=504,
            error_code="WANX_SUBMISSION_UNCERTAIN",
            submission_uncertain=True,
        )
    except httpx.HTTPError as exc:
        logger.error(
            "Video submission transport failed; provider acceptance is uncertain | error_type=%s",
            type(exc).__name__,
        )
        response.status_code = 202
        return SubmitVideoResponse(
            success=False,
            normalized_duration_ms=duration_ms,
            error="Video submission result is uncertain; do not resubmit automatically",
            status_code=503,
            error_code="WANX_SUBMISSION_UNCERTAIN",
            submission_uncertain=True,
        )
    except (InputValidationError, ValueError):
        logger.error("Video provider configuration was rejected")
        response.status_code = 400
        return SubmitVideoResponse(
            success=False,
            normalized_duration_ms=duration_ms,
            error="Invalid video provider configuration",
            status_code=400,
            error_code="WANX_PROVIDER_CONFIG_ERROR",
        )
    except Exception as exc:
        logger.error(
            "Unexpected video submission failure; provider acceptance is uncertain | error_type=%s",
            type(exc).__name__,
        )
        response.status_code = 202
        return SubmitVideoResponse(
            success=False,
            normalized_duration_ms=duration_ms,
            error="Video submission result is uncertain; do not resubmit automatically",
            status_code=500,
            error_code="WANX_SUBMISSION_UNCERTAIN",
            submission_uncertain=True,
        )


# ── Video Query Endpoint ─────────────────────────────────────────────────────

@router.post("/query-video", response_model=QueryVideoResponse)
async def query_video(
    request: QueryVideoRequest,
    response: Response,
    client: Annotated[httpx.AsyncClient, Depends(get_http_client)],
) -> QueryVideoResponse:
    """Query the status of a video generation task.

    Args:
        request: Query request with API key and task ID.

    Returns:
        Task status and video URL if completed.
    """
    logger.info("Querying video task | task_id_length=%d", len(request.task_id))

    headers = {
        "Authorization": f"Bearer {request.api_key}",
    }

    try:
        url = _validated_task_query_url(request.base_url, request.task_id)
        provider_response = await client.get(
            url,
            headers=headers,
            timeout=_http_timeout(HTTP_READ_TIMEOUT_VIDEO_QUERY),
        )

        if not 200 <= provider_response.status_code < 300:
            logger.error(
                "Video query provider HTTP failure | status=%d",
                provider_response.status_code,
            )
            retryable = _is_retryable_status(provider_response.status_code)
            response.status_code = _provider_http_status(provider_response.status_code)
            return QueryVideoResponse(
                success=False,
                error="Video provider query failed",
                status_code=provider_response.status_code,
                error_code=(
                    "WANX_QUERY_RETRYABLE_HTTP_ERROR"
                    if retryable else "WANX_QUERY_HTTP_ERROR"
                ),
                retryable=retryable,
            )

        try:
            provider_result = _parse_provider_response(
                provider_response,
                QueryVideoProviderResponse,
            )
        except _ProviderResponseError:
            logger.error("Video query provider returned an invalid success response")
            response.status_code = 502
            return QueryVideoResponse(
                success=False,
                error="Video provider returned an invalid query response",
                status_code=502,
                error_code="WANX_QUERY_INVALID_RESPONSE",
                retryable=True,
            )

        output = provider_result.output
        status_str = output.task_status
        video_url = output.resolved_video_url

        # Map status string to enum
        try:
            status = TaskStatus(status_str)
        except ValueError:
            status = TaskStatus.UNKNOWN
            logger.warning("Unknown task status | status_length=%d", len(status_str))

        logger.info(
            "Video task queried | task_id_length=%d | status=%s | has_video=%s",
            len(request.task_id),
            status.value,
            bool(video_url),
        )

        response.status_code = 200
        return QueryVideoResponse(
            success=True,
            status=status,
            video_url=video_url,
            error=(
                "Video generation task failed"
                if status == TaskStatus.FAILED
                else None
            ),
            provider_status=(status_str if status == TaskStatus.UNKNOWN else None),
        )

    except httpx.TimeoutException:
        logger.error("Video provider query timed out")
        response.status_code = 504
        return QueryVideoResponse(
            success=False,
            error="Video provider query timed out",
            status_code=504,
            error_code="WANX_QUERY_TIMEOUT",
            retryable=True,
        )
    except httpx.HTTPError as exc:
        logger.error(
            "Video provider query transport failed | error_type=%s",
            type(exc).__name__,
        )
        response.status_code = 503
        return QueryVideoResponse(
            success=False,
            error="Video provider query is temporarily unavailable",
            status_code=503,
            error_code="WANX_QUERY_TRANSPORT_ERROR",
            retryable=True,
        )
    except (InputValidationError, ValueError):
        logger.error("Video query provider configuration was rejected")
        response.status_code = 400
        return QueryVideoResponse(
            success=False,
            error="Invalid video provider configuration",
            status_code=400,
            error_code="WANX_PROVIDER_CONFIG_ERROR",
        )
    except Exception as exc:
        logger.error(
            "Unexpected video query failure | error_type=%s",
            type(exc).__name__,
        )
        response.status_code = 500
        return QueryVideoResponse(
            success=False,
            error="Video query failed unexpectedly",
            status_code=500,
            error_code="WANX_QUERY_INTERNAL_ERROR",
            retryable=True,
        )
