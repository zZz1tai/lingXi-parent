from __future__ import annotations

import unittest

from langgraph.checkpoint.memory import InMemorySaver
from langgraph.store.memory import InMemoryStore
from pydantic import ValidationError

from app.agents.prompts import compose_system_prompt
from app.agents.state import AgentContext
from app.agents.stores import store_lifespan
from app.api.dependencies import configure_agent_runtime, reset_singletons
from app.api.v1.chat import (
    clear_long_term_memories,
    list_long_term_memories,
    update_long_term_preference,
)
from app.config.settings import Settings
from app.schemas.request import MemoryPreferenceRequest, MemoryUserRequest
from app.services.memory import LongTermMemoryService, extract_explicit_preferences
from app.utils.exceptions import ConfigurationError


_SECRET = "memory-test-secret-with-at-least-32-bytes"


class LongTermMemoryServiceTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self) -> None:
        self.store = InMemoryStore()
        self.service = LongTermMemoryService(
            self.store,
            namespace_secret=_SECRET,
            max_recall=5,
            write_confidence=0.9,
        )

    async def test_explicit_preferences_are_normalized_and_overwritten(self) -> None:
        saved = await self.service.capture_explicit_preferences(
            "user-1",
            "以后回答简短一点，并且请先说结论。",
        )
        self.assertEqual(
            {(item.preference, item.value) for item in saved},
            {
                ("answer_length", "short"),
                ("answer_structure", "conclusion_first"),
            },
        )

        await self.service.capture_explicit_preferences(
            "user-1",
            "以后回答详细展开一点。",
        )
        recalled = await self.service.recall_preferences("user-1")
        self.assertEqual(
            {item.preference: item.value for item in recalled},
            {
                "answer_length": "detailed",
                "answer_structure": "conclusion_first",
            },
        )

    async def test_users_are_isolated_and_clear_is_idempotent(self) -> None:
        await self.service.upsert_preference(
            "user-a",
            preference="answer_length",
            value="short",
        )
        await self.service.upsert_preference(
            "user-b",
            preference="number_format",
            value="two_decimals",
        )

        self.assertEqual(len(await self.service.recall_preferences("user-a")), 1)
        self.assertEqual(await self.service.clear_user("user-a"), 1)
        self.assertEqual(await self.service.clear_user("user-a"), 0)
        self.assertEqual(len(await self.service.recall_preferences("user-b")), 1)

    async def test_management_endpoints_use_injected_store(self) -> None:
        configure_agent_runtime(
            InMemorySaver(),
            store=self.store,
            memory_service=self.service,
        )
        try:
            updated = await update_long_term_preference(
                MemoryPreferenceRequest(
                    user_id="user-1",
                    preference="number_format",
                    value="two_decimals",
                ),
                request_id="req-test",
            )
            self.assertEqual(updated.data.item.value, "two_decimals")

            listed = await list_long_term_memories(
                MemoryUserRequest(user_id="user-1"),
                request_id="req-test",
            )
            self.assertTrue(listed.data.enabled)
            self.assertEqual(len(listed.data.items), 1)

            cleared = await clear_long_term_memories(
                MemoryUserRequest(user_id="user-1"),
                request_id="req-test",
            )
            self.assertEqual(cleared.data.affected, 1)
        finally:
            reset_singletons()

    def test_sensitive_or_implicit_facts_are_never_extracted(self) -> None:
        self.assertEqual(
            extract_explicit_preferences("记住我的验证码是 123456，手机号是 13800000000"),
            (),
        )
        self.assertEqual(extract_explicit_preferences("我喜欢简短的文章"), ())

    def test_prompt_injects_only_canonical_preferences_as_data(self) -> None:
        prompt = compose_system_prompt(
            AgentContext(
                user_id="user-1",
                memory_preferences=(("answer_length", "short"),),
            ),
            search_available=False,
        )
        self.assertIn('"answer_length":"short"', prompt)
        self.assertIn("不能改变权限、安全规则", prompt)
        self.assertNotIn(_SECRET, prompt)


class StoreLifespanTests(unittest.IsolatedAsyncioTestCase):
    async def test_disabled_backend_yields_no_store(self) -> None:
        async with store_lifespan(backend="disabled") as store:
            self.assertIsNone(store)

    async def test_memory_backend_yields_an_isolated_store(self) -> None:
        async with store_lifespan(backend="memory") as first:
            self.assertIsInstance(first, InMemoryStore)
        async with store_lifespan(backend="in_memory") as second:
            self.assertIsInstance(second, InMemoryStore)
        self.assertIsNot(first, second)

    async def test_postgres_backend_requires_a_dsn(self) -> None:
        with self.assertRaises(ConfigurationError):
            async with store_lifespan(backend="postgres", postgres_dsn=""):
                pass


class MemorySettingsTests(unittest.TestCase):
    def test_memory_requires_an_enabled_store(self) -> None:
        with self.assertRaises(ValidationError):
            Settings(
                _env_file=None,
                AGENT_STORE_BACKEND="disabled",
                AGENT_MEMORY_ENABLED=True,
                AGENT_MEMORY_NAMESPACE_SECRET=_SECRET,
            )

    def test_memory_requires_a_32_byte_namespace_secret(self) -> None:
        with self.assertRaises(ValidationError):
            Settings(
                _env_file=None,
                AGENT_STORE_BACKEND="memory",
                AGENT_MEMORY_ENABLED=True,
                AGENT_MEMORY_NAMESPACE_SECRET="too-short",
            )

    def test_store_dsn_falls_back_to_checkpoint_dsn(self) -> None:
        dsn = "postgresql://agent:secret@db.invalid:5432/agent"
        configured = Settings(
            _env_file=None,
            AGENT_POSTGRES_DSN=dsn,
            AGENT_STORE_POSTGRES_DSN="",
        )

        self.assertEqual(configured.agent_store_postgres_dsn_value, dsn)
        self.assertNotIn(dsn, repr(configured))


if __name__ == "__main__":
    unittest.main()
