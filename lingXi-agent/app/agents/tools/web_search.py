"""Bounded Tavily search exposed as a LangChain v1 runtime-aware tool."""

from __future__ import annotations

import asyncio
import json
from typing import Any

from langchain.tools import ToolRuntime, tool
from langchain_core.tools import BaseTool
from pydantic import BaseModel, Field

from app.config.settings import settings
from app.utils.exceptions import ConfigurationError
from app.utils.logger import logger


class WebSearchInput(BaseModel):
    """Public, model-visible search arguments."""

    query: str = Field(
        ...,
        min_length=2,
        max_length=500,
        description="A concise public-web search query without secrets or internal data",
    )


def _normalized_results(payload: dict[str, Any]) -> list[dict[str, str]]:
    results: list[dict[str, str]] = []
    raw_results = payload.get("results")
    if not isinstance(raw_results, list):
        return results

    for item in raw_results[: settings.search_max_results]:
        if not isinstance(item, dict):
            continue
        results.append(
            {
                "title": str(item.get("title") or "")[:300],
                "url": str(item.get("url") or "")[:2_000],
                "content": str(item.get("content") or "")[:1_500],
            }
        )
    return results


def create_tavily_search_tool() -> BaseTool:
    """Create a timeout-bounded Tavily tool with custom progress streaming."""

    if not settings.tavily_api_key:
        raise ConfigurationError("TAVILY_API_KEY is not configured")

    @tool(
        "web_search",
        args_schema=WebSearchInput,
        response_format="content_and_artifact",
        description=(
            "Search public web sources for recent or externally verifiable facts. "
            "Never include credentials, customer data, or internal business details "
            "in the query."
        ),
    )
    async def web_search(
        query: str,
        runtime: ToolRuntime,
    ) -> tuple[str, dict[str, Any]]:
        from tavily import AsyncTavilyClient

        runtime.stream_writer(
            {"type": "tool_progress", "tool": "web_search", "status": "started"}
        )
        client = AsyncTavilyClient(api_key=settings.tavily_api_key)
        try:
            async with asyncio.timeout(settings.tool_timeout):
                payload = await client.search(
                    query=query,
                    max_results=settings.search_max_results,
                    search_depth="basic",
                    include_answer=False,
                    include_raw_content=False,
                    timeout=float(settings.tool_timeout),
                )
        finally:
            await client.close()

        results = _normalized_results(payload)
        runtime.stream_writer(
            {
                "type": "tool_progress",
                "tool": "web_search",
                "status": "completed",
                "result_count": len(results),
            }
        )

        model_content = json.dumps(
            {"query": query, "results": results},
            ensure_ascii=False,
            separators=(",", ":"),
        )
        artifact = {
            "provider": "tavily",
            "query": query,
            "results": results,
        }
        return model_content, artifact

    return web_search


def get_default_tools() -> list[BaseTool]:
    """Return configured tools, explicitly degrading when search is disabled."""

    if not settings.tavily_api_key:
        logger.warning("TAVILY_API_KEY is not configured; search tool disabled")
        return []

    try:
        search_tool = create_tavily_search_tool()
    except Exception as exc:
        logger.error(
            "Failed to initialize Tavily search tool | error_type=%s",
            type(exc).__name__,
        )
        return []

    logger.info("Web search tool initialized | provider=tavily")
    return [search_tool]
