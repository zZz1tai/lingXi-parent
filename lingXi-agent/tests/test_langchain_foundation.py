"""Offline coverage for the shared LangChain/LangGraph integration layer.

These tests replace provider constructors and graph compilation with local
fakes. They must never make an HTTP request or invoke a language model.
"""

from __future__ import annotations

import sys
import unittest
from contextlib import contextmanager
from types import ModuleType, SimpleNamespace
from unittest.mock import patch

from langchain_core.messages import HumanMessage
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
from app.agents.prompts import CASUAL_PROMPT, compose_system_prompt, get_system_prompt
from app.agents.state import AgentContext, RetailAgentState
from app.api import dependencies
from app.api.v1.chat import _build_agent_input
from app.schemas.request import ChatRequest, LLMConfig
from app.utils.exceptions import (
    ConfigurationError,
    InputValidationError,
    ModelNotAvailableError,
)


class _FakeChatOpenAI:
    created: list["_FakeChatOpenAI"] = []

    def __init__(self, **kwargs):
        self.kwargs = kwargs
        self.__class__.created.append(self)


class _FailingChatOpenAI:
    secret = ""

    def __init__(self, **_kwargs):
        raise RuntimeError(f"provider echoed credential: {self.secret}")


_SHARED_HTTP_CLIENT = object()


def _settings(**overrides):
    values = {
        "openai_api_key": "env-secret-value",
        "model_name": "env-model",
        "openai_api_base": "https://dashscope.aliyuncs.com/compatible-mode/v1",
        "temperature": 0.7,
        "llm_provider": "openai-compatible",
        "max_iterations": 5,
    }
    values.update(overrides)
    return SimpleNamespace(**values)


@contextmanager
def _chat_model_factory(chat_model_type):
    """Replace init_chat_model without invoking a provider integration."""

    def factory(model, **kwargs):
        return chat_model_type(model=model, **kwargs)

    with (
        patch.object(dependencies, "init_chat_model", side_effect=factory) as constructor,
        patch.object(
            dependencies,
            "get_http_client",
            return_value=_SHARED_HTTP_CLIENT,
        ),
    ):
        yield constructor


