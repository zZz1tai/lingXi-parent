"""
Application configuration management.

Loads settings from environment variables and .env file.
All sensitive values (API keys, model names) are managed here.
"""

from __future__ import annotations

import os
from pathlib import Path
from typing import Optional

from pydantic import Field, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


PROJECT_ROOT = Path(__file__).resolve().parents[2]
ENV_FILE = PROJECT_ROOT / ".env"


class Settings(BaseSettings):
    """Global application settings loaded from environment variables."""

    model_config = SettingsConfigDict(
        env_file=str(ENV_FILE),
        env_file_encoding="utf-8",
        extra="ignore",
        case_sensitive=False,
    )

    # ── LLM Configuration ──────────────────────────────────────────
    llm_provider: str = Field(
        default="openai",
        description="LLM provider type: openai / azure / custom",
    )
    openai_api_key: str = Field(
        default="",
        description="OpenAI API key (or compatible API key)",
    )
    openai_api_base: Optional[str] = Field(
        default=None,
        description="Custom API base URL for OpenAI-compatible endpoints",
    )
    model_name: str = Field(
        default="gpt-4o-mini",
        description="Model name to use for chat completions",
    )
    temperature: float = Field(
        default=0.7,
        ge=0.0,
        le=2.0,
        description="LLM sampling temperature",
    )

    # ── Search Configuration ───────────────────────────────────────
    tavily_api_key: str = Field(
        default="",
        description="Tavily Search API key",
    )
    search_max_results: int = Field(
        default=5,
        ge=1,
        le=20,
        description="Maximum number of search results per query",
    )

    # ── Agent Configuration ────────────────────────────────────────
    max_iterations: int = Field(
        default=5,
        ge=1,
        le=20,
        description="Maximum agent loop iterations (safety limit)",
    )
    tool_timeout: int = Field(
        default=30,
        ge=5,
        le=120,
        description="Timeout in seconds for individual tool calls",
    )

    # ── Server Configuration ───────────────────────────────────────
    host: str = Field(default="0.0.0.0", description="Server bind host")
    port: int = Field(default=5000, description="Server bind port")
    debug: bool = Field(default=False, description="Enable debug mode")

    @model_validator(mode="before")
    @classmethod
    def _read_port_from_env(cls, values: dict) -> dict:  # noqa: N805
        """Read DEPLOY_RUN_PORT from sandbox environment if available."""
        deploy_port = os.environ.get("DEPLOY_RUN_PORT")
        if deploy_port and "port" not in values:
            values["port"] = int(deploy_port)
        return values


# Singleton settings instance
settings = Settings()
