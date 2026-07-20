"""Regression coverage for service authentication and outbound URL policy."""

from __future__ import annotations

import asyncio
import json
import logging
import unittest
from unittest.mock import patch

import httpx
from pydantic import SecretStr

from app.api.auth import require_service_auth
from app.api.middleware import ResourceLimitMiddleware
from app.main import app, lifespan
from app.security.outbound import validate_outbound_http_url
from app.services.http_client import close_http_client, initialize_http_client
from app.utils.exceptions import AgentError, InputValidationError, generic_error_handler
from app.utils.logger import logger, reset_request_id, set_request_id


class ServiceAuthenticationTests(unittest.TestCase):
    def test_missing_or_wrong_service_key_is_rejected(self) -> None:
        with patch("app.api.auth.settings.service_api_key", SecretStr("correct-secret")):
            with self.assertRaises(AgentError) as missing:
                asyncio.run(require_service_auth(None))
            with self.assertRaises(AgentError) as wrong:
                asyncio.run(require_service_auth("wrong-secret"))

        self.assertEqual(missing.exception.status_code, 401)
        self.assertEqual(wrong.exception.code, "UNAUTHORIZED")

    def test_valid_service_key_preserves_legitimate_java_call(self) -> None:
        with patch("app.api.auth.settings.service_api_key", SecretStr("correct-secret")):
            result = asyncio.run(require_service_auth("correct-secret"))
        self.assertIsNone(result)

    def test_whitespace_only_service_key_fails_closed(self) -> None:
        with patch("app.api.auth.settings.service_api_key", SecretStr("   ")):
            with self.assertRaises(AgentError) as raised:
                asyncio.run(require_service_auth("   "))

        self.assertEqual(raised.exception.code, "CONFIG_ERROR")


class OutboundDestinationTests(unittest.TestCase):
    def test_request_controlled_private_and_unlisted_destinations_are_rejected(self) -> None:
        allowed = {"dashscope.aliyuncs.com"}
        malicious = (
            "http://127.0.0.1:8080/admin",
            "https://169.254.169.254/latest/meta-data",
            "https://[::1]/admin",
            "https://2130706433/admin",
            "https://dashscope.aliyuncs.com@evil.invalid/v1",
            "https://dashscope.aliyuncs.com.evil.invalid/v1",
            "https://evil.invalid/v1",
            "https://dashscope.aliyuncs.com:8443/compatible-mode/v1",
            "file:///etc/passwd",
        )
        for candidate in malicious:
            with self.subTest(candidate=candidate):
                with self.assertRaises(InputValidationError):
                    validate_outbound_http_url(candidate, allowed_hosts=allowed)

    def test_allowlisted_https_provider_is_preserved(self) -> None:
        controls = (
            "https://dashscope.aliyuncs.com/compatible-mode/v1",
            "https://api.openai.com/v1",
        )
        for candidate in controls:
            with self.subTest(candidate=candidate):
                result = validate_outbound_http_url(
                    candidate,
                    allowed_hosts={"dashscope.aliyuncs.com", "api.openai.com"},
                )
                self.assertEqual(result, candidate)

    def test_explicit_wildcard_allows_workspace_subdomain_only(self) -> None:
        allowed = {"*.cn-beijing.maas.aliyuncs.com"}
        candidate = (
            "https://workspace-123.cn-beijing.maas.aliyuncs.com/"
            "api/v1/tasks/task-1"
        )
        self.assertEqual(
            validate_outbound_http_url(candidate, allowed_hosts=allowed),
            candidate,
        )
        rejected = (
            "https://cn-beijing.maas.aliyuncs.com/api/v1/tasks/task-1",
            "https://workspace-123.cn-beijing.maas.aliyuncs.com.evil.invalid/api/v1/tasks/task-1",
            "https://workspace-123.cn-beijing.maas.aliyuncs.com:8443/api/v1/tasks/task-1",
        )
        for url in rejected:
            with self.subTest(url=url):
                with self.assertRaises(InputValidationError):
                    validate_outbound_http_url(url, allowed_hosts=allowed)


