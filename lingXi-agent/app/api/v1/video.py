"""
视频生成API端点模块。

提供通过DashScope进行文本到图像和图像到视频的生成服务。
从Java aiVedio模块迁移而来，用于在Python中集中管理DashScope API调用。
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


# ── 常量定义 ────────────────────────────────────────────────────────────────

# DashScope 原生接口路径。域名和可选网关前缀取自 request.base_url，
# 因而 Java 可以切换模型提供方地址，而无需修改本服务。
IMAGE_GENERATION_PATH = "/services/aigc/multimodal-generation/generation"
VIDEO_SYNTHESIS_PATH = "/services/aigc/video-generation/video-synthesis"
TASK_QUERY_PATH = "/tasks"
MAX_REFERENCE_IMAGE_COUNT = 5
CHARACTER_REFERENCE_ASSET_TYPE = "CHARACTER_REFERENCE"

# 不同模型的提示词长度限制
IMAGE_PROMPT_LIMIT = 2000
VIDEO_NEGATIVE_PROMPT_LIMIT = 500
ProviderResponseModel = TypeVar("ProviderResponseModel", bound=BaseModel)


class _ProviderResponseError(ValueError):
    """当提供商返回2xx响应但不符合传输契约时抛出的异常。"""

# 超时配置（秒）
HTTP_CONNECT_TIMEOUT = 10
HTTP_READ_TIMEOUT_IMAGE = 180  # 3 minutes for image generation
HTTP_READ_TIMEOUT_VIDEO_SUBMIT = 30
HTTP_READ_TIMEOUT_VIDEO_QUERY = 30


# ── 辅助函数 ────────────────────────────────────────────────────────────────

def _aspect_ratio_to_size(aspect_ratio: str) -> str:
    """将宽高比字符串转换为DashScope所需的像素尺寸。"""
    mapping = {
        "9:16": "720*1280",
        "1:1": "1024*1024",
        "16:9": "1280*720",
    }
    return mapping.get(aspect_ratio, "1280*720")


def _resolve_image_aspect_ratio(request: GenerateImageRequest) -> ImageAspectRatio:
    """在Python AI层中应用特定于资源类型的图像布局规则。"""

    if request.asset_type == CHARACTER_REFERENCE_ASSET_TYPE:
        return ImageAspectRatio.LANDSCAPE_16_9
    return request.aspect_ratio or ImageAspectRatio.LANDSCAPE_16_9


def _is_retryable_status(status_code: int) -> bool:
    """检查HTTP状态码是否表示可重试的错误。"""
    return status_code in (408, 429) or status_code >= 500


def _normalize_api_base_url(base_url: str) -> str:
    """将配置的提供商URL规范化为原生DashScope API基础URL。

    旧版Java配置通常提供DashScope的OpenAI兼容模式``/compatible-mode/v1``基础地址。
    图像和视频API位于原生``/api/v1``树下，因此该已知后缀会被确定性地转换。
    自定义网关前缀在其他情况下会被保留。
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
    video_endpoint_suffix = "/api/v1" + VIDEO_SYNTHESIS_PATH
    if path.endswith(video_endpoint_suffix):
        path = path[: -len(VIDEO_SYNTHESIS_PATH)]
    elif path.endswith(compatible_suffix):
        path = path[: -len(compatible_suffix)] + "/api/v1"
    elif not path:
        path = "/api/v1"

    return urlunsplit((parsed.scheme.lower(), parsed.netloc, path, "", ""))


def _build_api_url(base_url: str, endpoint_path: str) -> str:
    """将规范化的API基础地址与原生端点路径拼接。"""
    normalized_base = _normalize_api_base_url(base_url)
    normalized_path = "/" + endpoint_path.lstrip("/")
    return normalized_base + normalized_path


def _build_task_query_url(base_url: str, task_id: str) -> str:
    """构建任务查询URL，防止任务ID篡改URL路径。"""
    encoded_task_id = quote(task_id.strip(), safe="")
    if not encoded_task_id:
        raise ValueError("task_id must not be blank")
    return _build_api_url(base_url, f"{TASK_QUERY_PATH}/{encoded_task_id}")


