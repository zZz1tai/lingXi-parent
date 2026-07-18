"""Low-level ASGI resource and payload limits."""

from __future__ import annotations

import asyncio
from typing import Any

from starlette.responses import JSONResponse

from app.utils.logger import get_request_id


class _RequestBodyTooLarge(Exception):
    pass


class ResourceLimitMiddleware:
    """Bound concurrent requests and enforce body size for streamed payloads."""

    def __init__(
        self,
        app: Any,
        *,
        max_body_bytes: int,
        max_concurrent_requests: int,
        queue_timeout_seconds: float,
    ) -> None:
        self.app = app
        self.max_body_bytes = max_body_bytes
        self.queue_timeout_seconds = queue_timeout_seconds
        self._semaphore = asyncio.Semaphore(max_concurrent_requests)

    async def __call__(self, scope: dict, receive: Any, send: Any) -> None:
        if scope.get("type") != "http":
            await self.app(scope, receive, send)
            return

        content_lengths = [
            value
            for key, value in scope.get("headers", [])
            if key.lower() == b"content-length"
        ]
        if len(content_lengths) > 1:
            await self._send_error(scope, receive, send, 400, "INVALID_CONTENT_LENGTH")
            return
        if content_lengths:
            try:
                declared_length = int(content_lengths[0].decode("ascii"))
            except (UnicodeDecodeError, ValueError):
                await self._send_error(scope, receive, send, 400, "INVALID_CONTENT_LENGTH")
                return
            if declared_length < 0:
                await self._send_error(scope, receive, send, 400, "INVALID_CONTENT_LENGTH")
                return
            if declared_length > self.max_body_bytes:
                await self._send_error(scope, receive, send, 413, "REQUEST_TOO_LARGE")
                return

        try:
            await asyncio.wait_for(
                self._semaphore.acquire(),
                timeout=self.queue_timeout_seconds,
            )
        except TimeoutError:
            await self._send_error(scope, receive, send, 503, "SERVICE_BUSY")
            return

        total = 0
        response_started = False

        async def limited_receive() -> dict:
            nonlocal total
            message = await receive()
            if message.get("type") == "http.request":
                total += len(message.get("body", b""))
                if total > self.max_body_bytes:
                    raise _RequestBodyTooLarge
            return message

        async def tracked_send(message: dict) -> None:
            nonlocal response_started
            if message.get("type") == "http.response.start":
                response_started = True
            await send(message)

        try:
            await self.app(scope, limited_receive, tracked_send)
        except _RequestBodyTooLarge:
            if response_started:
                raise
            await self._send_error(scope, receive, send, 413, "REQUEST_TOO_LARGE")
        finally:
            self._semaphore.release()

    @staticmethod
    async def _send_error(
        scope: dict,
        receive: Any,
        send: Any,
        status_code: int,
        code: str,
    ) -> None:
        response = JSONResponse(
            status_code=status_code,
            content={
                "success": False,
                "error": {"code": code, "message": "Request rejected"},
                "request_id": get_request_id(),
            },
        )
        request_id = get_request_id()
        if request_id and request_id != "-":
            response.headers["X-Request-ID"] = request_id
        await response(scope, receive, send)
