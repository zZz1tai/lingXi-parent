"""Offline contract tests for the Python-owned DashScope media gateway."""

from __future__ import annotations

import unittest
from typing import Any

import httpx
from fastapi import Response
from pydantic import ValidationError

from app.api.v1 import video
from app.schemas.video import GenerateImageRequest, SubmitVideoRequest


IMAGE_MODEL = "qwen-image-2.0-pro-2026-06-22"
VIDEO_MODEL = "wanx2.1-i2v-turbo"
PROVIDER_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"


class _FakeResponse:
    def __init__(
        self,
        *,
        status_code: int = 200,
        payload: Any = None,
        text: str = "",
        json_error: Exception | None = None,
    ) -> None:
        self.status_code = status_code
        self.text = text
        self._payload = payload if payload is not None else {
            "output": {
                "choices": [
                    {"message": {"content": [{"image": "https://result.invalid/image.png"}]}}
                ]
            }
        }
        self._json_error = json_error

    def json(self) -> Any:
        if self._json_error is not None:
            raise self._json_error
        return self._payload


class _FakeAsyncClient:
    def __init__(self, response: _FakeResponse | None = None) -> None:
        self.url = ""
        self.json_body: dict = {}
        self.headers: dict = {}
        self.response = response or _FakeResponse()

    async def post(
        self,
        url: str,
        *,
        json: dict,
        headers: dict,
        timeout: httpx.Timeout,
    ) -> _FakeResponse:
        self.url = url
        self.json_body = json
        self.headers = headers
        return self.response

    async def get(
        self,
        url: str,
        *,
        headers: dict,
        timeout: httpx.Timeout,
    ) -> _FakeResponse:
        self.url = url
        self.headers = headers
        return self.response


class _TimeoutAsyncClient:
    async def post(
        self,
        _url: str,
        *,
        json: dict,
        headers: dict,
        timeout: httpx.Timeout,
    ):
        raise httpx.ReadTimeout("provider response timed out")


