"""Configuration integration coverage for durable Agent checkpoints."""

from __future__ import annotations

import hmac
import os
import tempfile
import unittest
from contextlib import asynccontextmanager
from pathlib import Path
from unittest.mock import AsyncMock, Mock, patch

from pydantic import SecretStr

import app.main as main_module
from app.agents.checkpoints import checkpointer_lifespan
from app.config.settings import Settings
from app.utils.exceptions import ConfigurationError


class CheckpointSettingsTests(unittest.TestCase):
    def test_dotenv_checkpoint_values_are_loaded_and_dsn_is_masked(self) -> None:
        dsn = "postgresql://agent:checkpoint-secret@db.invalid:5432/agent"
        with tempfile.TemporaryDirectory() as directory:
            env_file = Path(directory) / ".env"
            env_file.write_text(
                "AGENT_CHECKPOINTER_BACKEND=postgres\n"
                f"AGENT_POSTGRES_DSN={dsn}\n",
                encoding="utf-8",
            )
            # Pydantic Settings 规定进程环境优先于 dotenv；测试必须隔离开发机
            # 可能存在的真实 checkpoint 配置，避免失败断言打印真实 DSN。
            with patch.dict(os.environ, {}, clear=True):
                configured = Settings(_env_file=env_file)

        self.assertEqual(configured.agent_checkpointer_backend, "postgres")
        self.assertTrue(
            hmac.compare_digest(configured.agent_postgres_dsn_value, dsn)
        )
        self.assertNotIn(dsn, repr(configured))


class CheckpointLifespanIntegrationTests(unittest.IsolatedAsyncioTestCase):
    async def test_injected_backend_requires_a_dsn_for_postgres(self) -> None:
        with self.assertRaises(ConfigurationError):
            async with checkpointer_lifespan(
                backend="postgres",
                postgres_dsn="",
            ):
                pass

    async def test_injected_memory_backend_does_not_require_a_dsn(self) -> None:
        async with checkpointer_lifespan(
            backend="memory",
            postgres_dsn="",
        ) as saver:
            self.assertIsNotNone(saver)

    async def test_lifespan_passes_settings_to_checkpointer_factory(self) -> None:
        dsn = "postgresql://agent:runtime-secret@db.invalid:5432/agent"
        captured: dict[str, object] = {}
        saver = object()

        @asynccontextmanager
        async def fake_checkpointer_lifespan(**kwargs):
            captured.update(kwargs)
            yield saver

        with (
            patch.object(
                main_module.settings,
                "agent_checkpointer_backend",
                "postgres",
            ),
            patch.object(
                main_module.settings,
                "agent_postgres_dsn",
                SecretStr(dsn),
            ),
            patch.object(main_module.settings, "knowledge_backend", "disabled"),
            patch.object(main_module.settings, "agent_store_backend", "disabled"),
            patch.object(main_module.settings, "agent_memory_enabled", False),
            patch.object(main_module.settings, "agent_tools_enabled", False),
            patch.object(
                main_module,
                "checkpointer_lifespan",
                fake_checkpointer_lifespan,
            ),
            patch.object(main_module, "initialize_http_client", AsyncMock()),
            patch.object(main_module, "close_http_client", AsyncMock()),
            patch.object(main_module, "configure_agent_runtime", Mock()) as configure,
            patch.object(main_module, "reset_singletons", Mock()),
        ):
            async with main_module.lifespan(main_module.app):
                pass

        self.assertEqual(captured["backend"], "postgres")
        self.assertEqual(captured["postgres_dsn"], dsn)
        configure.assert_called_once_with(
            saver,
            store=None,
            knowledge_retriever=None,
            agent_tool_client=None,
            memory_service=None,
        )


if __name__ == "__main__":
    unittest.main()
