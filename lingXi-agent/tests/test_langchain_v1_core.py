"""Contract tests for the LangChain v1 agent foundation.

These tests are deliberately provider-free.  They assert the application
boundary we rely on while allowing LangChain itself to be replaced with
fakes where a real model call would otherwise be required.
"""

from __future__ import annotations

import unittest
import hashlib
import json
import asyncio
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch

from langchain_core.language_models.fake_chat_models import FakeListChatModel
from langchain_core.messages import (
    AIMessage,
    AIMessageChunk,
    HumanMessage,
    ToolMessage,
)

from app.agents import builder
from app.agents.checkpoints import create_in_memory_checkpointer
from app.agents.middleware import RuntimeModelSummarizationMiddleware
from app.agents import middleware as middleware_module
from app.agents.state import AgentContext, checkpoint_thread_id
from app.api import dependencies
from app.api.v1 import chat as chat_api
from app.schemas.request import (
    ActionResumeRequest,
    ChatRequest,
    DeleteChatThreadRequest,
    ImageOcrRequest,
    UserContext,
)


class LangChainV1ContractTests(unittest.TestCase):
    def test_builder_exposes_the_v1_create_agent_factory(self) -> None:
        self.assertTrue(
            hasattr(builder, "create_agent"),
            "the core builder must use langchain.agents.create_agent",
        )
        self.assertFalse(
            hasattr(builder, "create_react_agent"),
            "the deprecated langgraph.prebuilt factory must not remain",
        )

    def test_thread_id_is_not_derived_from_user_id(self) -> None:
        request = ChatRequest(
            message="继续刚才的话题",
            thread_id="conversation-42",
            user_id="user-7",
        )

        config = chat_api._build_agent_config(
            request,
            request_id="request-1",
        )

        expected = "lingxi:" + hashlib.sha256(
            json.dumps(
                ["user-7", "conversation-42"],
                ensure_ascii=False,
                separators=(",", ":"),
            ).encode("utf-8")
        ).hexdigest()
        self.assertEqual(config["configurable"]["thread_id"], expected)
        self.assertNotEqual(request.thread_id, request.user_id)

    def test_checkpoint_namespace_has_no_delimiter_collisions(self) -> None:
        first = chat_api._build_agent_config(
            ChatRequest(message="a", user_id="a:b", thread_id="c"),
            request_id="request-1",
        )
        second = chat_api._build_agent_config(
            ChatRequest(message="a", user_id="a", thread_id="b:c"),
            request_id="request-2",
        )

        self.assertNotEqual(
            first["configurable"]["thread_id"],
            second["configurable"]["thread_id"],
        )

    def test_agent_input_only_contains_messages(self) -> None:
        request = ChatRequest(
            message="测试消息",
            thread_id="conversation-42",
            user_id="user-7",
            style="casual",
            business_tag="库存预警",
        )

        payload = chat_api._build_agent_input(request)

        self.assertEqual(set(payload), {"messages"})
        self.assertIsInstance(payload["messages"][0], HumanMessage)

    def test_attachment_only_message_builds_multimodal_content(self) -> None:
        request = ChatRequest(
            attachments=[
                {
                    "attachment_id": "123e4567-e89b-42d3-a456-426614174000",
                    "name": "screen.png",
                    "mime_type": "image/png",
                    "size": 1024,
                    "kind": "image",
                    "image_url": "https://oss.example.com/signed.png?token=short",
                    "extracted_text": "神通骨\n作者名",
                },
                {
                    "attachment_id": "123e4567-e89b-42d3-a456-426614174001",
                    "name": "prompt.py",
                    "mime_type": "text/plain",
                    "size": 128,
                    "kind": "document",
                    "extracted_text": "print('hello')",
                },
            ]
        )

        payload = chat_api._build_agent_input(request)
        content = payload["messages"][0].content

        self.assertIsInstance(content, list)
        self.assertEqual(content[0]["type"], "text")
        self.assertIn("请分析", content[0]["text"])
        self.assertTrue(
            any(
                block.get("type") == "text" and "print('hello')" in block.get("text", "")
                for block in content
            )
        )
        image = next(block for block in content if block.get("type") == "image_url")
        self.assertEqual(image["image_url"]["detail"], "auto")
        ocr = next(
            block
            for block in content
            if block.get("type") == "text" and "<image_ocr" in block.get("text", "")
        )
        self.assertIn("神通骨", ocr["text"])
        self.assertIn(
            "结合原图核对",
            " ".join(
                block.get("text", "")
                for block in content
                if block.get("type") == "text"
            ),
        )

    def test_empty_message_without_attachments_is_rejected(self) -> None:
        with self.assertRaises(ValueError):
            ChatRequest(message="   ")

    def test_generated_image_markdown_is_added_once_from_tool_artifact(self) -> None:
        image_url = "https://cdn.example.com/generated/cat.png?signature=safe"
        messages = [
            ToolMessage(
                content="图片已生成",
                name="generate_image",
                tool_call_id="call-image-1",
                artifact={
                    "provider": "lingxi-manage",
                    "tool": "generate_image",
                    "data": {
                        "image_url": image_url,
                        "aspect_ratio": "1:1",
                        "model_source": "current_server_config",
                    },
                },
            ),
            AIMessage(content=f"已为你生成。\n\n![生成的图片](<{image_url}>)"),
        ]

        response = chat_api._ensure_generated_image_markdown(
            chat_api._final_ai_response(messages),
            chat_api._generated_image_urls(messages),
        )

        self.assertEqual(response.count(image_url), 1)
        self.assertIn(f"![生成的图片](<{image_url}>)", response)