class MediaContractTests(unittest.IsolatedAsyncioTestCase):
    def test_model_specific_video_duration_normalization_lives_in_python(self) -> None:
        self.assertEqual(3000, video._normalize_duration_ms(1000, "wanx2.1-i2v-turbo"))
        self.assertEqual(5000, video._normalize_duration_ms(10000, "wanx2.1-i2v-turbo"))
        self.assertEqual(5000, video._normalize_duration_ms(9000, "wanx2.2-i2v-plus"))
        self.assertEqual(5000, video._normalize_duration_ms(7000, "wanx2.5-i2v-preview"))
        self.assertEqual(10000, video._normalize_duration_ms(8000, "wanx2.5-i2v-preview"))
        self.assertEqual(2000, video._normalize_duration_ms(1000, "wanx2.6-i2v"))
        self.assertEqual(9000, video._normalize_duration_ms(9000, "wanx2.6-i2v"))

    def test_compatible_llm_base_is_converted_to_native_dashscope_path(self) -> None:
        actual = video._build_api_url(
            "https://dashscope.aliyuncs.com/compatible-mode/v1",
            video.IMAGE_GENERATION_PATH,
        )
        self.assertEqual(
            "https://dashscope.aliyuncs.com/api/v1/services/aigc/"
            "multimodal-generation/generation",
            actual,
        )

    def test_image_schema_accepts_five_references_and_rejects_six(self) -> None:
        five_urls = [f"https://assets.invalid/{index}.png" for index in range(5)]
        request = GenerateImageRequest(
            api_key="offline-key",
            model=IMAGE_MODEL,
            base_url=PROVIDER_BASE_URL,
            prompt="test prompt",
            reference_image_urls=five_urls,
        )
        self.assertEqual(five_urls, request.reference_image_urls)

        with self.assertRaises(ValidationError):
            GenerateImageRequest(
                api_key="offline-key",
                model=IMAGE_MODEL,
                base_url=PROVIDER_BASE_URL,
                prompt="test prompt",
                reference_image_urls=five_urls + ["https://assets.invalid/5.png"],
            )

    def test_video_transport_identifiers_are_bounded(self) -> None:
        with self.assertRaises(ValidationError):
            SubmitVideoRequest(
                api_key="offline-key",
                model=VIDEO_MODEL,
                base_url=PROVIDER_BASE_URL,
                prompt="test prompt",
                image_url="https://assets.invalid/keyframe.png",
                resolution="720P",
                duration_ms=4000,
                idempotency_key="x" * 257,
            )

    async def test_all_five_references_are_sent_in_order_without_truncation(self) -> None:
        references = [
            "https://assets.invalid/character-1.png",
            "https://assets.invalid/character-2.png",
            "https://assets.invalid/character-3.png",
            "https://assets.invalid/character-4.png",
            "https://assets.invalid/scene.png",
        ]
        request = GenerateImageRequest(
            api_key="offline-key",
            model=IMAGE_MODEL,
            base_url=PROVIDER_BASE_URL,
            prompt="four characters in one continuous scene",
            reference_image_urls=references,
        )
        fake_client = _FakeAsyncClient()

        result = await video.generate_image(
            request,
            response=Response(),
            client=fake_client,
        )

        self.assertTrue(result.success)
        content = fake_client.json_body["input"]["messages"][0]["content"]
        sent_references = [item["image"] for item in content if "image" in item]
        self.assertEqual(references, sent_references)
        self.assertEqual(references[-1], sent_references[-1])
        self.assertTrue(fake_client.url.endswith(video.IMAGE_GENERATION_PATH))

    async def test_image_prompt_over_provider_limit_is_rejected_before_http(self) -> None:
        request = GenerateImageRequest(
            api_key="offline-key",
            model=IMAGE_MODEL,
            base_url=PROVIDER_BASE_URL,
            prompt="x" * (video.IMAGE_PROMPT_LIMIT + 1),
        )

        http_response = Response()
        result = await video.generate_image(
            request,
            response=http_response,
            client=_FakeAsyncClient(),
        )

        self.assertFalse(result.success)
        self.assertEqual(400, result.status_code)
        self.assertEqual("IMAGE_PROMPT_TOO_LONG", result.error_code)
        self.assertEqual(400, http_response.status_code)

    async def test_character_reference_layout_rule_lives_in_python(self) -> None:
        request = GenerateImageRequest(
            api_key="offline-key",
            model=IMAGE_MODEL,
            base_url=PROVIDER_BASE_URL,
            asset_type="CHARACTER_REFERENCE",
            prompt="character turnaround",
            aspect_ratio="9:16",
        )
        fake_client = _FakeAsyncClient()

        result = await video.generate_image(
            request,
            response=Response(),
            client=fake_client,
        )

        self.assertTrue(result.success)
        self.assertEqual("1280*720", fake_client.json_body["parameters"]["size"])

    async def test_video_timeout_is_an_uncertain_submission(self) -> None:
        request = SubmitVideoRequest(
            api_key="offline-key",
            model=VIDEO_MODEL,
            base_url=PROVIDER_BASE_URL,
            prompt="slow camera push",
            image_url="https://assets.invalid/keyframe.png",
            resolution="720P",
            duration_ms=4000,
        )

        http_response = Response()
        result = await video.submit_video(
            request,
            response=http_response,
            client=_TimeoutAsyncClient(),
        )

        self.assertFalse(result.success)
        self.assertTrue(result.submission_uncertain)
        self.assertEqual("WANX_SUBMISSION_UNCERTAIN", result.error_code)
        self.assertEqual(504, result.status_code)
        self.assertEqual(202, http_response.status_code)

    async def test_video_prompt_over_model_limit_is_rejected_without_truncation(self) -> None:
        limit = video._get_prompt_limit(VIDEO_MODEL)
        request = SubmitVideoRequest(
            api_key="offline-key",
            model=VIDEO_MODEL,
            base_url=PROVIDER_BASE_URL,
            prompt="x" * (limit + 1),
            image_url="https://assets.invalid/keyframe.png",
            resolution="720P",
            duration_ms=4000,
        )

        http_response = Response()
        result = await video.submit_video(
            request,
            response=http_response,
            client=_FakeAsyncClient(),
        )

        self.assertFalse(result.success)
        self.assertEqual(400, result.status_code)
        self.assertEqual("VIDEO_PROMPT_TOO_LONG", result.error_code)
        self.assertFalse(result.submission_uncertain)
        self.assertEqual(400, http_response.status_code)

    async def test_http_408_submission_is_uncertain_and_not_retryable(self) -> None:
        request = SubmitVideoRequest(
            api_key="offline-key",
            model=VIDEO_MODEL,
            base_url=PROVIDER_BASE_URL,
            prompt="slow camera push",
            image_url="https://assets.invalid/keyframe.png",
            resolution="720P",
            duration_ms=4000,
        )
        client = _FakeAsyncClient(_FakeResponse(status_code=408, text="request timeout"))
        http_response = Response()

        result = await video.submit_video(
            request,
            response=http_response,
            client=client,
        )

        self.assertFalse(result.success)
        self.assertTrue(result.submission_uncertain)
        self.assertFalse(result.retryable)
        self.assertEqual("WANX_SUBMISSION_UNCERTAIN", result.error_code)
        self.assertEqual(202, http_response.status_code)

    async def test_invalid_success_payload_submission_is_uncertain(self) -> None:
        request = SubmitVideoRequest(
            api_key="offline-key",
            model=VIDEO_MODEL,
            base_url=PROVIDER_BASE_URL,
            prompt="slow camera push",
            image_url="https://assets.invalid/keyframe.png",
            resolution="720P",
            duration_ms=4000,
        )
        client = _FakeAsyncClient(
            _FakeResponse(json_error=ValueError("provider returned invalid JSON"))
        )
        http_response = Response()

        result = await video.submit_video(
            request,
            response=http_response,
            client=client,
        )

        self.assertFalse(result.success)
        self.assertTrue(result.submission_uncertain)
        self.assertEqual("WANX_SUBMISSION_UNCERTAIN", result.error_code)
        self.assertEqual(202, http_response.status_code)

    async def test_success_payload_without_task_id_is_uncertain(self) -> None:
        request = SubmitVideoRequest(
            api_key="offline-key",
            model=VIDEO_MODEL,
            base_url=PROVIDER_BASE_URL,
            prompt="slow camera push",
            image_url="https://assets.invalid/keyframe.png",
            resolution="720P",
            duration_ms=4000,
        )
        client = _FakeAsyncClient(_FakeResponse(payload={"output": {}}))
        http_response = Response()

        result = await video.submit_video(
            request,
            response=http_response,
            client=client,
        )

        self.assertFalse(result.success)
        self.assertTrue(result.submission_uncertain)
        self.assertEqual("WANX_SUBMISSION_UNCERTAIN", result.error_code)
        self.assertEqual(202, http_response.status_code)

    async def test_idempotency_key_is_forwarded_to_provider(self) -> None:
        request = SubmitVideoRequest(
            api_key="offline-key",
            model=VIDEO_MODEL,
            base_url=PROVIDER_BASE_URL,
            prompt="slow camera push",
            image_url="https://assets.invalid/keyframe.png",
            resolution="720P",
            duration_ms=4000,
            idempotency_key="wanx-video-asset-42-v3",
        )
        client = _FakeAsyncClient(
            _FakeResponse(payload={"output": {"task_id": "provider-task-1"}})
        )

        result = await video.submit_video(
            request,
            response=Response(),
            client=client,
        )

        self.assertTrue(result.success)
        self.assertEqual(
            "wanx-video-asset-42-v3",
            client.headers["Idempotency-Key"],
        )

    async def test_provider_task_id_is_not_written_to_logs(self) -> None:
        sentinel = "SENTINEL_TASK_ID\nFORGED_LOG_LINE"
        request = SubmitVideoRequest(
            api_key="offline-key",
            model=VIDEO_MODEL,
            base_url=PROVIDER_BASE_URL,
            prompt="slow camera push",
            image_url="https://assets.invalid/keyframe.png",
            resolution="720P",
            duration_ms=4000,
        )
        client = _FakeAsyncClient(
            _FakeResponse(payload={"output": {"task_id": sentinel}})
        )

        with self.assertLogs(video.logger, level="INFO") as captured:
            result = await video.submit_video(
                request,
                response=Response(),
                client=client,
            )

        self.assertTrue(result.success)
        logs = "\n".join(captured.output)
        self.assertIn(f"task_id_length={len(sentinel)}", logs)
        self.assertNotIn(sentinel, logs)

    async def test_unknown_query_status_is_preserved_as_unknown(self) -> None:
        from app.schemas.video import QueryVideoRequest, TaskStatus

        task_id = "SENTINEL_QUERY_TASK_ID"
        provider_status = "SENTINEL_STATUS\nFORGED_LOG_LINE"
        request = QueryVideoRequest(
            api_key="offline-key",
            base_url=PROVIDER_BASE_URL,
            task_id=task_id,
        )
        client = _FakeAsyncClient(
            _FakeResponse(payload={"output": {"task_status": provider_status}})
        )

        with self.assertLogs(video.logger, level="INFO") as captured:
            result = await video.query_video(
                request,
                response=Response(),
                client=client,
            )

        self.assertTrue(result.success)
        self.assertEqual(TaskStatus.UNKNOWN, result.status)
        self.assertEqual(provider_status, result.provider_status)
        logs = "\n".join(captured.output)
        self.assertIn(f"task_id_length={len(task_id)}", logs)
        self.assertIn(f"status_length={len(provider_status)}", logs)
        self.assertNotIn(task_id, logs)
        self.assertNotIn(provider_status, logs)


if __name__ == "__main__":
    unittest.main()