class ProviderClientSecurityTests(unittest.IsolatedAsyncioTestCase):
    async def test_shared_provider_client_never_follows_redirects(self) -> None:
        client = await initialize_http_client()
        try:
            self.assertFalse(client.follow_redirects)
        finally:
            await close_http_client()


class RequestLoggingContextTests(unittest.TestCase):
    def test_request_id_is_injected_into_nested_logs(self) -> None:
        class Capture(logging.Handler):
            record: logging.LogRecord | None = None

            def emit(self, record: logging.LogRecord) -> None:
                self.record = record

        capture = Capture()
        for handler in logger.handlers:
            for log_filter in handler.filters:
                capture.addFilter(log_filter)
        logger.addHandler(capture)
        token = set_request_id("security-test-request")
        try:
            logger.info("nested provider log")
        finally:
            reset_request_id(token)
            logger.removeHandler(capture)

        self.assertIsNotNone(capture.record)
        self.assertEqual(capture.record.request_id, "security-test-request")  # type: ignore[union-attr]

    def test_generic_exception_does_not_log_or_return_provider_body(self) -> None:
        sentinel = "provider-body-must-not-escape"
        with self.assertLogs(logger, level="ERROR") as captured:
            response = asyncio.run(
                generic_error_handler(None, RuntimeError(sentinel))  # type: ignore[arg-type]
            )

        self.assertNotIn(sentinel, "\n".join(captured.output))
        self.assertNotIn(sentinel, response.body.decode("utf-8"))


class ResourceLimitBoundaryTests(unittest.IsolatedAsyncioTestCase):
    async def _request(
        self,
        *,
        headers: list[tuple[bytes, bytes]] | None = None,
        chunks: list[tuple[bytes, bool]] | None = None,
        max_body_bytes: int = 8,
    ) -> tuple[int, dict]:
        sent: list[dict] = []
        pending = iter(chunks or [(b"", False)])

        async def receive() -> dict:
            try:
                body, more_body = next(pending)
                return {
                    "type": "http.request",
                    "body": body,
                    "more_body": more_body,
                }
            except StopIteration:
                return {"type": "http.disconnect"}

        async def send(message: dict) -> None:
            sent.append(message)

        async def endpoint(_scope: dict, limited_receive, endpoint_send) -> None:
            more_body = True
            while more_body:
                message = await limited_receive()
                more_body = bool(message.get("more_body"))
            await endpoint_send(
                {
                    "type": "http.response.start",
                    "status": 200,
                    "headers": [(b"content-type", b"application/json")],
                }
            )
            await endpoint_send(
                {
                    "type": "http.response.body",
                    "body": b'{"success":true}',
                }
            )

        middleware = ResourceLimitMiddleware(
            endpoint,
            max_body_bytes=max_body_bytes,
            max_concurrent_requests=1,
            queue_timeout_seconds=1,
        )
        scope = {
            "type": "http",
            "asgi": {"version": "3.0"},
            "http_version": "1.1",
            "method": "POST",
            "scheme": "http",
            "path": "/api/v1/chat/invoke",
            "raw_path": b"/api/v1/chat/invoke",
            "query_string": b"",
            "headers": headers or [],
            "server": ("test", 80),
            "client": ("test", 1234),
        }
        await middleware(scope, receive, send)
        start = next(message for message in sent if message["type"] == "http.response.start")
        raw_body = b"".join(
            message.get("body", b"")
            for message in sent
            if message["type"] == "http.response.body"
        )
        return start["status"], json.loads(raw_body)

    async def test_declared_oversized_body_is_rejected_before_endpoint(self) -> None:
        status, payload = await self._request(
            headers=[(b"content-length", b"9")],
        )
        self.assertEqual(status, 413)
        self.assertEqual(payload["error"]["code"], "REQUEST_TOO_LARGE")

    async def test_streamed_body_is_bounded_without_content_length(self) -> None:
        status, payload = await self._request(
            chunks=[(b"12345", True), (b"6789", False)],
        )
        self.assertEqual(status, 413)
        self.assertEqual(payload["error"]["code"], "REQUEST_TOO_LARGE")

    async def test_ambiguous_or_negative_content_length_is_rejected(self) -> None:
        variants = (
            [(b"content-length", b"-1")],
            [(b"content-length", b"1"), (b"content-length", b"1")],
        )
        for headers in variants:
            with self.subTest(headers=headers):
                status, payload = await self._request(headers=headers)
                self.assertEqual(status, 400)
                self.assertEqual(payload["error"]["code"], "INVALID_CONTENT_LENGTH")

    async def test_body_at_limit_reaches_endpoint(self) -> None:
        status, payload = await self._request(
            headers=[(b"content-length", b"8")],
            chunks=[(b"1234", True), (b"5678", False)],
        )
        self.assertEqual(status, 200)
        self.assertTrue(payload["success"])


