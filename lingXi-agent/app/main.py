"""
FastAPI application entry point.

Registers routes, global exception handlers, middleware, and
manages the application lifecycle (startup/shutdown).
"""

from __future__ import annotations

import time
from contextlib import asynccontextmanager
from typing import AsyncGenerator

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware

from app.api.v1.chat import router as chat_router
from app.api.v1.extract import router as extract_router
from app.api.v1.video import router as video_router
from app.config.settings import settings
from app.schemas.response import HealthData, HealthResponse
from app.utils.exceptions import (
    AgentError,
    agent_error_handler,
    generic_error_handler,
    validation_exception_handler,
)
from app.utils.logger import logger


# ── Lifespan ────────────────────────────────────────────────────────────────

@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncGenerator[None, None]:
    """Application lifespan handler — startup and shutdown logic."""
    # ── Startup ──
    logger.info(
        "Starting LangChain Search Agent | model=%s | port=%d | debug=%s",
        settings.model_name,
        settings.port,
        settings.debug,
    )

    # Validate critical configuration
    if not settings.openai_api_key:
        logger.warning(
            "OPENAI_API_KEY is not set. LLM-dependent endpoints will fail "
            "until a valid key is configured."
        )
    if not settings.tavily_api_key:
        logger.warning(
            "TAVILY_API_KEY is not set. Web search will be unavailable "
            "until a valid key is configured."
        )

    yield

    # ── Shutdown ──
    from app.api.dependencies import reset_singletons
    reset_singletons()
    logger.info("Application shutdown complete")


# ── Application Factory ─────────────────────────────────────────────────────

app = FastAPI(
    title="LangChain Search Agent",
    description=(
        "A production-ready REST API service powered by LangChain's new Agent "
        "architecture. Provides web-search-augmented AI chat with dynamic prompts, "
        "custom state management, structured output, and real-time SSE streaming."
    ),
    version="1.0.0",
    lifespan=lifespan,
    docs_url="/docs",
    redoc_url="/redoc",
)


# ── Middleware ───────────────────────────────────────────────────────────────

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.middleware("http")
async def request_logging_middleware(request: Request, call_next):  # type: ignore
    """Log every request with timing information."""
    start = time.time()
    response = await call_next(request)
    elapsed = time.time() - start

    logger.info(
        "%s %s | status=%d | elapsed=%.3fs",
        request.method,
        request.url.path,
        response.status_code,
        elapsed,
    )
    return response


# ── Exception Handlers ──────────────────────────────────────────────────────

app.add_exception_handler(AgentError, agent_error_handler)  # type: ignore
app.add_exception_handler(Exception, generic_error_handler)  # type: ignore
app.add_exception_handler(RequestValidationError, validation_exception_handler)  # type: ignore


# ── Routes ──────────────────────────────────────────────────────────────────

app.include_router(chat_router)
app.include_router(extract_router)
app.include_router(video_router)


@app.get("/health", response_model=HealthResponse, tags=["system"])
async def health_check() -> HealthResponse:
    """Service health check endpoint.

    Returns the current service status, model configuration,
    and search tool availability.
    """
    return HealthResponse(
        success=True,
        message="ok",
        data=HealthData(
            status="running",
            version="1.0.0",
            model=settings.model_name,
            search_tool="tavily" if settings.tavily_api_key else "unconfigured",
        ),
    )


# ── CLI Entry Point ─────────────────────────────────────────────────────────

if __name__ == "__main__":
    import uvicorn

    uvicorn.run(
        "app.main:app",
        host=settings.host,
        port=settings.port,
        reload=settings.debug,
        log_level="info",
    )
