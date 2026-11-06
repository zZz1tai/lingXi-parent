"""内部知识检索的权限、版本、工具和配置回归测试。"""

from __future__ import annotations

import json
import tempfile
import unittest
from datetime import date
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from langchain_core.messages import AIMessage, HumanMessage, ToolMessage
from langgraph.graph import END, START, MessagesState, StateGraph
from langgraph.prebuilt import ToolNode
from langgraph.types import interrupt
from pydantic import ValidationError

from app.agents.checkpoints import create_in_memory_checkpointer
from app.agents.state import AgentContext
from app.agents.tools.knowledge_search import create_knowledge_search_tool
from app.api import dependencies
from app.api.v1 import chat as chat_api
from app.api.v1.chat import _safe_custom_event
from app.config.settings import Settings
from app.schemas.request import ActionResumeRequest, ChatRequest, UserContext
from app.services.knowledge import (
    InMemoryKnowledgeRetriever,
    KnowledgeChunk,
    create_knowledge_retriever,
)
from app.utils.exceptions import ConfigurationError


def _chunk(**overrides) -> KnowledgeChunk:
    payload = {
        "document_id": "sop-replenishment",
        "title": "补货工单操作规范",
        "section": "3.2 完成工单",
        "content": "完成补货后，需要确认每个货道的实际补货数量已经提交。",
        "document_type": "sop",
        "version": "2026-06",
        "effective_from": date(2026, 6, 1),
        "effective_to": None,
        "visibility_roles": ("1001", "1002"),
        "product_model": None,
        "source_id": "sop-replenishment#3.2@2026-06",
        "source_uri": "knowledge://internal/path-must-not-be-public",
        "keywords": ("补货工单", "完成工单"),
        "is_current": True,
    }
    payload.update(overrides)
    return KnowledgeChunk.model_validate(payload)