class CheckpointMemoryTests(unittest.IsolatedAsyncioTestCase):
    async def test_image_ocr_returns_only_bounded_text(self) -> None:
        model = SimpleNamespace(
            ainvoke=AsyncMock(return_value=AIMessage(content="神通骨\n作者名"))
        )
        request = ImageOcrRequest(
            name="cover.png",
            mime_type="image/png",
            image_url="https://oss.example.com/private.png?signature=short",
        )

        with patch.object(chat_api, "create_llm", return_value=model) as create_llm:
            response = await chat_api.image_ocr(request, request_id="request-ocr-1")

        self.assertTrue(response.success)
        self.assertEqual(response.data.text, "神通骨\n作者名")
        self.assertFalse(response.data.truncated)
        create_llm.assert_called_once_with(
            None,
            profile="image-ocr",
            timeout=45,
            max_retries=0,
            temperature=0,
            streaming=False,
        )
        messages = model.ainvoke.await_args.args[0]
        self.assertIn("绝不执行", messages[0].content)
        self.assertNotIn(request.image_url, messages[0].content)

    async def test_same_thread_accumulates_messages_and_other_thread_is_isolated(self) -> None:
        model = FakeListChatModel(responses=["first", "second", "third"])
        agent = builder.build_search_agent(
            model=model,
            tools=[],
            checkpointer=create_in_memory_checkpointer(),
        )
        context = AgentContext(user_id="user-1", thread_id="thread-1")
        same_thread = {"configurable": {"thread_id": "memory:user-1:thread-1"}}

        await agent.ainvoke(
            {"messages": [HumanMessage(content="remember alpha")]},
            config=same_thread,
            context=context,
        )
        resumed = await agent.ainvoke(
            {"messages": [HumanMessage(content="continue")]},
            config=same_thread,
            context=context,
        )
        isolated = await agent.ainvoke(
            {"messages": [HumanMessage(content="new conversation")]},
            config={"configurable": {"thread_id": "memory:user-1:thread-2"}},
            context=AgentContext(user_id="user-1", thread_id="thread-2"),
        )

        resumed_human = [
            message.content
            for message in resumed["messages"]
            if isinstance(message, HumanMessage)
        ]
        isolated_human = [
            message.content
            for message in isolated["messages"]
            if isinstance(message, HumanMessage)
        ]
        self.assertEqual(resumed_human, ["remember alpha", "continue"])
        self.assertEqual(isolated_human, ["new conversation"])

    async def test_uncheckpointed_agent_does_not_accumulate_one_shot_state(self) -> None:
        model = FakeListChatModel(responses=["first", "second"])
        agent = builder.build_search_agent(
            model=model,
            tools=[],
            checkpointer=None,
        )
        config = {"configurable": {"thread_id": "ephemeral-request"}}
        context = AgentContext(thread_id="ephemeral-request")

        await agent.ainvoke(
            {"messages": [HumanMessage(content="first one-shot")]},
            config=config,
            context=context,
        )
        second = await agent.ainvoke(
            {"messages": [HumanMessage(content="second one-shot")]},
            config=config,
            context=context,
        )

        second_human = [
            message.content
            for message in second["messages"]
            if isinstance(message, HumanMessage)
        ]
        self.assertEqual(second_human, ["second one-shot"])

    def test_ephemeral_and_checkpointed_agents_are_cached_separately(self) -> None:
        saver = SimpleNamespace(adelete_thread=AsyncMock())
        checkpointed_agent = object()
        ephemeral_agent = object()
        dependencies.configure_agent_runtime(saver)
        try:
            with (
                patch.object(dependencies, "get_llm", return_value=object()),
                patch.object(
                    dependencies,
                    "build_search_agent",
                    side_effect=[checkpointed_agent, ephemeral_agent],
                ) as build_agent,
            ):
                self.assertIs(
                    dependencies.get_agent(checkpointed=True),
                    checkpointed_agent,
                )
                self.assertIs(
                    dependencies.get_agent(checkpointed=False),
                    ephemeral_agent,
                )
                self.assertIs(
                    dependencies.get_agent(checkpointed=False),
                    ephemeral_agent,
                )
        finally:
            dependencies.reset_singletons()

        self.assertEqual(build_agent.call_count, 2)
        self.assertIs(build_agent.call_args_list[0].kwargs["checkpointer"], saver)
        self.assertIsNone(build_agent.call_args_list[1].kwargs["checkpointer"])

    async def test_one_shot_chat_selects_the_ephemeral_agent(self) -> None:
        class FakeAgent:
            async def ainvoke(self, _input, **_kwargs):
                return {"messages": [AIMessage(content="one-shot response")]}

        with (
            patch.object(chat_api, "get_agent", return_value=FakeAgent()) as get_agent,
            patch.object(
                chat_api,
                "create_agent_context",
                return_value=AgentContext(thread_id="request-1"),
            ),
        ):
            response = await chat_api.chat_invoke(
                ChatRequest(message="one shot"),
                request_id="request-1",
            )

        self.assertTrue(response.success)
        get_agent.assert_called_once_with(checkpointed=False)

    async def test_delete_thread_uses_the_same_private_namespace(self) -> None:
        saver = SimpleNamespace(adelete_thread=AsyncMock())
        dependencies.configure_agent_runtime(saver)
        try:
            await dependencies.delete_agent_thread(
                user_id="user-1",
                thread_id="thread-1",
            )
        finally:
            dependencies.reset_singletons()

        saver.adelete_thread.assert_awaited_once_with(
            checkpoint_thread_id("user-1", "thread-1")
        )

    async def test_delete_endpoint_does_not_log_raw_identifiers(self) -> None:
        user_id = "sensitive-user"
        thread_id = "sensitive-thread"
        with (
            patch.object(chat_api, "delete_agent_thread", new=AsyncMock()),
            self.assertLogs(chat_api.logger, level="INFO") as logs,
        ):
            response = await chat_api.delete_chat_thread(
                DeleteChatThreadRequest(user_id=user_id, thread_id=thread_id),
                request_id="request-1",
            )

        self.assertTrue(response.success)
        log_text = "\n".join(logs.output)
        self.assertNotIn(user_id, log_text)
        self.assertNotIn(thread_id, log_text)
        self.assertIn("user_id_length", log_text)


