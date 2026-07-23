"""
应用配置管理模块。

从环境变量和 .env 文件加载基础设施配置。
模型凭据和模型名称由 Java 服务在每次请求时提供。
"""

from __future__ import annotations

import os
from pathlib import Path
from typing import Literal, Optional

from pydantic import AliasChoices, Field, SecretStr, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


PROJECT_ROOT = Path(__file__).resolve().parents[2]
ENV_FILE = PROJECT_ROOT / ".env"


class Settings(BaseSettings):
    """全局应用配置，从环境变量加载。"""

    model_config = SettingsConfigDict(
        env_file=str(ENV_FILE),
        env_file_encoding="utf-8",
        extra="ignore",
        case_sensitive=False,
    )

    # ── 大模型配置 ─────────────────────────────────────────────────
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
        default="runtime-configured",
        description="Diagnostic placeholder; Java requests provide the runtime model",
    )
    temperature: float = Field(
        default=0.7,
        ge=0.0,
        le=2.0,
        description="LLM sampling temperature",
    )

    # ── 搜索配置 ───────────────────────────────────────────────────
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

    # ── Agent 配置 ─────────────────────────────────────────────────
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

    # ── 服务安全配置 ───────────────────────────────────────────────
    service_api_key: SecretStr = Field(
        default=SecretStr(""),
        validation_alias=AliasChoices("AGENT_SERVICE_API_KEY", "SERVICE_API_KEY"),
        description=(
            "Shared secret required in X-Agent-Service-Key for all non-health "
            "API routes. The service fails closed when it is not configured."
        ),
    )
    outbound_allowed_hosts: str = Field(
        default=(
            "api.openai.com,dashscope.aliyuncs.com,"
            "*.cn-beijing.maas.aliyuncs.com"
        ),
        description="Comma-separated allowlist for provider HTTP destinations",
    )
    allow_insecure_outbound_http: bool = Field(
        default=False,
        description="Allow HTTP provider URLs for explicitly allowlisted local development hosts",
    )
    cors_allowed_origins: str = Field(
        default="",
        description="Comma-separated browser origins; empty disables CORS",
    )
    docs_enabled: bool = Field(
        default=False,
        description="Expose Swagger and ReDoc endpoints",
    )

    # ── 资源限制 ───────────────────────────────────────────────────
    max_request_body_bytes: int = Field(
        default=2 * 1024 * 1024,
        ge=1024,
        le=20 * 1024 * 1024,
        description="Hard ASGI request-body limit",
    )
    max_concurrent_requests: int = Field(
        default=32,
        ge=1,
        le=1024,
        description="Maximum concurrent in-flight HTTP requests per worker",
    )
    request_queue_timeout_seconds: float = Field(
        default=5.0,
        gt=0,
        le=60,
        description="Maximum time a request may wait for an in-process concurrency slot",
    )
    provider_http_max_connections: int = Field(default=64, ge=1, le=1024)
    provider_http_max_keepalive_connections: int = Field(default=16, ge=0, le=1024)
    provider_http_timeout_seconds: float = Field(default=60.0, gt=0, le=1800)
    agent_stream_max_seconds: float = Field(default=300.0, gt=0, le=1800)
    agent_stream_max_text_chars: int = Field(
        default=200_000,
        ge=1_000,
        le=2_000_000,
    )

    # ── LangGraph 检查点配置 ───────────────────────────────────────
    agent_checkpointer_backend: Literal["memory", "postgres"] = Field(
        default="memory",
        validation_alias="AGENT_CHECKPOINTER_BACKEND",
        description="Short-term memory backend",
    )
    agent_postgres_dsn: SecretStr = Field(
        default=SecretStr(""),
        validation_alias="AGENT_POSTGRES_DSN",
        description="PostgreSQL DSN used by the durable checkpoint backend",
    )

    # ── 服务端配置 ─────────────────────────────────────────────────
    host: str = Field(default="127.0.0.1", description="Server bind host")
    port: int = Field(default=5000, description="Server bind port")
    debug: bool = Field(default=False, description="Enable debug mode")

    @model_validator(mode="before")
    @classmethod
    def _read_port_from_env(cls, values: dict) -> dict:  # noqa: N805
        """从沙箱环境变量中读取 DEPLOY_RUN_PORT（如果存在）。"""
        deploy_port = os.environ.get("DEPLOY_RUN_PORT")
        if deploy_port and "port" not in values:
            values["port"] = int(deploy_port)
        return values

    @property
    def service_api_key_value(self) -> str:
        """返回服务共享密钥，不会在 repr/日志中暴露。"""
        return self.service_api_key.get_secret_value().strip()

    @property
    def agent_postgres_dsn_value(self) -> str:
        """仅在资源构造边界返回检查点 DSN。"""
        return self.agent_postgres_dsn.get_secret_value()

    @property
    def outbound_host_allowlist(self) -> set[str]:
        """标准化的提供商主机/权限白名单。"""
        return {
            item.strip().lower().rstrip(".")
            for item in self.outbound_allowed_hosts.split(",")
            if item.strip()
        }

    @property
    def cors_origin_allowlist(self) -> list[str]:
        """标准化的显式 CORS 来源列表。"""
        return [
            item.strip().rstrip("/")
            for item in self.cors_allowed_origins.split(",")
            if item.strip()
        ]


# 全局单例配置实例
settings = Settings()
