"""面向 Agent 的权限感知内部知识检索工具。"""

from __future__ import annotations

import asyncio
import json
from typing import Any

from langchain.tools import ToolRuntime, tool
from langchain_core.tools import BaseTool
from pydantic import BaseModel, ConfigDict, Field, field_validator

from app.agents.state import AgentContext
from app.config.settings import settings
from app.services.knowledge import KnowledgeRetriever


class KnowledgeSearchInput(BaseModel):
    """模型可见的检索条件；调用者角色不在参数中。"""

    model_config = ConfigDict(
        arbitrary_types_allowed=True,
        extra="forbid",
        str_strip_whitespace=True,
    )

    query: str = Field(
        ...,
        min_length=2,
        max_length=500,
        description="Concise keywords for internal SOP, manual, fault-code, or system help",
    )
    document_type: str | None = Field(default=None, min_length=1, max_length=64)
    product_model: str | None = Field(default=None, min_length=1, max_length=128)
    # 保留 ToolNode 注入值；ToolRuntime 是直接注入类型，不会暴露给模型。
    runtime: ToolRuntime[AgentContext]

    @field_validator("query", "document_type", "product_model")
    @classmethod
    def reject_control_characters(cls, value: str | None) -> str | None:
        if value is not None and any(char in value for char in ("\r", "\n", "\x00")):
            raise ValueError("knowledge search values must be single-line text")
        return value


def create_knowledge_search_tool(retriever: KnowledgeRetriever) -> BaseTool:
    """创建只从可信运行时读取角色范围的知识检索工具。"""

    @tool(
        "search_knowledge",
        args_schema=KnowledgeSearchInput,
        response_format="content_and_artifact",
        description=(
            "Search current internal manuals, SOPs, fault codes, and system help. "
            "Use this before public web search for LingXi operational questions. "
            "Access scope is enforced from trusted runtime context; never invent sources."
        ),
    )
    async def search_knowledge(
        query: str,
        runtime: ToolRuntime[AgentContext],
        document_type: str | None = None,
        product_model: str | None = None,
    ) -> tuple[str, dict[str, Any]]:
        context = runtime.context
        role_code = context.role_code if context is not None else ""
        runtime.stream_writer(
            {
                "type": "tool_progress",
                "tool": "search_knowledge",
                "status": "started",
            }
        )
        async with asyncio.timeout(settings.tool_timeout):
            candidates = await retriever.search(
                query,
                role_code=role_code,
                document_type=document_type,
                product_model=product_model,
                top_k=settings.knowledge_top_k,
            )
        results = candidates[: settings.knowledge_rerank_top_n]

        model_results: list[dict[str, object]] = []
        citations: list[dict[str, object]] = []
        for result in results:
            model_payload = result.model_payload()
            model_payload["content"] = str(model_payload["content"])[
                : settings.knowledge_model_chunk_chars
            ]
            model_results.append(model_payload)
            citation = result.citation_payload()
            citations.append(citation)
            runtime.stream_writer(
                {
                    "type": "citation",
                    "tool": "search_knowledge",
                    "citation": citation,
                }
            )

        runtime.stream_writer(
            {
                "type": "tool_progress",
                "tool": "search_knowledge",
                "status": "completed",
                "result_count": len(results),
            }
        )
        model_content = json.dumps(
            {"query": query, "results": model_results},
            ensure_ascii=False,
            separators=(",", ":"),
        )
        artifact = {
            "provider": "internal_knowledge",
            "query": query,
            "result_count": len(results),
            "citations": citations,
        }
        return model_content, artifact

    return search_knowledge
