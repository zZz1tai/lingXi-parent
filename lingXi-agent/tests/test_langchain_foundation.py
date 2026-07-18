"""Offline coverage for the shared LangChain/LangGraph integration layer.

These tests replace provider constructors and graph compilation with local
fakes. They must never make an HTTP request or invoke a language model.
"""

from __future__ import annotations

import sys
import unittest
from types import ModuleType, SimpleNamespace
from typing import get_type_hints
from unittest.mock import patch

from langchain_core.messages import HumanMessage, SystemMessage
from langgraph.managed import RemainingSteps
from pydantic import BaseModel

# The developer workstation intentionally has no project environment yet.
# Keep these foundation tests runnable there without installing anything: the
# real package is used whenever present, while this tiny import-time stand-in
# only supplies the two objects needed to construct the Settings class. Tests
# replace the settings instance before exercising any LLM factory behavior.
try:
    import pydantic_settings  # noqa: F401
except ModuleNotFoundError:
    pydantic_settings_module = ModuleType("pydantic_settings")
    pydantic_settings_module.BaseSettings = BaseModel
    pydantic_settings_module.SettingsConfigDict = lambda **kwargs: kwargs
    sys.modules["pydantic_settings"] = pydantic_settings_module

from app.agents import builder
from app.agents.prompts import CASUAL_PROMPT, get_system_prompt
from app.api import dependencies
from app.api.v1.chat import _build_agent_input
from app.schemas.request import ChatRequest, LLMConfig
from app.utils.exceptions import ModelNotAvailableError


class _FakeChatOpenAI:
    created: list["_FakeChatOpenAI"] = []

    def __init__(self, **kwargs):
        self.kwargs = kwargs
        self.__class__.created.append(self)


class _FailingChatOpenAI:
    secret = ""

    def __init__(self, **_kwargs):
        raise RuntimeError(f"provider echoed credential: {self.secret}")


def _settings(**overrides):
    values = {
        "openai_api_key": "env-secret-value",
        "model_name": "env-model",
        "openai_api_base": "https://env.invalid/v1",
        "temperature": 0.7,
        "llm_provider": "openai-compatible",
        "max_iterations": 5,
    }
    values.update(overrides)
    return SimpleNamespace(**values)


def _chat_openai_module(chat_model_type):
    """Provide the lazy integration import without requiring its package."""
    module = ModuleType("langchain_openai")
    module.ChatOpenAI = chat_model_type
    return patch.dict(sys.modules, {"langchain_openai": module})


