"""
Web search tool wrapper.

Encapsulates Tavily Search as a standard LangChain Tool.
Provides a clean extension point for swapping in alternative
search providers (e.g. Coze platform search plugin).
"""

from __future__ import annotations

from typing import Any

from langchain_core.tools import BaseTool

from app.config.settings import settings
from app.utils.logger import logger


# ── Tavily Search Tool ──────────────────────────────────────────────────────

def create_tavily_search_tool() -> BaseTool:
    """Create a Tavily web search tool instance.

    Returns a ``TavilySearchResults`` tool configured with the API key
    and result count from application settings.

    Raises:
        ConfigurationError: If ``TAVILY_API_KEY`` is not set.
    """
    from langchain_community.tools.tavily_search import TavilySearchResults

    if not settings.tavily_api_key:
        logger.warning(
            "TAVILY_API_KEY is not configured. "
            "Web search will not be available until it is set."
        )

    return TavilySearchResults(
        max_results=settings.search_max_results,
        tavily_api_key=settings.tavily_api_key or None,
        name="web_search",
        description=(
            "Search the web for current information. "
            "Use this tool for factual questions, recent events, "
            "or any query that requires up-to-date information. "
            "Input should be a clear search query string."
        ),
    )


# ── Tool Registry ───────────────────────────────────────────────────────────

def get_default_tools() -> list[BaseTool]:
    """Return the default set of tools for the search agent.

    This is the single place to add/remove tools.  To swap in a
    different search provider, replace the implementation here.

    Returns:
        List of LangChain ``BaseTool`` instances.
    """
    tools: list[BaseTool] = []

    # Primary search tool
    try:
        search_tool = create_tavily_search_tool()
        tools.append(search_tool)
        logger.info("Web search tool (Tavily) initialized successfully")
    except Exception as exc:
        logger.error("Failed to initialize Tavily search tool: %s", exc)

    return tools


# ── Extension Point ─────────────────────────────────────────────────────────

def create_coze_search_tool() -> BaseTool:
    """Placeholder for Coze platform web search plugin integration.

    To use Coze's official search plugin instead of Tavily:
    1. Obtain the Coze plugin endpoint and auth credentials
    2. Implement the search logic using ``@tool`` decorator
    3. Replace ``create_tavily_search_tool()`` in ``get_default_tools()``

    Example::

        from langchain_core.tools import tool

        @tool
        def coze_web_search(query: str) -> str:
            \"\"\"Search the web using Coze platform plugin.\"\"\"
            # Call Coze plugin API here
            ...
    """
    raise NotImplementedError(
        "Coze search plugin integration is not yet implemented. "
        "Use Tavily search or implement this function."
    )
