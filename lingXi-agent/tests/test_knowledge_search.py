"""内部知识检索的权限、版本、工具和配置回归测试。"""

from __future__ import annotations

import json
import tempfile
import unittest
from datetime import date
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from pydantic import ValidationError

from app.agents.state import AgentContext
from app.agents.tools.knowledge_search import create_knowledge_search_tool
from app.api import dependencies
from app.config.settings import Settings
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
        self.assertEqual(
            [event["type"] for event in events],
            ["tool_progress", "citation", "tool_progress"],
        )


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
            self.assertEqual([tool.name for tool in tools], ["search_knowledge"])
        finally:
            dependencies.reset_singletons()


if __name__ == "__main__":
    unittest.main()
