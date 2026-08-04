"""Java Tool Gateway 客户端、运行时令牌和业务工具回归测试。"""

from __future__ import annotations

import json
import unittest
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch

import httpx
from pydantic import SecretStr, ValidationError

from app.agents.state import AgentContext
from app.agents.tools.business_data import create_business_data_tools
from app.api import dependencies
from app.schemas.request import ChatRequest, UserContext
from app.services.agent_tool_client import (
    AgentToolClient,
    AgentToolClientError,
    ToolCallResult,
    ToolMetadata,
)


def _success_envelope() -> dict:
    return {
        "success": True,
        "data": {
            "dataset": "device_status",
            "scope": {
                "region_ids": [12],
                "region_names": ["上海一区"],
                "permission_filtered": True,
            },
            "rows": [{"inner_code": "A001", "vm_status": 1}],
            "truncated": False,
        },
        "metadata": {
            "request_id": "req-11111111111111111111111111111111",
            "tool": "lookup_device",
            "elapsed_ms": 12,
            "generated_at": "2026-07-25T14:00:00+08:00",
            "permission_filtered": True,
            "truncated": False,
        },
        "error": None,
    }


class AgentToolClientTests(unittest.IsolatedAsyncioTestCase):
    async def test_credentials_are_headers_and_identity_never_enters_arguments(self) -> None:
        captured: dict[str, object] = {}

        async def handler(request: httpx.Request) -> httpx.Response:
            captured["authorization"] = request.headers.get("authorization")
            captured["request_id"] = request.headers.get("x-agent-request-id")
            captured["payload"] = json.loads(request.content)
            return httpx.Response(200, json=_success_envelope())

        http_client = httpx.AsyncClient(
            transport=httpx.MockTransport(handler),
            follow_redirects=False,
        )
        try:
            client = AgentToolClient("http://localhost:8080")
            with patch(
                "app.services.agent_tool_client.get_http_client",
                return_value=http_client,
            ):
                result = await client.invoke(
                    tool="lookup_device",
                    arguments={"inner_code": "A001"},
                    token="opaque-secret-token",
                    agent_request_id="req-11111111111111111111111111111111",
                    thread_id="thread-1",
                )
        finally:
            await http_client.aclose()

        self.assertEqual(captured["authorization"], "Bearer opaque-secret-token")
        self.assertEqual(
            captured["request_id"], "req-11111111111111111111111111111111"
        )
        payload = captured["payload"]
        assert isinstance(payload, dict)
        self.assertEqual(payload["arguments"], {"inner_code": "A001"})
        self.assertNotIn("user_id", payload["arguments"])
        self.assertEqual(result.data["scope"]["region_ids"], [12])

    async def test_java_failure_envelope_maps_to_stable_error(self) -> None:
        envelope = _success_envelope()
        envelope.update(
            {
                "success": False,
                "data": None,
                "error": {
                    "code": "TOOL_SCOPE_EMPTY",
                    "message": "请求区域不在当前用户可见范围内",
                    "retryable": False,
                },
            }
        )

        async def handler(_request: httpx.Request) -> httpx.Response:
            return httpx.Response(403, json=envelope)

        http_client = httpx.AsyncClient(transport=httpx.MockTransport(handler))
        try:
            client = AgentToolClient("http://localhost:8080")
            with (
                patch(
                    "app.services.agent_tool_client.get_http_client",
                    return_value=http_client,
                ),
                self.assertRaises(AgentToolClientError) as raised,
            ):
                await client.invoke(
                    tool="lookup_device",
                    arguments={"inner_code": "A001", "region_id": 99},
                    token="opaque-secret-token",
                    agent_request_id="req-11111111111111111111111111111111",
                    thread_id="thread-1",
                )
        finally:
            await http_client.aclose()

        self.assertEqual(raised.exception.code, "TOOL_SCOPE_EMPTY")
        self.assertNotIn("opaque-secret-token", str(raised.exception))


