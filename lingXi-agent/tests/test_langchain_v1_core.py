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
from app.schemas.request import ChatRequest, DeleteChatThreadRequest


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

class CheckpointMemoryTests(unittest.IsolatedAsyncioTestCase):
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
                    {"type": "tool_progress", "status": "completed"},
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
        self.assertEqual(
            fake_agent.stream_modes,
            ["messages", "updates", "custom"],
        )
        self.assertEqual(token["content"], "你好")
        self.assertEqual(token["content_blocks"], [{"type": "text", "text": "你好"}])
        self.assertNotIn("content", done)
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