class FastAPISecurityBoundaryTests(unittest.IsolatedAsyncioTestCase):
    async def test_health_is_public_and_agent_routes_require_service_auth(self) -> None:
        with patch("app.api.auth.settings.service_api_key", SecretStr("java-secret")):
            async with lifespan(app):
                transport = httpx.ASGITransport(app=app, raise_app_exceptions=False)
                async with httpx.AsyncClient(
                    transport=transport,
                    base_url="http://testserver",
                ) as client:
                    health = await client.get("/livez")
                    missing = await client.post(
                        "/api/v1/chat/invoke",
                        json={"message": "不应触发模型"},
                    )
                    wrong = await client.post(
                        "/api/v1/chat/invoke",
                        headers={"X-Agent-Service-Key": "wrong-secret"},
                        json={"message": "不应触发模型"},
                    )

        self.assertEqual(health.status_code, 200)
        self.assertEqual(missing.status_code, 401)
        self.assertEqual(missing.json()["error"]["code"], "UNAUTHORIZED")
        self.assertEqual(wrong.status_code, 401)

    async def test_valid_java_key_reaches_route_validation(self) -> None:
        with patch("app.api.auth.settings.service_api_key", SecretStr("java-secret")):
            async with lifespan(app):
                transport = httpx.ASGITransport(app=app, raise_app_exceptions=False)
                async with httpx.AsyncClient(
                    transport=transport,
                    base_url="http://testserver",
                ) as client:
                    response = await client.post(
                        "/api/v1/chat/invoke",
                        headers={"X-Agent-Service-Key": "java-secret"},
                        json={},
                    )

        self.assertEqual(response.status_code, 422)
        payload = response.json()
        self.assertEqual(payload["error"]["code"], "VALIDATION_ERROR")
        self.assertIn("request_id", payload)
        self.assertEqual(response.headers["X-Request-ID"], payload["request_id"])

    async def test_authenticated_request_cannot_reach_private_model_endpoint(self) -> None:
        secret = "request-secret-that-must-not-escape"
        with patch("app.api.auth.settings.service_api_key", SecretStr("java-secret")):
            async with lifespan(app):
                transport = httpx.ASGITransport(app=app, raise_app_exceptions=False)
                async with httpx.AsyncClient(
                    transport=transport,
                    base_url="http://testserver",
                ) as client:
                    response = await client.post(
                        "/api/v1/chat/invoke",
                        headers={"X-Agent-Service-Key": "java-secret"},
                        json={
                            "message": "不能访问内网",
                            "mode": "context_analysis",
                            "context_data": {"probe": True},
                            "llm_config": {
                                "api_key": secret,
                                "model": "test-model",
                                "base_url": "http://169.254.169.254/latest/meta-data",
                            },
                        },
                    )

        self.assertEqual(response.status_code, 422)
        rendered = response.text
        self.assertEqual(response.json()["error"]["code"], "VALIDATION_ERROR")
        self.assertNotIn(secret, rendered)
        self.assertNotIn("169.254.169.254", rendered)


if __name__ == "__main__":
    unittest.main()