class KnowledgeRetrieverTests(unittest.IsolatedAsyncioTestCase):
    async def test_role_date_and_version_filters_run_before_ranking(self) -> None:
        old = _chunk(
            version="2026-01",
            effective_from=date(2026, 1, 1),
            source_id="sop-replenishment#3.2@2026-01",
            content="旧版本要求。",
        )
        current = _chunk()
        future = _chunk(
            document_id="future-sop",
            section="未来流程",
            effective_from=date(2027, 1, 1),
            source_id="future-sop#1",
        )
        retriever = InMemoryKnowledgeRetriever([old, current, future])

        results = await retriever.search(
            "补货工单怎么完成",
            role_code="1002",
            top_k=8,
            as_of=date(2026, 7, 25),
        )

        self.assertEqual([item.chunk.version for item in results], ["2026-06"])
        self.assertNotIn("旧版本要求", results[0].chunk.content)

        unauthorized = await retriever.search(
            "补货工单怎么完成",
            role_code="1003",
            as_of=date(2026, 7, 25),
        )
        anonymous = await retriever.search(
            "补货工单怎么完成",
            role_code="",
            as_of=date(2026, 7, 25),
        )
        self.assertEqual(unauthorized, [])
        self.assertEqual(anonymous, [])

    async def test_document_type_and_product_model_only_narrow_scope(self) -> None:
        generic = _chunk()
        model_specific = _chunk(
            document_id="fault-a1",
            title="A1 故障码手册",
            section="E01",
            content="A1 型号 E01 表示温控异常。",
            document_type="fault_code",
            product_model="A1",
            source_id="fault-a1#E01",
            keywords=("E01", "温控异常"),
        )
        retriever = InMemoryKnowledgeRetriever([generic, model_specific])

        results = await retriever.search(
            "E01 温控异常",
            role_code="1002",
            document_type="fault_code",
            product_model="A1",
        )

        self.assertEqual([item.chunk.source_id for item in results], ["fault-a1#E01"])

    async def test_tool_reads_role_from_runtime_and_emits_safe_citations(self) -> None:
        retriever = InMemoryKnowledgeRetriever([_chunk()])
        knowledge_tool = create_knowledge_search_tool(retriever)
        events: list[dict] = []
        runtime = SimpleNamespace(
            context=AgentContext(role_code="1002"),
            stream_writer=events.append,
        )

        assert knowledge_tool.coroutine is not None
        with (
            patch("app.agents.tools.knowledge_search.settings.knowledge_top_k", 8),
            patch(
                "app.agents.tools.knowledge_search.settings.knowledge_rerank_top_n",
                5,
            ),
        ):
            content, artifact = await knowledge_tool.coroutine(
                query="补货工单怎么完成",
                runtime=runtime,
                document_type=None,
                product_model=None,
            )

        payload = json.loads(content)
        self.assertEqual(payload["results"][0]["source_id"], "sop-replenishment#3.2@2026-06")
        self.assertNotIn("source_uri", content)
        self.assertEqual(artifact["result_count"], 1)
        self.assertNotIn("content", artifact["citations"][0])
        citation = artifact["citations"][0]
        self.assertEqual(payload["results"][0]["content"], citation["excerpt"])
        self.assertFalse(citation["excerpt_truncated"])
        self.assertEqual("sop", citation["document_type"])
        self.assertEqual(
            [event["type"] for event in events],
            ["tool_progress", "citation", "tool_progress"],
        )

    async def test_excerpt_never_expands_model_evidence_and_is_unicode_bounded(self) -> None:
        source = "补货工单" + "😀" * 4_000
        knowledge_tool = create_knowledge_search_tool(
            InMemoryKnowledgeRetriever([_chunk(content=source)])
        )
        for model_limit in (200, 2_000, 2_400):
            events = []
            runtime = SimpleNamespace(
                context=AgentContext(role_code="1002"), stream_writer=events.append,
            )
            with patch(
                "app.agents.tools.knowledge_search.settings.knowledge_model_chunk_chars",
                model_limit,
            ):
                content, artifact = await knowledge_tool.coroutine(
                    query="补货工单", runtime=runtime,
                )
            evidence = json.loads(content)["results"][0]["content"]
            citation = artifact["citations"][0]
            self.assertEqual(source[:model_limit], evidence)
            self.assertEqual(evidence[:2_000], citation["excerpt"])
            self.assertTrue(citation["excerpt_truncated"])
            self.assertEqual(citation, events[1]["citation"])
            public = _safe_custom_event(events[1], request_id="r", thread_id="t")
            self.assertEqual(citation, public.data)

    async def test_citations_only_contain_current_visible_evidence(self) -> None:
        private = _chunk(document_id="private", source_id="private",
                         visibility_roles=("admin",), content="补货工单机密片段")
        expired = _chunk(document_id="expired", source_id="expired",
                         effective_to=date(2000, 1, 2), effective_from=date(2000, 1, 1))
        future = _chunk(document_id="future", source_id="future",
                        effective_from=date(9999, 1, 1))
        old = _chunk(source_id="old", version="2020", effective_from=date(2020, 1, 1))
        current = _chunk()
        knowledge_tool = create_knowledge_search_tool(
            InMemoryKnowledgeRetriever([private, expired, future, old, current])
        )
        for role_code, expected_count in (("1002", 1), ("1003", 0), ("", 0)):
            events = []
            runtime = SimpleNamespace(
                context=AgentContext(role_code=role_code), stream_writer=events.append,
            )
            content, artifact = await knowledge_tool.coroutine(
                query="补货工单", runtime=runtime,
            )
            self.assertEqual(expected_count, artifact["result_count"])
            self.assertEqual(expected_count, len(artifact["citations"]))
            self.assertEqual(expected_count, sum(event["type"] == "citation" for event in events))
            serialized = content + json.dumps(artifact, ensure_ascii=False) + json.dumps(events)
            self.assertNotIn("机密片段", serialized)
            for internal in ("source_uri", "visibility_roles", "effective_from", "source_id\": \"old"):
                self.assertNotIn(internal, serialized)
            if expected_count:
                self.assertEqual(current.content, artifact["citations"][0]["excerpt"])

    def test_sse_citation_whitelist_rejects_wrong_types_and_bounds_strings(self) -> None:
        raw = {
            "title": "题" * 301, "section": "节" * 301, "version": "v" * 129,
            "source_id": "s" * 513, "document_type": "d" * 129,
            "excerpt": "😀" * 2_001, "excerpt_truncated": False,
            "score": True, "source_uri": "internal://secret", "visibility_roles": ["admin"],
        }
        event = _safe_custom_event(
            {"type": "citation", "citation": raw}, request_id="r", thread_id="t",
        )
        self.assertEqual({
            "title": "题" * 300, "section": "节" * 300, "version": "v" * 128,
            "document_type": "d" * 128, "excerpt": "😀" * 2_000,
            "excerpt_truncated": True,
        }, event.data)
        for score in (True, "0.9", float("nan"), float("inf"), 10 ** 1000):
            event = _safe_custom_event({"type": "citation", "citation": {
                "title": 42, "excerpt": {}, "excerpt_truncated": "false", "score": score,
            }}, request_id="r", thread_id="t")
            self.assertEqual({}, event.data)
        event = _safe_custom_event({"type": "citation", "citation": {
            "source_id": "x" * 512, "excerpt": "证据", "excerpt_truncated": 1, "score": 0.91,
        }}, request_id="r", thread_id="t")
        self.assertEqual({"source_id": "x" * 512, "excerpt": "证据", "score": 0.91}, event.data)

    def test_resume_snapshots_only_include_current_turn_knowledge_and_twenty_unique_sources(self) -> None:
        def tool_message(citations, name="search_knowledge"):
            return ToolMessage(content="证据", name=name, tool_call_id="call-1", artifact={
                "provider": "internal_knowledge", "citations": citations,
            })

        old = tool_message([{"source_id": "old", "excerpt": "旧轮次内容"}])
        current = tool_message(
            [{"source_id": "new", "excerpt": "当前证据", "source_uri": "secret"}] * 2
            + [{"source_id": f"source-{index}", "excerpt": "证据"} for index in range(25)]
        )
        messages = [HumanMessage(content="旧问题"), old, HumanMessage(content="当前问题"),
                    tool_message([{"source_id": "foreign"}], name="web_search"), current]
        citations = chat_api._current_turn_knowledge_citations(messages)
        self.assertEqual(20, len(citations))
        self.assertEqual({"source_id": "new", "excerpt": "当前证据"}, citations[0])
        self.assertNotIn("old", [item["source_id"] for item in citations])
        self.assertNotIn("foreign", [item["source_id"] for item in citations])
        self.assertEqual([], chat_api._current_turn_knowledge_citations([old]))

    async def test_real_checkpoint_pause_resume_replays_only_same_users_evidence(self) -> None:
        knowledge_tool = create_knowledge_search_tool(InMemoryKnowledgeRetriever([_chunk()]))
        graph = StateGraph(MessagesState, context_schema=AgentContext)

        async def plan(_state):
            return {"messages": [AIMessage(content="", tool_calls=[{
                "name": "search_knowledge", "args": {"query": "补货工单"},
                "id": "search-1", "type": "tool_call",
            }])]}

        async def approve(_state):
            interrupt({"type": "approval_required", "action": {
                "action_id": "action123", "action_type": "CREATE_MAINTENANCE_TASK",
                "status": "PENDING", "target": {"inner_code": "A001"},
                "description": "检查设备", "impact": "创建待处理工单",
            }})
            return {"messages": [AIMessage(content="已记录处理决定。按补货规范检查数量。") ]}

        graph.add_node("plan", plan)
        graph.add_node("tools", ToolNode([knowledge_tool], handle_tool_errors=False))
        graph.add_node("approval", approve)
        graph.add_edge(START, "plan")
        graph.add_edge("plan", "tools")
        graph.add_edge("tools", "approval")
        graph.add_edge("approval", END)
        agent = graph.compile(checkpointer=create_in_memory_checkpointer())
        initial = ChatRequest(message="补货工单如何完成", user_id="42", thread_id="shared-name")
        resumed = ActionResumeRequest(
            action_id="action123", decision="reject", user_id="42", thread_id="shared-name",
            user_context=UserContext(user_name="测试用户", role_code="1002"),
            agent_request_id="req-11111111111111111111111111111111",
            tool_access_token="opaque-secret-token-with-enough-entropy-123456",
        )
        context = AgentContext(user_id="42", thread_id="shared-name", role_code="1002", checkpointed=True)
        with (
            patch.object(chat_api, "get_agent", return_value=agent),
            patch.object(chat_api, "create_agent_context", return_value=context),
            patch.object(chat_api, "with_trace", side_effect=lambda config, *_args, **_kwargs: config),
        ):
            first = [json.loads(event.removeprefix("data:").strip())
                     async for event in chat_api._stream_agent_events(initial, "r-1")]
            self.assertIn("approval_required", [event["type"] for event in first])
            self.assertNotIn("done", [event["type"] for event in first])
            original = next(event["data"] for event in first if event["type"] == "citation")
            # 相同公共会话名的另一用户没有这个 checkpoint，不得重放任何证据。
            foreign = resumed.model_copy(update={"user_id": "99"})
            other = [json.loads(event.removeprefix("data:").strip())
                     async for event in chat_api._stream_agent_events(foreign, "r-other", resume=True)]
            self.assertNotIn("citation", [event["type"] for event in other])
            self.assertEqual(["error"], [event["type"] for event in other])
            second = [json.loads(event.removeprefix("data:").strip())
                      async for event in chat_api._stream_agent_events(resumed, "r-2", resume=True)]
        self.assertEqual("citation", second[0]["type"])
        self.assertEqual(original, second[0]["data"])
        self.assertEqual(1, sum(event["type"] == "citation" for event in second))
        self.assertEqual("done", second[-1]["type"])
        snapshot = await agent.aget_state(chat_api._build_agent_config(resumed, request_id="r-2"))
        self.assertEqual(1, sum(isinstance(item, HumanMessage) for item in snapshot.values["messages"]))