def _http_timeout(read_timeout: float) -> httpx.Timeout:
    """使用专用的连接超时，而不是将读取超时应用于所有阶段。"""
    return httpx.Timeout(
        connect=float(HTTP_CONNECT_TIMEOUT),
        read=float(read_timeout),
        write=30.0,
        pool=10.0,
    )


def _validated_provider_url(base_url: str, endpoint_path: str) -> str:
    """构建并进行失败关闭验证的原生提供商端点。"""

    return validate_outbound_http_url(_build_api_url(base_url, endpoint_path))


def _validated_task_query_url(base_url: str, task_id: str) -> str:
    return validate_outbound_http_url(_build_task_query_url(base_url, task_id))


def _provider_http_status(status_code: int) -> int:
    """为非成功提供商响应返回有意义的网关状态码。"""

    return status_code if 400 <= status_code <= 599 else 502


def _parse_provider_response(
    response: httpx.Response,
    response_model: type[ProviderResponseModel],
) -> ProviderResponseModel:
    """解码并验证成功的提供商响应，不泄露其内容体。"""

    try:
        payload = response.json()
    except (TypeError, ValueError) as exc:
        raise _ProviderResponseError("provider returned invalid JSON") from exc
    try:
        return response_model.model_validate(payload)
    except ValidationError as exc:
        raise _ProviderResponseError("provider response failed validation") from exc


# ── 图片生成接口 ────────────────────────────────────────────────────────────