class LLMFactoryTests(unittest.TestCase):
    def setUp(self) -> None:
        _FakeChatOpenAI.created.clear()
        dependencies.reset_singletons()

    def tearDown(self) -> None:
        dependencies.reset_singletons()

    def test_request_config_and_runtime_overrides_are_forwarded_without_key_logging(self) -> None:
        secret = "request-secret-value"
        model_name = "qwen-compatible-model\nFORGED_LOG_LINE"
        config = LLMConfig(
            api_key=secret,
            model=model_name,
            base_url="https://dashscope.aliyuncs.com/compatible-mode/v1",
        )

        with (
            patch.object(dependencies, "settings", _settings()),
            _chat_model_factory(_FakeChatOpenAI),
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
        self.assertEqual(model.kwargs["model"], model_name)
        self.assertEqual(model.kwargs["api_key"], secret)
        self.assertEqual(
            model.kwargs["base_url"],
            "https://dashscope.aliyuncs.com/compatible-mode/v1",
        )
        self.assertEqual(model.kwargs["output_version"], "v1")
        self.assertIs(model.kwargs["http_async_client"], _SHARED_HTTP_CLIENT)
        self.assertEqual(model.kwargs["timeout"], 300)
        self.assertEqual(model.kwargs["temperature"], 0.1)
        self.assertEqual(model.kwargs["max_retries"], 2)
        self.assertIs(model.kwargs["streaming"], True)
        log_text = "\n".join(captured.output)
        self.assertIn("profile=chapter-analysis", log_text)
        self.assertIn(f"model_length={len(model_name)}", log_text)
        self.assertNotIn(model_name, log_text)
        self.assertNotIn(secret, log_text)

    def test_request_timeout_is_used_when_runtime_override_is_absent(self) -> None:
        config = LLMConfig(
            api_key="request-secret-value",
            model="qwen-compatible-model",
            base_url="https://dashscope.aliyuncs.com/compatible-mode/v1",
            timeout_seconds=417,
        )

        with (
            patch.object(dependencies, "settings", _settings()),
            _chat_model_factory(_FakeChatOpenAI),
        ):
            model = dependencies.create_llm(
                config,
                profile="chapter-analysis",
                streaming=True,
            )

        self.assertEqual(model.kwargs["timeout"], 417)
        self.assertIs(model.kwargs["streaming"], True)

    def test_equivalent_request_models_use_the_bounded_cache(self) -> None:
        config = LLMConfig(
            api_key="request-secret-value",
            model="qwen-compatible-model",
            base_url="https://dashscope.aliyuncs.com/compatible-mode/v1",
        )

        with (
            patch.object(dependencies, "settings", _settings()),
            _chat_model_factory(_FakeChatOpenAI) as constructor,
        ):
            first = dependencies.create_llm(config, profile="chat-request")
            second = dependencies.create_llm(config, profile="chat-request")

        self.assertIs(first, second)
        self.assertEqual(constructor.call_count, 1)

    def test_default_environment_model_is_cached_even_with_a_profile_label(self) -> None:
        with (
            patch.object(dependencies, "settings", _settings()),
            _chat_model_factory(_FakeChatOpenAI),
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
            _chat_model_factory(_FailingChatOpenAI),
            self.assertLogs(dependencies.logger, level="ERROR") as captured,
            self.assertRaises(ModelNotAvailableError) as raised,
        ):
            dependencies.create_llm(config, profile="redaction-test")

        self.assertNotIn(secret, str(raised.exception))
        self.assertNotIn(secret, "\n".join(captured.output))
        self.assertIn("[REDACTED]", str(raised.exception))

    def test_request_base_url_rejection_remains_a_validation_error(self) -> None:
        config = LLMConfig(
            api_key="request-secret-value",
            model="qwen-compatible-model",
            base_url="https://provider.invalid/v1",
        )

        with (
            patch.object(dependencies, "settings", _settings()),
            patch.object(dependencies, "init_chat_model") as constructor,
            self.assertRaises(InputValidationError),
        ):
            dependencies.create_llm(config, profile="validation-test")

        constructor.assert_not_called()

    def test_model_creation_requires_the_lifespan_http_client(self) -> None:
        config = LLMConfig(
            api_key="request-secret-value",
            model="qwen-compatible-model",
            base_url="https://dashscope.aliyuncs.com/compatible-mode/v1",
        )
        with (
            patch.object(dependencies, "settings", _settings()),
            patch.object(
                dependencies,
                "get_http_client",
                side_effect=ConfigurationError("Provider HTTP client is not initialized"),
            ),
            patch.object(dependencies, "init_chat_model") as constructor,
            self.assertRaises(ConfigurationError),
        ):
            dependencies.create_llm(config, profile="direct-call")

        constructor.assert_not_called()


class AgentStateAndPromptTests(unittest.TestCase):
    def test_state_extends_the_v1_agent_state_contract(self) -> None:
        self.assertIs(builder.AgentState, RetailAgentState)
        self.assertIn("messages", RetailAgentState.__annotations__)

    def test_dynamic_prompt_uses_immutable_runtime_context(self) -> None:
        result = compose_system_prompt(
            AgentContext(
                style="casual",
                business_tag="库存预警",
            ),
            search_available=True,
        )

        self.assertIn(CASUAL_PROMPT, result)
        self.assertIn("库存预警", result)
        self.assertNotIn("当前未配置联网搜索工具", result)

    def test_builder_passes_the_managed_state_schema_to_langgraph(self) -> None:
        captured = {}
        compiled_graph = object()

        def fake_create_agent(**kwargs):
            captured.update(kwargs)
            return compiled_graph

        fake_model = SimpleNamespace(model_name="offline-fake")
        with (
            patch.object(builder, "create_agent", fake_create_agent),
            patch.object(builder, "build_agent_middleware", return_value=[get_system_prompt]),
        ):
            result = builder.build_search_agent(
                model=fake_model,
                tools=[],
                checkpointer=False,
            )

        self.assertIs(result, compiled_graph)
        self.assertIs(captured["state_schema"], RetailAgentState)
        self.assertIs(captured["context_schema"], AgentContext)
        self.assertEqual(captured["middleware"], [get_system_prompt])
        self.assertIs(captured["checkpointer"], False)
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
            {"messages"},
        )
        self.assertIsInstance(payload["messages"][0], HumanMessage)
        self.assertEqual(payload["messages"][0].content, "测试消息")


if __name__ == "__main__":
    unittest.main()