class KnowledgeConfigurationTests(unittest.TestCase):
    def test_jsonl_backend_validates_and_loads_strict_records(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            index_path = Path(directory) / "index.jsonl"
            index_path.write_text(
                _chunk().model_dump_json() + "\n",
                encoding="utf-8",
            )
            retriever = create_knowledge_retriever(
                backend="jsonl",
                index_path=index_path,
                max_index_bytes=1024 * 1024,
            )

        self.assertIsNotNone(retriever)

    def test_enabled_backend_fails_closed_for_missing_or_invalid_index(self) -> None:
        with self.assertRaises(ConfigurationError):
            create_knowledge_retriever(
                backend="jsonl",
                index_path=None,
                max_index_bytes=1024 * 1024,
            )
        with tempfile.TemporaryDirectory() as directory:
            invalid = Path(directory) / "invalid.jsonl"
            invalid.write_text('{"unexpected":true}\n', encoding="utf-8")
            with self.assertRaises(ConfigurationError):
                create_knowledge_retriever(
                    backend="jsonl",
                    index_path=invalid,
                    max_index_bytes=1024 * 1024,
                )

    def test_settings_reject_inverted_retrieval_limits(self) -> None:
        with self.assertRaises(ValidationError):
            Settings(
                _env_file=None,
                knowledge_top_k=3,
                knowledge_rerank_top_n=5,
            )

    def test_runtime_tool_registration_is_feature_gated(self) -> None:
        retriever = InMemoryKnowledgeRetriever([_chunk()])
        dependencies.configure_agent_runtime(
            SimpleNamespace(),
            knowledge_retriever=retriever,
        )
        try:
            with patch.object(dependencies, "get_default_tools", return_value=[]):
                tools = dependencies._runtime_tools()
            self.assertEqual(
                [tool.name for tool in tools],
                [
                    "get_current_datetime",
                    "calculate",
                    "convert_units",
                    "date_calculator",
                    "analyze_csv",
                    "get_weather",
                    "search_knowledge",
                ],
            )
        finally:
            dependencies.reset_singletons()


if __name__ == "__main__":
    unittest.main()
