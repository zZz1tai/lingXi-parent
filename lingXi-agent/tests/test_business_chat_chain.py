from __future__ import annotations

import json
import unittest
from typing import Any
from unittest.mock import patch

from langchain_core.messages import AIMessage
from langchain_core.runnables import RunnableLambda
from pydantic import ValidationError

from app.api.v1 import chat as chat_api
from app.chains.business_chat import (
    analyze_context,
    generate_smart_questions,
    stream_context_analysis,
)
from app.main import app
from app.schemas.request import ChatMode, ChatRequest, SmartQuestionHistoryItem


class CapturingModel:
    def __init__(self, response: str):
        self.response = response
        self.input: Any = None

    async def __call__(self, value: Any) -> AIMessage:
        self.input = value
        return AIMessage(content=self.response)


class BusinessChatChainTests(unittest.IsolatedAsyncioTestCase):
    async def test_context_prompt_is_built_in_python_from_structured_data(self) -> None:
        model = CapturingModel("库存异常来自设备 A")
        result = await analyze_context(
            RunnableLambda(model.__call__),
            "有什么异常？",
            {"equipment": [{"id": "A", "status": "offline"}]},
        )

        self.assertEqual("库存异常来自设备 A", result)
        rendered = model.input.to_string()
        self.assertIn("结构化业务数据", rendered)
        self.assertIn('"status":"offline"', rendered)
        self.assertIn("有什么异常？", rendered)

    async def test_context_chain_streams_the_same_python_owned_prompt(self) -> None:
        model = CapturingModel("设备 A 当前离线")

        chunks = [
            chunk
            async for chunk in stream_context_analysis(
                RunnableLambda(model.__call__),
                "设备状态如何？",
                {"equipment": [{"id": "A", "status": "offline"}]},
            )
        ]

        self.assertEqual("设备 A 当前离线", "".join(chunks))
        rendered = model.input.to_string()
        self.assertIn('"status":"offline"', rendered)
        self.assertIn("设备状态如何？", rendered)

    async def test_context_sync_endpoint_uses_business_chain(self) -> None:
        model = CapturingModel("库存数据正常")
        request = ChatRequest(
            message="库存是否正常？",
            mode=ChatMode.CONTEXT_ANALYSIS,
            context_data={"inventory": {"status": "normal"}},
        )

        with patch.object(
            chat_api,
            "create_llm",
            return_value=RunnableLambda(model.__call__),
        ):
            response = await chat_api.chat_invoke(request, request_id="offline-request")

        self.assertTrue(response.success)
        self.assertIsNotNone(response.data)
        self.assertEqual("库存数据正常", response.data.response)
        self.assertEqual("offline-request", response.data.request_id)

    async def test_context_stream_reports_model_initialization_error_as_sse(self) -> None:
        request = ChatRequest(
            message="看一下设备状态",
            mode=ChatMode.CONTEXT_ANALYSIS,
            context_data={"equipment": []},
        )

        with patch.object(
            chat_api,
            "create_llm",
            side_effect=RuntimeError("offline initialization failure"),
        ):
            events = [
                event
                async for event in chat_api._stream_context_analysis(
                    request,
                    "offline-request",
                )
            ]

        self.assertEqual(1, len(events))
        payload = json.loads(events[0].removeprefix("data:").strip())
        self.assertEqual("error", payload["type"])
        self.assertEqual("offline-request", payload["request_id"])

    async def test_smart_questions_are_parsed_and_validated_in_python(self) -> None:
        model = CapturingModel(
            '{"questions":["哪些设备离线？","销售下降原因是什么？","下一步该怎么处理？"]}'
        )
        history = [
            SmartQuestionHistoryItem(content="看一下今天的看板", isUser=True),
            SmartQuestionHistoryItem(content="有两台设备离线", messageType="assistant"),
        ]

        questions = await generate_smart_questions(
            RunnableLambda(model.__call__),
            history,
        )

        self.assertEqual(3, len(questions))
        rendered = model.input.to_string()
        self.assertIn('"role":"user"', rendered)
        self.assertIn('"role":"assistant"', rendered)

    async def test_smart_questions_reject_non_three_item_output(self) -> None:
        model = CapturingModel('{"questions":["只有一条"]}')
        history = [SmartQuestionHistoryItem(content="继续", role="user")]

        with self.assertRaises(ValidationError):
            await generate_smart_questions(RunnableLambda(model.__call__), history)

    async def test_smart_questions_reject_duplicate_output(self) -> None:
        model = CapturingModel(
            '{"questions":["哪些设备离线？","哪些设备离线？","下一步怎么处理？"]}'
        )
        history = [SmartQuestionHistoryItem(content="继续", role="user")]

        with self.assertRaisesRegex(ValidationError, "questions must be unique"):
            await generate_smart_questions(RunnableLambda(model.__call__), history)

    def test_context_mode_requires_structured_context(self) -> None:
        with self.assertRaisesRegex(ValidationError, "context_data is required"):
            ChatRequest(
                message="分析业务数据",
                mode=ChatMode.CONTEXT_ANALYSIS,
            )

    def test_stream_openapi_declares_event_stream_media_type(self) -> None:
        operation = app.openapi()["paths"]["/api/v1/chat/stream"]["post"]
        content = operation["responses"]["200"]["content"]

        self.assertIn("text/event-stream", content)
        self.assertNotIn("application/json", content)


if __name__ == "__main__":
    unittest.main()