class LLMFactoryTests(unittest.TestCase):
    def setUp(self) -> None:
        _FakeChatOpenAI.created.clear()
        dependencies.reset_singletons()

    def tearDown(self) -> None:
        dependencies.reset_singletons()

    def test_request_config_and_runtime_overrides_are_forwarded_without_key_logging(self) -> None:
        secret = "request-secret-value"
        config = LLMConfig(
            api_key=secret,
            model="qwen-compatible-model",
            base_url="https://provider.invalid/v1",
        )

        with (
            patch.object(dependencies, "settings", _settings()),
            _chat_openai_module(_FakeChatOpenAI),
            self.assertLogs(dependencies.logger, level="INFO") as captured,
        ):
            model = dependencies.create_llm(
                config,
                profile="chapter-analysis",
                timeout=300,
                temperature=0.1,
                max_retries=2,
                streaming=True,
            )

        self.assertIs(model, _FakeChatOpenAI.created[0])
        self.assertEqual(model.kwargs["model"], "qwen-compatible-model")
        self.assertEqual(model.kwargs["api_key"], secret)
        self.assertEqual(model.kwargs["base_url"], "https://provider.invalid/v1")
        self.assertEqual(model.kwargs["timeout"], 300)
        self.assertEqual(model.kwargs["temperature"], 0.1)
        self.assertEqual(model.kwargs["max_retries"], 2)
        self.assertIs(model.kwargs["streaming"], True)
        log_text = "\n".join(captured.output)
        self.assertIn("profile=chapter-analysis", log_text)
        self.assertNotIn(secret, log_text)

    def test_request_timeout_is_used_when_runtime_override_is_absent(self) -> None:
        config = LLMConfig(
            api_key="request-secret-value",
            model="qwen-compatible-model",
            base_url="https://provider.invalid/v1",
            timeout_seconds=417,
        )

        with (
            patch.object(dependencies, "settings", _settings()),
            _chat_openai_module(_FakeChatOpenAI),
        ):
            model = dependencies.create_llm(
                config,
                profile="chapter-analysis",
                streaming=True,
            )

        self.assertEqual(model.kwargs["timeout"], 417)
        self.assertIs(model.kwargs["streaming"], True)

    def test_default_environment_model_is_cached_even_with_a_profile_label(self) -> None:
        with (
            patch.object(dependencies, "settings", _settings()),
            _chat_openai_module(_FakeChatOpenAI),
        ):
            first = dependencies.create_llm(profile="chat")
            second = dependencies.create_llm(profile="chat")

        self.assertIs(first, second)
        self.assertEqual(len(_FakeChatOpenAI.created), 1)
        self.assertNotIn("streaming", first.kwargs)

    def test_provider_error_redacts_the_complete_api_key(self) -> None:
        secret = "credential-that-must-never-appear"
        _FailingChatOpenAI.secret = secret
        config = LLMConfig(api_key=secret, model="broken-model")

        with (
            patch.object(dependencies, "settings", _settings()),
            _chat_openai_module(_FailingChatOpenAI),
            self.assertLogs(dependencies.logger, level="ERROR") as captured,
            self.assertRaises(ModelNotAvailableError) as raised,
        ):
            dependencies.create_llm(config, profile="redaction-test")

        self.assertNotIn(secret, str(raised.exception))
        self.assertNotIn(secret, "\n".join(captured.output))
        self.assertIn("[REDACTED]", str(raised.exception))


class AgentStateAndPromptTests(unittest.TestCase):
    def test_remaining_steps_uses_langgraph_managed_value(self) -> None:
        annotations = get_type_hints(builder.AgentState, include_extras=True)
        self.assertEqual(annotations["remaining_steps"], RemainingSteps)

    def test_dynamic_prompt_preserves_existing_messages(self) -> None:
        human = HumanMessage(content="请分析这个问题")
        result = get_system_prompt(
            {
                "messages": [human],
                "style": "casual",
                "business_tag": "库存预警",
            }
        )

        self.assertIsInstance(result[0], SystemMessage)
        self.assertIn(CASUAL_PROMPT, result[0].content)
        self.assertIn("库存预警", result[0].content)
        self.assertEqual(result[1:], [human])

    def test_builder_passes_the_managed_state_schema_to_langgraph(self) -> None:
        captured = {}
        compiled_graph = object()

        def fake_create_react_agent(**kwargs):
            captured.update(kwargs)
            return compiled_graph

        fake_model = SimpleNamespace(model_name="offline-fake")
        with patch.object(builder, "create_react_agent", fake_create_react_agent):
            result = builder.build_search_agent(model=fake_model, tools=[])

        self.assertIs(result, compiled_graph)
        self.assertIs(captured["state_schema"], builder.AgentState)
        self.assertIs(captured["prompt"], get_system_prompt)
        self.assertEqual(captured["tools"], [])

    def test_java_chat_input_contract_does_not_supply_managed_state(self) -> None:
        request = ChatRequest(
            message="测试消息",
            style="professional",
            user_id="1002",
            business_tag="dashboard",
            max_iterations=7,
        )

        payload = _build_agent_input(request)

        self.assertNotIn("remaining_steps", payload)
        self.assertEqual(
            set(payload),
            {"messages", "style", "user_id", "business_tag"},
        )
        self.assertIsInstance(payload["messages"][0], HumanMessage)
        self.assertEqual(payload["messages"][0].content, "测试消息")
        self.assertEqual(payload["user_id"], "1002")


if __name__ == "__main__":
    unittest.main()