class StreamingContractTests(unittest.IsolatedAsyncioTestCase):
    async def test_generated_image_markdown_is_forced_before_done(self) -> None:
        image_url = "https://cdn.example.com/generated/cat.png?signature=safe"

        class ImageAgent:
            async def astream(self, _input, **_kwargs):
                yield (
                    "updates",
                    {
                        "tools": {
                            "messages": [
                                ToolMessage(
                                    content="图片已生成",
                                    name="generate_image",
                                    tool_call_id="call-image-1",
                                    artifact={
                                        "provider": "lingxi-manage",
                                        "tool": "generate_image",
                                        "data": {
                                            "image_url": image_url,
                                            "aspect_ratio": "1:1",
                                            "model_source": "current_server_config",
                                        },
                                    },
                                )
                            ]
                        }
                    },
                )
                yield (
                    "messages",
                    (
                        AIMessageChunk(content="已经为你生成好了。"),
                        {"langgraph_node": "model"},
                    ),
                )

        request = ChatRequest(message="生成一张猫的图片", thread_id="thread-1")
        with (
            patch.object(chat_api, "get_agent", return_value=ImageAgent()),
            patch.object(
                chat_api,
                "create_agent_context",
                return_value=AgentContext(thread_id="thread-1"),
            ),
        ):
            payloads = [
                json.loads(event.removeprefix("data:").strip())
                async for event in chat_api._stream_agent_events(request, "request-1")
            ]

        token_text = "".join(
            payload.get("content", "")
            for payload in payloads
            if payload["type"] == "token"
        )
        self.assertIn(f"![生成的图片](<{image_url}>)", token_text)
        self.assertEqual(token_text.count(image_url), 1)
        self.assertEqual(payloads[-1]["type"], "done")

    async def test_interrupt_is_a_whitelisted_approval_event_without_done(self) -> None:
        public_action = {
            "action_id": "action123",
            "action_type": "CREATE_MAINTENANCE_TASK",
            "status": "PENDING",
            "target": {"inner_code": "A001"},
            "description": "检查设备",
            "impact": "只创建一张待处理维修工单，不修改设备状态、库存或配置",
            "expires_at": "2026-07-25T08:30:00Z",
            "user_id": "must-not-escape",
        }

        class InterruptedAgent:
            async def astream(self, _input, **_kwargs):
                yield (
                    "updates",
                    {
                        "__interrupt__": (
                            SimpleNamespace(
                                value={
                                    "type": "approval_required",
                                    "action": public_action,
                                }
                            ),
                        )
                    },
                )

        request = ChatRequest(
            message="创建维修工单", user_id="42", thread_id="thread-1"
        )
        with (
            patch.object(chat_api, "get_agent", return_value=InterruptedAgent()),
            patch.object(
                chat_api,
                "create_agent_context",
                return_value=AgentContext(
                    user_id="42", thread_id="thread-1", checkpointed=True
                ),
            ),
        ):
            payloads = [
                json.loads(event.removeprefix("data:").strip())
                async for event in chat_api._stream_agent_events(request, "request-1")
            ]

        self.assertEqual([payload["type"] for payload in payloads], ["approval_required"])
        self.assertEqual(payloads[0]["data"]["target"]["inner_code"], "A001")
        self.assertNotIn("user_id", json.dumps(payloads))

    async def test_resume_uses_command_and_same_checkpoint_without_user_message(self) -> None:
        captured: dict[str, object] = {}

        class ResumeAgent:
            async def astream(self, agent_input, **kwargs):
                captured["input"] = agent_input
                captured["config"] = kwargs["config"]
                yield (
                    "custom",
                    {
                        "type": "action_rejected",
                        "action": {
                            "action_id": "action123",
                            "action_type": "CREATE_MAINTENANCE_TASK",
                            "status": "REJECTED",
                            "target": {"inner_code": "A001"},
                            "description": "检查设备",
                            "impact": "只创建一张待处理维修工单，不修改设备状态、库存或配置",
                            "expires_at": "2026-07-25T08:30:00Z",
                        },
                    },
                )

        request = ActionResumeRequest(
            action_id="action123",
            decision="reject",
            user_id="42",
            thread_id="thread-1",
            user_context=UserContext(
                user_name="张三", permissions=["manage:task:add"]
            ),
            agent_request_id="req-11111111111111111111111111111111",
            tool_access_token="opaque-secret-token-with-enough-entropy-123456",
        )
        with (
            patch.object(chat_api, "get_agent", return_value=ResumeAgent()),
            patch.object(
                chat_api,
                "create_agent_context",
                return_value=AgentContext(
                    user_id="42", thread_id="thread-1", checkpointed=True
                ),
            ),
        ):
            payloads = [
                json.loads(event.removeprefix("data:").strip())
                async for event in chat_api._stream_agent_events(
                    request, "request-2", resume=True
                )
            ]

        self.assertEqual(type(captured["input"]).__name__, "Command")
        self.assertEqual(payloads[0]["type"], "action_rejected")
        self.assertEqual(payloads[-1]["type"], "done")
        self.assertEqual(
            captured["config"]["configurable"]["thread_id"],
            checkpoint_thread_id("42", "thread-1"),
        )

    async def test_v1_stream_modes_and_content_blocks_are_normalized(self) -> None:
        class FakeAgent:
            stream_modes = None

            async def astream(self, _input, **kwargs):
                self.stream_modes = kwargs["stream_mode"]
                yield (
                    "messages",
                    (
                        AIMessageChunk(
                            content=[{"type": "text", "text": "你好"}]
                        ),
                        {"langgraph_node": "model"},
                    ),
                )
                yield (
                    "updates",
                    {
                        "model": {
                            "messages": [
                                AIMessage(
                                    content=[{"type": "text", "text": "你好"}]
                                )
                            ]
                        }
                    },
                )
                yield (
                    "custom",
                    {
                        "type": "tool_progress",
                        "tool": "search_knowledge",
                        "status": "completed",
                        "result_count": 2,
                        "internal_url": "must-not-escape",
                    },
                )
                yield (
                    "custom",
                    {
                        "type": "citation",
                        "tool": "search_knowledge",
                        "citation": {
                            "title": "补货规范",
                            "section": "完成工单",
                            "version": "2026-06",
                            "source_id": "sop#3.2",
                            "score": 0.91,
                            "source_uri": "internal://must-not-escape",
                        },
                    },
                )

        fake_agent = FakeAgent()
        request = ChatRequest(
            message="打个招呼",
            user_id="user-1",
            thread_id="thread-1",
        )
        with (
            patch.object(chat_api, "get_agent", return_value=fake_agent),
            patch.object(
                chat_api,
                "create_agent_context",
                return_value=AgentContext(user_id="user-1", thread_id="thread-1"),
            ),
        ):
            raw_events = [
                event
                async for event in chat_api._stream_agent_events(
                    request,
                    "request-1",
                )
            ]

        payloads = [json.loads(event.removeprefix("data:").strip()) for event in raw_events]
        token = next(payload for payload in payloads if payload["type"] == "token")
        done = next(payload for payload in payloads if payload["type"] == "done")
        progress = next(
            payload for payload in payloads if payload["type"] == "tool_progress"
        )
        citation = next(
            payload for payload in payloads if payload["type"] == "citation"
        )
        self.assertEqual(
            fake_agent.stream_modes,
            ["messages", "updates", "custom"],
        )
        self.assertEqual(token["content"], "你好")
        self.assertEqual(token["content_blocks"], [{"type": "text", "text": "你好"}])
        self.assertNotIn("content", done)
        self.assertEqual(progress["tool"], "search_knowledge")
        self.assertEqual(progress["data"], {"status": "completed", "result_count": 2})
        self.assertNotIn("internal_url", json.dumps(progress))
        self.assertEqual(citation["data"]["source_id"], "sop#3.2")
        self.assertNotIn("source_uri", json.dumps(citation))
        self.assertEqual(
            [payload["type"] for payload in payloads].count("done"),
            1,
        )

    async def test_output_limit_cancels_the_agent_stream(self) -> None:
        class LimitedAgent:
            closed = False

            async def astream(self, _input, **_kwargs):
                try:
                    for _ in range(10):
                        yield (
                            "messages",
                            (
                                AIMessageChunk(content="abc"),
                                {"langgraph_node": "model"},
                            ),
                        )
                finally:
                    self.closed = True

        fake_agent = LimitedAgent()
        request = ChatRequest(message="stream", thread_id="thread-1")
        with (
            patch.object(chat_api, "get_agent", return_value=fake_agent),
            patch.object(
                chat_api,
                "create_agent_context",
                return_value=AgentContext(thread_id="thread-1"),
            ),
            patch.object(chat_api.settings, "agent_stream_max_text_chars", 5),
        ):
            events = [
                json.loads(event.removeprefix("data:").strip())
                async for event in chat_api._stream_agent_events(request, "request-1")
            ]

        self.assertTrue(fake_agent.closed)
        self.assertEqual(events[-1]["type"], "error")
        self.assertEqual(events[-1]["content"], "Stream output limit exceeded")

    async def test_wall_clock_limit_cancels_continuous_small_events(self) -> None:
        closed = asyncio.Event()

        async def source():
            try:
                while True:
                    await asyncio.sleep(0.001)
                    yield "data: {}\n\n"
            finally:
                closed.set()

        class ConnectedRequest:
            async def is_disconnected(self) -> bool:
                return False

        with patch.object(chat_api.settings, "agent_stream_max_seconds", 0.02):
            events = [
                event
                async for event in chat_api._with_heartbeats(
                    source(),
                    http_request=ConnectedRequest(),
                    request_id="request-1",
                    thread_id="thread-1",
                )
            ]

        await asyncio.wait_for(closed.wait(), timeout=1)
        payload = json.loads(events[-1].removeprefix("data:").strip())
        self.assertEqual(payload["type"], "error")
        self.assertEqual(payload["content"], "Stream time limit exceeded")

    async def test_full_queue_disconnect_closes_source_without_hanging(self) -> None:
        class FastSource:
            def __init__(self) -> None:
                self.emitted = 0
                self.full_queue_attempted = asyncio.Event()
                self.closed = asyncio.Event()

            def __aiter__(self):
                return self

            async def __anext__(self) -> str:
                self.emitted += 1
                if self.emitted >= 18:
                    self.full_queue_attempted.set()
                return f"data: {self.emitted}\n\n"

            async def aclose(self) -> None:
                self.closed.set()

        class DisconnectableRequest:
            disconnected = False

            async def is_disconnected(self) -> bool:
                return self.disconnected

        source = FastSource()
        request = DisconnectableRequest()
        stream = chat_api._with_heartbeats(
            source,
            http_request=request,
            request_id="request-1",
            thread_id="thread-1",
        )

        first = await asyncio.wait_for(anext(stream), timeout=1)
        self.assertEqual(first, "data: 1\n\n")
        await asyncio.wait_for(source.full_queue_attempted.wait(), timeout=1)
        request.disconnected = True

        with self.assertRaises(StopAsyncIteration):
            await asyncio.wait_for(anext(stream), timeout=1)
        await asyncio.wait_for(source.closed.wait(), timeout=1)

    async def test_updates_only_final_response_cannot_bypass_output_limit(self) -> None:
        class UpdatesOnlyAgent:
            async def astream(self, _input, **_kwargs):
                yield (
                    "updates",
                    {"model": {"messages": [AIMessage(content="abcdef")]}}
                )

        request = ChatRequest(message="stream", thread_id="thread-1")
        with (
            patch.object(chat_api, "get_agent", return_value=UpdatesOnlyAgent()),
            patch.object(
                chat_api,
                "create_agent_context",
                return_value=AgentContext(thread_id="thread-1"),
            ),
            patch.object(chat_api.settings, "agent_stream_max_text_chars", 5),
        ):
            events = [
                json.loads(event.removeprefix("data:").strip())
                async for event in chat_api._stream_agent_events(request, "request-1")
            ]

        self.assertEqual(events[-1]["type"], "error")
        self.assertNotIn("done", [event["type"] for event in events])

    async def test_stream_close_failure_is_logged_but_does_not_break_sse(self) -> None:
        class BadCloseStream:
            emitted = False

            def __aiter__(self):
                return self

            async def __anext__(self):
                if self.emitted:
                    raise StopAsyncIteration
                self.emitted = True
                return (
                    "messages",
                    (AIMessageChunk(content="ok"), {"langgraph_node": "model"}),
                )

            async def aclose(self):
                raise RuntimeError("close-provider-detail")

        class BadCloseAgent:
            def astream(self, _input, **_kwargs):
                return BadCloseStream()

        request = ChatRequest(message="stream", thread_id="thread-1")
        with (
            patch.object(chat_api, "get_agent", return_value=BadCloseAgent()),
            patch.object(
                chat_api,
                "create_agent_context",
                return_value=AgentContext(thread_id="thread-1"),
            ),
            self.assertLogs(chat_api.logger, level="WARNING") as logs,
        ):
            events = [
                json.loads(event.removeprefix("data:").strip())
                async for event in chat_api._stream_agent_events(request, "request-1")
            ]

        self.assertEqual(events[-1]["type"], "done")
        self.assertNotIn("close-provider-detail", "\n".join(logs.output))