@router.post("/generate-image", response_model=GenerateImageResponse)
async def generate_image(
    request: GenerateImageRequest,
    response: Response,
    client: Annotated[httpx.AsyncClient, Depends(get_http_client)],
) -> GenerateImageResponse:
    """使用DashScope QwenImage从文本提示生成图像。

    这是一个同步端点，等待图像生成完成。
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

    # 组装多模态内容：参考图在前，文本提示词在后。
    content: list[dict[str, str]] = []
    if reference_image_urls:
        # 保留所有已校验参考图及其顺序。Java 最多先传四张角色参考图，
        # 再传场景参考图；场景图必须保持在最后，因为它决定输出宽高比。
        for url in reference_image_urls:
            content.append({"image": url})
    content.append({"text": request.prompt})

    # 按提供方协议组装请求体。
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


# ── 视频任务提交接口 ────────────────────────────────────────────────────────

@router.post("/submit-video", response_model=SubmitVideoResponse)
async def submit_video(
    request: SubmitVideoRequest,
    response: Response,
    client: Annotated[httpx.AsyncClient, Depends(get_http_client)],
) -> SubmitVideoResponse:
    """通过活动的提供商适配器提交图像到视频生成任务。

    这是一个异步端点，返回任务ID用于轮询。
    """
    logger.info(
        "Submitting video task | provider=%s | model_length=%d | duration_ms=%d | prompt_len=%d | character_refs=%d | scene_ref=%s",
        request.provider,
        len(request.model),
        request.duration_ms,
        len(request.prompt),
        len(request.character_reference_image_urls),
        bool(request.scene_reference_image_url),
    )

    # 将用户时长转换为当前模型支持的合法档位。
    duration_ms = _normalize_duration_ms(request.duration_ms, request.model)

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
    is_happyhorse = request.provider.lower() == "happyhorse" or "happyhorse" in request.model.lower()
    prompt = request.prompt
    input_data: dict[str, Any]
    parameters: dict[str, Any]
    if is_happyhorse:
        media_urls = [request.image_url, *request.character_reference_image_urls]
        if request.scene_reference_image_url:
            media_urls.append(request.scene_reference_image_url)
        if not 1 <= len(media_urls) <= 9:
            response.status_code = 400
            return SubmitVideoResponse(
                success=False,
                normalized_duration_ms=duration_ms,
                error="HappyHorse requires between 1 and 9 reference images",
                status_code=400,
                error_code="VIDEO_REFERENCE_COUNT_INVALID",
            )
        reference_instructions = [
            "[Image 1]是当前分镜关键帧，用于约束主要构图、人物站位和镜头起始状态。"
        ]
        for index in range(len(request.character_reference_image_urls)):
            reference_instructions.append(
                f"[Image {index + 2}]是当前分镜人物三视图，用于保持人物身份、服装和体态一致。"
            )
        if request.scene_reference_image_url:
            reference_instructions.append(
                f"[Image {len(media_urls)}]是场景参考图，用于保持环境、光线和美术风格一致。"
            )
        prompt = "\n".join(reference_instructions + [request.prompt])
        if negative_prompt:
            prompt += f"\n生成时避免出现：{negative_prompt}"
        input_data = {
            "prompt": prompt,
            "media": [
                {"type": "reference_image", "url": url} for url in media_urls
            ],
        }
        parameters = {
            "resolution": request.resolution,
            "ratio": request.ratio,
            "duration": duration_ms // 1000,
            "watermark": request.watermark,
        }
    else:
        input_data = {"prompt": prompt, "img_url": request.image_url}
        if negative_prompt:
            input_data["negative_prompt"] = negative_prompt
        parameters = {
            "resolution": request.resolution,
            "prompt_extend": request.prompt_extend,
        }
        if _should_include_duration(request.model):
            parameters["duration"] = duration_ms // 1000

    # 用户审核确认后的内容不得静默截断，超限时应明确返回错误。
    prompt_limit = _get_prompt_limit(request.model)
    if len(prompt) > prompt_limit:
        response.status_code = 400
        return SubmitVideoResponse(
            success=False,
            normalized_duration_ms=duration_ms,
            error=f"Video prompt exceeds the provider limit of {prompt_limit} characters",
            status_code=400,
            error_code="VIDEO_PROMPT_TOO_LONG",
        )

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
                    "VIDEO_PROVIDER_SUBMISSION_UNCERTAIN"
                    if submission_uncertain else "VIDEO_PROVIDER_SUBMISSION_REJECTED"
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
                error_code="VIDEO_PROVIDER_SUBMISSION_UNCERTAIN",
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
            error_code="VIDEO_PROVIDER_SUBMISSION_UNCERTAIN",
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
            error_code="VIDEO_PROVIDER_SUBMISSION_UNCERTAIN",
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
            error_code="VIDEO_PROVIDER_CONFIG_ERROR",
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
            error_code="VIDEO_PROVIDER_SUBMISSION_UNCERTAIN",
            submission_uncertain=True,
        )


# ── 视频任务查询接口 ────────────────────────────────────────────────────────

@router.post("/query-video", response_model=QueryVideoResponse)
async def query_video(
    request: QueryVideoRequest,
    response: Response,
    client: Annotated[httpx.AsyncClient, Depends(get_http_client)],
) -> QueryVideoResponse:
    """查询视频生成任务的状态。

    Args:
        request: 包含API密钥和任务ID的查询请求。

    Returns:
        任务状态和完成后的视频URL。
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
                    "VIDEO_PROVIDER_QUERY_RETRYABLE_HTTP_ERROR"
                    if retryable else "VIDEO_PROVIDER_QUERY_HTTP_ERROR"
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
                error_code="VIDEO_PROVIDER_QUERY_INVALID_RESPONSE",
                retryable=True,
            )

        output = provider_result.output
        status_str = output.task_status
        video_url = output.resolved_video_url

        # 将提供方状态字符串映射为稳定的内部枚举。
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
            error_code="VIDEO_PROVIDER_QUERY_TIMEOUT",
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
            error_code="VIDEO_PROVIDER_QUERY_TRANSPORT_ERROR",
            retryable=True,
        )
    except (InputValidationError, ValueError):
        logger.error("Video query provider configuration was rejected")
        response.status_code = 400
        return QueryVideoResponse(
            success=False,
            error="Invalid video provider configuration",
            status_code=400,
            error_code="VIDEO_PROVIDER_CONFIG_ERROR",
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
            error_code="VIDEO_PROVIDER_QUERY_INTERNAL_ERROR",
            retryable=True,
        )
