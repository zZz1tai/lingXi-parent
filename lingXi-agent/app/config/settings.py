"""
应用配置管理模块。

从环境变量和 .env 文件加载基础设施配置。
模型凭据和模型名称由 Java 服务在每次请求时提供。
"""

from __future__ import annotations

import os
from pathlib import Path
from typing import Literal

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
    openai_api_base: str | None = Field(
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
    tavily_trust_env: bool = Field(
        default=False,
        validation_alias="TAVILY_TRUST_ENV",
        description="Allow Tavily to inherit process and desktop proxy settings",
    )
    tavily_https_proxy: SecretStr = Field(
        default=SecretStr(""),
        validation_alias="TAVILY_HTTPS_PROXY",
        description="Explicit HTTPS proxy used only for Tavily requests",
    )
    search_max_results: int = Field(
        default=5,
        ge=1,
        le=20,
        description="Maximum number of search results per query",
    )
    weather_enabled: bool = Field(
        default=True,
        validation_alias="WEATHER_ENABLED",
        description="Expose the fixed-destination Open-Meteo weather tool",
    )
    weather_max_response_bytes: int = Field(
        default=128 * 1024,
        validation_alias="WEATHER_MAX_RESPONSE_BYTES",
        ge=16 * 1024,
        le=1024 * 1024,
        description="Maximum response bytes accepted from each weather endpoint",
    )

    # ── 内部知识检索配置 ───────────────────────────────────────────
    knowledge_backend: Literal["disabled", "jsonl"] = Field(
        default="disabled",
        validation_alias="KNOWLEDGE_BACKEND",
        description="Internal knowledge backend; disabled unless explicitly enabled",
    )
    knowledge_index_path: Path | None = Field(
        default=None,
        validation_alias="KNOWLEDGE_INDEX_PATH",
        description="UTF-8 JSONL knowledge index path for the jsonl backend",
    )
    knowledge_top_k: int = Field(default=8, ge=1, le=20)
    knowledge_rerank_top_n: int = Field(default=5, ge=1, le=8)
    knowledge_model_chunk_chars: int = Field(default=2_400, ge=200, le=8_000)
    knowledge_max_index_bytes: int = Field(
        default=20 * 1024 * 1024,
        ge=1_024,
        le=512 * 1024 * 1024,
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
    agent_tools_enabled: bool = Field(
        default=False,
        validation_alias="AGENT_TOOLS_ENABLED",
        description="Expose Java-backed business tools to the Agent",
    )
    agent_write_actions_enabled: bool = Field(
        default=False,
        validation_alias="AGENT_WRITE_ACTIONS_ENABLED",
        description="Expose human-confirmed low-risk maintenance task proposals",
    )
    agent_tool_base_url: str = Field(
        default="http://localhost:8080",
        validation_alias="AGENT_TOOL_BASE_URL",
        min_length=8,
        max_length=2048,
    )
    agent_tool_allowed_hosts: str = Field(
        default="localhost,127.0.0.1",
        validation_alias="AGENT_TOOL_ALLOWED_HOSTS",
        description="Exact comma-separated destinations for the fixed Java Tool Gateway",
    )
    agent_tool_allow_insecure_http: bool = Field(
        default=True,
        validation_alias="AGENT_TOOL_ALLOW_INSECURE_HTTP",
        description="Allow HTTP only for an explicitly allowlisted internal development gateway",
    )
    agent_tool_timeout_seconds: float = Field(
        default=20.0,
        validation_alias="AGENT_TOOL_TIMEOUT_SECONDS",
        gt=0,
        le=60,
    )
    agent_tool_max_response_bytes: int = Field(
        default=256 * 1024,
        validation_alias="AGENT_TOOL_MAX_RESPONSE_BYTES",
        ge=1024,
        le=2 * 1024 * 1024,
    )
    agent_tool_model_text_chars: int = Field(
        default=12_000,
        validation_alias="AGENT_TOOL_MODEL_TEXT_CHARS",
        ge=1_000,
        le=16_000,
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

    # ── LangGraph 长期 Store 与记忆 ───────────────────────────────
    agent_store_backend: Literal["disabled", "memory", "postgres"] = Field(
        default="disabled",
        validation_alias="AGENT_STORE_BACKEND",
        description="Cross-thread Store backend",
    )
    agent_store_postgres_dsn: SecretStr = Field(
        default=SecretStr(""),
        validation_alias="AGENT_STORE_POSTGRES_DSN",
        description="PostgreSQL DSN used by the long-term Store",
    )
    agent_memory_enabled: bool = Field(
        default=False,
        validation_alias="AGENT_MEMORY_ENABLED",
    )
    agent_memory_namespace_secret: SecretStr = Field(
        default=SecretStr(""),
        validation_alias="AGENT_MEMORY_NAMESPACE_SECRET",
        description="HMAC secret used to pseudonymize Store user namespaces",
    )
    agent_memory_max_recall: int = Field(
        default=5,
        validation_alias="AGENT_MEMORY_MAX_RECALL",
        ge=1,
        le=20,
    )
    agent_memory_write_confidence: float = Field(
        default=0.9,
        validation_alias="AGENT_MEMORY_WRITE_CONFIDENCE",
        ge=0.0,
        le=1.0,
    )

    # ── 可观测性（Langfuse）配置 ─────────────────────────────────
    langfuse_enabled: bool = Field(
        default=False,
        validation_alias="LANGFUSE_ENABLED",
        description="Send LLM traces to Langfuse for observability",
    )
    langfuse_public_key: SecretStr = Field(
        default=SecretStr(""),
        validation_alias="LANGFUSE_PUBLIC_KEY",
        description="Langfuse public (pk-) key",
    )
    langfuse_secret_key: SecretStr = Field(
        default=SecretStr(""),
        validation_alias="LANGFUSE_SECRET_KEY",
        description="Langfuse secret (sk-) key",
    )
    langfuse_host: str = Field(
        default="https://cloud.langfuse.com",
        validation_alias="LANGFUSE_HOST",
        description="Langfuse server URL (cloud or self-hosted)",
    )
    langfuse_environment: str = Field(
        default="development",
        validation_alias="LANGFUSE_ENVIRONMENT",
        description="Environment label attached to traces (production/staging/development)",
    )
    langfuse_debug: bool = Field(
        default=False,
        validation_alias="LANGFUSE_DEBUG",
        description="Enable Langfuse SDK debug logging",
    )

    # ── 服务端配置 ─────────────────────────────────────────────────
    host: str = Field(default="127.0.0.1", description="Server bind host")
    port: int = Field(default=5000, description="Server bind port")
    debug: bool = Field(default=False, description="Enable debug mode")

    @model_validator(mode="before")
    @classmethod
    def _read_port_from_env(cls, values: dict) -> dict:
        """从沙箱环境变量中读取 DEPLOY_RUN_PORT（如果存在）。"""
        deploy_port = os.environ.get("DEPLOY_RUN_PORT")
        if deploy_port and "port" not in values:
            values["port"] = int(deploy_port)
        return values

    @model_validator(mode="after")
    def _validate_knowledge_limits(self) -> Settings:
        if self.knowledge_rerank_top_n > self.knowledge_top_k:
            raise ValueError("KNOWLEDGE_RERANK_TOP_N must not exceed KNOWLEDGE_TOP_K")
        if self.agent_memory_enabled:
            if self.agent_store_backend == "disabled":
                raise ValueError(
                    "AGENT_STORE_BACKEND must be enabled when AGENT_MEMORY_ENABLED=true"
                )
            if len(self.agent_memory_namespace_secret_value.encode("utf-8")) < 32:
                raise ValueError(
                    "AGENT_MEMORY_NAMESPACE_SECRET must contain at least 32 bytes"
                )
        if self.agent_write_actions_enabled and not self.agent_tools_enabled:
            raise ValueError(
                "AGENT_TOOLS_ENABLED must be true when AGENT_WRITE_ACTIONS_ENABLED=true"
            )
        return self

    @property
    def service_api_key_value(self) -> str:
        """返回服务共享密钥，不会在 repr/日志中暴露。"""
        return self.service_api_key.get_secret_value().strip()

    @property
    def tavily_https_proxy_value(self) -> str:
        """返回 Tavily 显式代理；空值表示直连。"""
        return self.tavily_https_proxy.get_secret_value().strip()

    @property
    def agent_postgres_dsn_value(self) -> str:
        """仅在资源构造边界返回检查点 DSN。"""
        return self.agent_postgres_dsn.get_secret_value()

    @property
    def agent_store_postgres_dsn_value(self) -> str:
        """返回长期 Store DSN；未单独配置时复用 checkpoint DSN。"""
        configured = self.agent_store_postgres_dsn.get_secret_value().strip()
        return configured or self.agent_postgres_dsn_value

    @property
    def agent_memory_namespace_secret_value(self) -> str:
        """仅在记忆服务构造边界返回命名空间 HMAC 密钥。"""
        return self.agent_memory_namespace_secret.get_secret_value()

    @property
    def langfuse_public_key_value(self) -> str:
        """返回 Langfuse 公钥；不会出现在 repr/日志中。"""
        return self.langfuse_public_key.get_secret_value().strip()

    @property
    def langfuse_secret_key_value(self) -> str:
        """返回 Langfuse 私钥；不会出现在 repr/日志中。"""
        return self.langfuse_secret_key.get_secret_value().strip()

    @property
    def outbound_host_allowlist(self) -> set[str]:
        """标准化的提供商主机/权限白名单。"""
        return {
            item.strip().lower().rstrip(".")
            for item in self.outbound_allowed_hosts.split(",")
            if item.strip()
        }

    @property
    def agent_tool_host_allowlist(self) -> set[str]:
        """标准化的 Java Tool Gateway 精确目的地主机白名单。"""
        return {
            item.strip().lower().rstrip(".")
            for item in self.agent_tool_allowed_hosts.split(",")
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