def _character_counter(messages) -> int:
    total = 0
    for message in messages:
        content = getattr(message, "content", message)
        if isinstance(content, str):
            total += len(content)
        else:
            total += len(str(content))
    return total


class RuntimeSummarizationTests(unittest.IsolatedAsyncioTestCase):
    async def test_request_model_summarizes_and_default_model_is_not_called(self) -> None:
        default_model = FakeListChatModel(responses=["DEFAULT", "unused"])
        request_model = FakeListChatModel(responses=["REQUEST SUMMARY", "unused"])
        middleware = RuntimeModelSummarizationMiddleware(
            model=default_model,
            trigger=("tokens", 20),
            keep=("tokens", 5),
            token_counter=_character_counter,
        )

        update = await middleware.abefore_model(
            {
                "messages": [
                    HumanMessage(content="old context " * 20),
                    HumanMessage(content="next"),
                ]
            },
            SimpleNamespace(context=AgentContext(model=request_model)),
        )

        self.assertIsNotNone(update)
        self.assertEqual(request_model.i, 1)
        self.assertEqual(default_model.i, 0)
        update_text = " ".join(
            str(getattr(message, "content", ""))
            for message in update["messages"]
        )
        self.assertIn("REQUEST SUMMARY", update_text)
        self.assertNotIn("DEFAULT", update_text)

    async def test_summary_failure_does_not_persist_provider_error_text(self) -> None:
        class FailingModel(FakeListChatModel):
            async def _agenerate(self, *_args, **_kwargs):
                raise RuntimeError("provider-secret-response")

        middleware = RuntimeModelSummarizationMiddleware(
            model=FakeListChatModel(responses=["DEFAULT", "unused"]),
            trigger=("tokens", 20),
            keep=("tokens", 5),
            token_counter=_character_counter,
        )
        with self.assertLogs(middleware_module.logger, level="ERROR") as logs:
            update = await middleware.abefore_model(
                {
                    "messages": [
                        HumanMessage(content="old context " * 20),
                        HumanMessage(content="next"),
                    ]
                },
                SimpleNamespace(
                    context=AgentContext(
                        model=FailingModel(responses=["unused", "unused-2"])
                    )
                ),
            )

        self.assertIsNone(update)
        self.assertNotIn("provider-secret-response", "\n".join(logs.output))

    async def test_token_keep_summarizes_few_long_messages_without_orphaning_tools(self) -> None:
        middleware = RuntimeModelSummarizationMiddleware(
            model=FakeListChatModel(responses=["summary", "unused"]),
            trigger=("tokens", 20),
            keep=("tokens", 8),
            token_counter=_character_counter,
        )
        update = await middleware.abefore_model(
            {
                "messages": [
                    HumanMessage(content="x" * 200),
                    AIMessage(
                        content="",
                        tool_calls=[{"name": "search", "args": {}, "id": "call-1"}],
                    ),
                    ToolMessage(content="result", tool_call_id="call-1"),
                    HumanMessage(content="latest"),
                ]
            },
            SimpleNamespace(context=AgentContext()),
        )

        self.assertIsNotNone(update)
        preserved = update["messages"]
        tool_messages = [m for m in preserved if isinstance(m, ToolMessage)]
        ai_tool_ids = {
            call["id"]
            for message in preserved
            if isinstance(message, AIMessage)
            for call in message.tool_calls
        }
        self.assertTrue(
            all(message.tool_call_id in ai_tool_ids for message in tool_messages)
        )


if __name__ == "__main__":
    unittest.main()