class BusinessDataToolTests(unittest.IsolatedAsyncioTestCase):
    async def test_tool_reads_secret_only_from_runtime_and_emits_safe_progress(self) -> None:
        result = ToolCallResult(
            data=_success_envelope()["data"],
            metadata=ToolMetadata.model_validate(_success_envelope()["metadata"]),
        )
        client = SimpleNamespace(invoke=AsyncMock(return_value=result))
        tools = create_business_data_tools(client)
        lookup_device = next(tool for tool in tools if tool.name == "lookup_device")
        events: list[dict] = []
        runtime = SimpleNamespace(
            context=AgentContext(
                user_id="42",
                thread_id="thread-1",
                region_id=12,
                agent_request_id="req-11111111111111111111111111111111",
                tool_access_token=SecretStr("opaque-secret-token"),
            ),
            stream_writer=events.append,
        )

        assert lookup_device.coroutine is not None
        content, artifact = await lookup_device.coroutine(
            inner_code="A001",
            runtime=runtime,
            region_id=None,
        )

        client.invoke.assert_awaited_once_with(
            tool="lookup_device",
            arguments={"inner_code": "A001", "region_id": None},
            token="opaque-secret-token",
            agent_request_id="req-11111111111111111111111111111111",
            thread_id="thread-1",
        )
        self.assertEqual(json.loads(content)["rows"][0]["inner_code"], "A001")
        self.assertEqual(artifact["provider"], "lingxi-manage")
        self.assertEqual(
            [event["status"] for event in events], ["started", "completed"]
        )
        self.assertNotIn("opaque-secret-token", json.dumps(events))

    async def test_image_tool_sends_only_prompt_options_and_returns_markdown(self) -> None:
        result = ToolCallResult(
            data={
                "image_url": "https://cdn.example.com/generated/cat.png?signature=safe",
                "aspect_ratio": "16:9",
                "model_source": "current_server_config",
            },
            metadata=ToolMetadata.model_validate(_success_envelope()["metadata"]),
        )
        client = SimpleNamespace(invoke=AsyncMock(return_value=result))
        generate_image = next(
            tool
            for tool in create_business_data_tools(client)
            if tool.name == "generate_image"
        )
        events: list[dict] = []
        runtime = SimpleNamespace(
            context=AgentContext(
                user_id="42",
                thread_id="thread-1",
                agent_request_id="req-11111111111111111111111111111111",
                tool_access_token=SecretStr("opaque-secret-token"),
            ),
            stream_writer=events.append,
        )

        assert generate_image.coroutine is not None
        content, artifact = await generate_image.coroutine(
            prompt="一只猫坐在窗边",
            negative_prompt="低清晰度",
            aspect_ratio="16:9",
            runtime=runtime,
        )

        client.invoke.assert_awaited_once_with(
            tool="generate_image",
            arguments={
                "prompt": "一只猫坐在窗边",
                "negative_prompt": "低清晰度",
                "aspect_ratio": "16:9",
            },
            token="opaque-secret-token",
            agent_request_id="req-11111111111111111111111111111111",
            thread_id="thread-1",
        )
        self.assertIn(
            "![生成的图片](<https://cdn.example.com/generated/cat.png?signature=safe>)",
            content,
        )
        self.assertEqual(artifact["tool"], "generate_image")
        self.assertEqual(artifact["data"]["model_source"], "current_server_config")
        self.assertNotIn("model", client.invoke.await_args.kwargs["arguments"])
        self.assertNotIn("api_key", client.invoke.await_args.kwargs["arguments"])
        self.assertNotIn(
            "opaque-secret-token",
            json.dumps({"events": events, "content": content, "artifact": artifact}),
        )

    def test_runtime_registration_is_feature_gated(self) -> None:
        fake_client = SimpleNamespace()
        dependencies.configure_agent_runtime(
            SimpleNamespace(),
            agent_tool_client=fake_client,
        )
        try:
            with (
                patch.object(dependencies, "get_default_tools", return_value=[]),
                patch.object(dependencies.settings, "weather_enabled", True),
                patch.object(
                    dependencies.settings, "agent_write_actions_enabled", False
                ),
            ):
                tools = dependencies._runtime_tools()
            self.assertEqual(
                [tool.name for tool in tools],
                [
                    "get_current_datetime",
                    "calculate",
                    "convert_units",
                    "date_calculator",
                    "get_weather",
                    "query_sales_summary",
                    "query_task_statistics",
                    "query_abnormal_devices",
                    "lookup_device",
                    "generate_image",
                ],
            )
        finally:
            dependencies.reset_singletons()

    async def test_write_tool_interrupts_and_only_internal_code_executes(self) -> None:
        proposal_data = {
            "action_id": "action123",
            "action_type": "CREATE_MAINTENANCE_TASK",
            "status": "PENDING",
            "target": {"inner_code": "A001"},
            "description": "设备制冷异常，请安排检查",
            "impact": "只创建一张待处理维修工单，不修改设备状态、库存或配置",
            "expires_at": "2026-07-25T08:30:00Z",
        }
        executed_data = {
            **proposal_data,
            "status": "SUCCEEDED",
            "result": {"task_id": 9, "task_code": "202607250001"},
        }
        metadata = ToolMetadata.model_validate(_success_envelope()["metadata"])
        client = SimpleNamespace(
            invoke=AsyncMock(
                side_effect=[
                    ToolCallResult(data=proposal_data, metadata=metadata),
                    ToolCallResult(data=executed_data, metadata=metadata),
                ]
            )
        )
        events: list[dict] = []
        runtime = SimpleNamespace(
            context=AgentContext(
                user_id="42",
                thread_id="thread-1",
                checkpointed=True,
                agent_request_id="req-11111111111111111111111111111111",
                tool_access_token=SecretStr("opaque-secret-token"),
            ),
            tool_call_id="call-1",
            stream_writer=events.append,
        )
        with (
            patch.object(
                dependencies.settings, "agent_write_actions_enabled", True
            ),
            patch(
                "app.agents.tools.business_data.interrupt",
                return_value={"action_id": "action123", "decision": "approve"},
            ),
        ):
            tools = create_business_data_tools(client)
            names = [tool.name for tool in tools]
            proposal_tool = next(
                tool for tool in tools if tool.name == "propose_maintenance_task"
            )
            assert proposal_tool.coroutine is not None
            content, artifact = await proposal_tool.coroutine(
                inner_code="A001",
                description="设备制冷异常，请安排检查",
                runtime=runtime,
            )

        self.assertIn("propose_maintenance_task", names)
        self.assertNotIn("execute_maintenance_task", names)
        first_call = client.invoke.await_args_list[0].kwargs
        self.assertEqual(first_call["tool"], "propose_maintenance_task")
        self.assertEqual(len(first_call["arguments"]["idempotency_key"]), 64)
        self.assertEqual(
            client.invoke.await_args_list[1].kwargs,
            {
                "tool": "execute_maintenance_task",
                "arguments": {"action_id": "action123"},
                "token": "opaque-secret-token",
                "agent_request_id": "req-11111111111111111111111111111111",
                "thread_id": "thread-1",
            },
        )
        self.assertIn("202607250001", content)
        self.assertEqual(artifact["status"], "SUCCEEDED")
        self.assertEqual(events[-1]["type"], "action_completed")
        self.assertNotIn("opaque-secret-token", json.dumps(events, ensure_ascii=False))

    async def test_rejected_write_never_calls_execute(self) -> None:
        proposal_data = {
            "action_id": "action123",
            "action_type": "CREATE_MAINTENANCE_TASK",
            "status": "REJECTED",
            "target": {"inner_code": "A001"},
            "description": "检查设备",
            "impact": "只创建一张待处理维修工单，不修改设备状态、库存或配置",
            "expires_at": "2026-07-25T08:30:00Z",
        }
        client = SimpleNamespace(
            invoke=AsyncMock(
                return_value=ToolCallResult(
                    data=proposal_data,
                    metadata=ToolMetadata.model_validate(_success_envelope()["metadata"]),
                )
            )
        )
        runtime = SimpleNamespace(
            context=AgentContext(
                user_id="42",
                thread_id="thread-1",
                checkpointed=True,
                agent_request_id="req-11111111111111111111111111111111",
                tool_access_token=SecretStr("opaque-secret-token"),
            ),
            tool_call_id="call-1",
            stream_writer=lambda _event: None,
        )
        with (
            patch.object(
                dependencies.settings, "agent_write_actions_enabled", True
            ),
            patch(
                "app.agents.tools.business_data.interrupt",
                return_value={"action_id": "action123", "decision": "reject"},
            ),
        ):
            proposal_tool = next(
                tool
                for tool in create_business_data_tools(client)
                if tool.name == "propose_maintenance_task"
            )
            assert proposal_tool.coroutine is not None
            content, artifact = await proposal_tool.coroutine(
                inner_code="A001", description="检查设备", runtime=runtime
            )

        self.assertEqual(client.invoke.await_count, 1)
        self.assertIn("未执行", content)
        self.assertEqual(artifact["status"], "REJECTED")


class ToolAccessRequestTests(unittest.TestCase):
    def test_tool_secret_is_paired_strict_and_not_printable(self) -> None:
        request = ChatRequest(
            message="查设备",
            user_id="42",
            thread_id="thread-1",
            user_context=UserContext(
                user_name="张三",
                region_id=12,
                permissions=["manage:vm:list"],
            ),
            agent_request_id="req-11111111111111111111111111111111",
            tool_access_token="opaque-secret-token-with-enough-entropy-123456",
        )

        self.assertNotIn("opaque-secret-token", repr(request))
        self.assertNotIn("opaque-secret-token", repr(request.tool_access_token))
        with self.assertRaises(ValidationError):
            ChatRequest(
                message="缺少令牌",
                user_id="42",
                user_context=UserContext(user_name="张三"),
                agent_request_id="req-11111111111111111111111111111111",
            )
        with self.assertRaises(ValidationError):
            ChatRequest(
                message="令牌没有可信上下文",
                user_id="42",
                agent_request_id="req-11111111111111111111111111111111",
                tool_access_token="opaque-secret-token-with-enough-entropy-123456",
            )


if __name__ == "__main__":
    unittest.main()
