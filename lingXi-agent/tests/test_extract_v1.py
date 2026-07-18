"""Offline contracts for explicit LangChain v1 structured output."""

from __future__ import annotations

import unittest
from types import SimpleNamespace
from unittest.mock import patch

from langchain.agents.structured_output import ProviderStrategy, ToolStrategy
from pydantic import BaseModel, ValidationError

from app.agents import builder
from app.api.v1 import extract as extract_api
from app.schemas.request import ExtractRequest, ExtractionStrategy


class ExampleOutput(BaseModel):
    value: str


class ExtractionBuilderTests(unittest.TestCase):
    def test_tool_strategy_is_explicit(self) -> None:
        captured = {}

        def fake_create_agent(**kwargs):
            captured.update(kwargs)
            return object()

        with patch.object(builder, "create_agent", fake_create_agent):
            builder.build_extraction_agent(
                SimpleNamespace(model_name="fake"),
                ExampleOutput,
                strategy="tool",
            )

        self.assertIsInstance(captured["response_format"], ToolStrategy)

    def test_provider_strategy_is_explicit_and_strict(self) -> None:
        captured = {}

        def fake_create_agent(**kwargs):
            captured.update(kwargs)
            return object()

        with patch.object(builder, "create_agent", fake_create_agent):
            builder.build_extraction_agent(
                SimpleNamespace(model_name="fake"),
                ExampleOutput,
                strategy="provider",
            )

        strategy = captured["response_format"]
        self.assertIsInstance(strategy, ProviderStrategy)
        self.assertIs(strategy.schema_spec.strict, True)


class ExtractionEndpointTests(unittest.IsolatedAsyncioTestCase):
    async def test_missing_structured_response_is_never_success_empty_object(self) -> None:
        class EmptyAgent:
            async def ainvoke(self, *_args, **_kwargs):
                return {"messages": []}

        request = ExtractRequest(
            text="Alice works at Example Corp.",
            strategy=ExtractionStrategy.TOOL,
        )
        with (
            patch.object(extract_api, "get_llm", return_value=object()),
            patch.object(
                extract_api,
                "build_extraction_agent",
                return_value=EmptyAgent(),
            ),
            self.assertRaises(extract_api.ExtractionOutputError),
        ):
            await extract_api.extract_structured(request, request_id="request-1")

    async def test_valid_structured_response_is_revalidated(self) -> None:
        class ValidAgent:
            async def ainvoke(self, *_args, **_kwargs):
                return {
                    "structured_response": {
                        "summary": "Example",
                        "key_points": ["Alice works at Example Corp."],
                        "entities": [],
                        "topics": ["employment"],
                        "sentiment": "neutral",
                    }
                }

        request = ExtractRequest(text="Alice works at Example Corp.")
        with (
            patch.object(extract_api, "get_llm", return_value=object()),
            patch.object(
                extract_api,
                "build_extraction_agent",
                return_value=ValidAgent(),
            ),
        ):
            response = await extract_api.extract_structured(
                request,
                request_id="request-1",
            )

        self.assertTrue(response.success)
        self.assertEqual(response.data.result["summary"], "Example")

    def test_unknown_schema_and_invalid_custom_fields_are_rejected(self) -> None:
        with self.assertRaises(ValidationError):
            ExtractRequest(text="hello", schema_name="unknown")
        with self.assertRaises(ValidationError):
            ExtractRequest(text="hello", custom_fields=["bad-name"])
        with self.assertRaises(ValidationError):
            ExtractRequest(text="hello", custom_fields=["name", "name"])


if __name__ == "__main__":
    unittest.main()
