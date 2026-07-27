"""显式 args_schema 与 ToolRuntime 的真实 ToolNode 注入回归测试。"""

from __future__ import annotations

import json
import unittest
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch

from langchain_core.messages import AIMessage, ToolMessage
from langchain_core.tools import BaseTool
from langgraph.graph import END, START, MessagesState, StateGraph
from langgraph.prebuilt import ToolNode
from pydantic import SecretStr

from app.agents.state import AgentContext
from app.agents.tools.business_data import create_business_data_tools
from app.agents.tools.knowledge_search import create_knowledge_search_tool
from app.agents.tools.web_search import create_tavily_search_tool
from app.services.agent_tool_client import ToolCallResult, ToolMetadata
from app.services.knowledge import InMemoryKnowledgeRetriever, KnowledgeChunk


def _tool_graph(tool: BaseTool):
    builder = StateGraph(MessagesState, context_schema=AgentContext)
    builder.add_node("tools", ToolNode([tool], handle_tool_errors=False))
    builder.add_edge(START, "tools")
    builder.add_edge("tools", END)
    return builder.compile()


def _tool_state(name: str, arguments: dict[str, object]) -> dict[str, object]:
    return {
        "messages": [
            AIMessage(
                content="",
                tool_calls=[
                    {
                        "name": name,
                        "args": arguments,
                        "id": f"{name}-call-1",
                        "type": "tool_call",
                    }
                ],
            )
        ]
    }


class _FakeTavilyClient:
    def __init__(self, **_kwargs: object) -> None:
        self.closed = False

    async def search(self, **_kwargs: object) -> dict[str, object]:
        return {
            "results": [
                {
                    "title": "示例科技新闻",
                    "url": "https://example.com/news",
                    "content": "用于验证 ToolNode 注入路径。",
                }
            ]
        }

    async def close(self) -> None:
        self.closed = True


class ToolRuntimeInjectionTests(unittest.IsolatedAsyncioTestCase):
    async def test_web_search_runs_through_tool_node_and_streams_progress(self) -> None:
        tool = create_tavily_search_tool()
        events: list[dict[str, object]] = []
        final_update: dict[str, object] | None = None

        with patch("tavily.AsyncTavilyClient", _FakeTavilyClient):
            async for mode, chunk in _tool_graph(tool).astream(
                _tool_state("web_search", {"query": "2026年7月25日 科技新闻"}),
                context=AgentContext(user_id="42"),
                stream_mode=["custom", "updates"],
            ):
                if mode == "custom":
                    events.append(chunk)
                else:
                    final_update = chunk

        self.assertEqual(
            [event["status"] for event in events],
            ["started", "completed"],
        )
        self.assertIsNotNone(final_update)
        assert final_update is not None
        message = final_update["tools"]["messages"][0]  # type: ignore[index]
        self.assertIsInstance(message, ToolMessage)
        self.assertEqual(message.status, "success")
        self.assertEqual(message.artifact["provider"], "tavily")
        self.assertEqual(message.artifact["results"][0]["title"], "示例科技新闻")

    async def test_business_tool_runtime_survives_explicit_input_schema(self) -> None:
        result = ToolCallResult(
            data={
                "dataset": "device_status",
                "scope": {"region_ids": [12], "permission_filtered": True},
                "rows": [{"inner_code": "A001", "vm_status": 1}],
                "truncated": False,
            },
            metadata=ToolMetadata(
                request_id="req-11111111111111111111111111111111",
                tool="lookup_device",
                elapsed_ms=12,
                generated_at="2026-07-25T17:30:00+08:00",
                permission_filtered=True,
                truncated=False,
            ),
        )
        client = SimpleNamespace(invoke=AsyncMock(return_value=result))
        tool = next(
            item
            for item in create_business_data_tools(client)
            if item.name == "lookup_device"
        )

        output = await _tool_graph(tool).ainvoke(
            _tool_state("lookup_device", {"inner_code": "A001"}),
            context=AgentContext(
                user_id="42",
                thread_id="thread-1",
                region_id=12,
                agent_request_id="req-11111111111111111111111111111111",
                tool_access_token=SecretStr("opaque-secret-token"),
            ),
        )

        message = output["messages"][-1]
        self.assertIsInstance(message, ToolMessage)
        self.assertEqual(json.loads(message.content)["rows"][0]["inner_code"], "A001")
        client.invoke.assert_awaited_once()

    async def test_knowledge_tool_runtime_survives_explicit_input_schema(self) -> None:
        chunk = KnowledgeChunk.model_validate(
            {
                "document_id": "sop-replenishment",
                "title": "补货工单操作规范",
                "section": "完成工单",
                "content": "完成补货后提交实际补货数量。",
                "document_type": "sop",
                "version": "2026-06",
                "effective_from": "2026-06-01",
                "visibility_roles": ["1002"],
                "source_id": "sop-replenishment#complete@2026-06",
                "source_uri": "knowledge://internal/not-public",
                "keywords": ["补货工单", "完成工单"],
                "is_current": True,
            }
        )
        tool = create_knowledge_search_tool(InMemoryKnowledgeRetriever([chunk]))

        output = await _tool_graph(tool).ainvoke(
            _tool_state("search_knowledge", {"query": "补货工单怎么完成"}),
            context=AgentContext(user_id="42", role_code="1002"),
        )

        message = output["messages"][-1]
        self.assertIsInstance(message, ToolMessage)
        self.assertEqual(message.artifact["result_count"], 1)
        self.assertNotIn("source_uri", message.content)

    def test_runtime_is_hidden_from_every_model_visible_schema(self) -> None:
        business_client = SimpleNamespace(invoke=AsyncMock())
        tools = [
            create_tavily_search_tool(),
            *create_business_data_tools(business_client),
        ]
        for tool in tools:
            with self.subTest(tool=tool.name):
                self.assertIn("runtime", tool.get_input_schema().model_fields)
                self.assertNotIn(
                    "runtime",
                    tool.tool_call_schema.model_json_schema()["properties"],
                )


if __name__ == "__main__":
    unittest.main()
