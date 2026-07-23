"""
FastAPI 应用入口点。

注册路由、全局异常处理器、中间件，
并管理应用生命周期（启动/关闭）。
"""

from __future__ import annotations

import time
import re
from contextlib import AsyncExitStack, asynccontextmanager
from typing import AsyncGenerator

from fastapi import Depends, FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from app.api.v1.chat import router as chat_router
from app.api.v1.extract import router as extract_router
from app.api.v1.video import router as video_router
from app.api.v1.chapter import router as chapter_router
from app.config.settings import settings
from app.api.auth import require_service_auth
from app.api.middleware import ResourceLimitMiddleware
from app.agents.checkpoints import checkpointer_lifespan
from app.api.dependencies import configure_agent_runtime, reset_singletons
from app.services.http_client import (
    close_http_client,
    http_client_ready,
    initialize_http_client,
)
from app.schemas.response import HealthData, HealthResponse
from app.utils.exceptions import (
    AgentError,
    agent_error_handler,
    generic_error_handler,
    validation_exception_handler,
)
from app.utils.logger import (
    generate_request_id,
    logger,
    reset_request_id,
    set_request_id,
)


# ── 应用生命周期 ────────────────────────────────────────────────────────────

@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncGenerator[None, None]:
    """应用生命周期处理器 — 启动和关闭逻辑。"""
    # ── 启动 ──
    logger.info(
        "Starting LangChain Search Agent | model=%s | port=%d | debug=%s",
        settings.model_name,
        settings.port,
        settings.debug,
    )

    # 启动阶段校验关键配置，避免服务在缺少安全参数时带病运行。
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

    if not settings.service_api_key_value:
        logger.error(
            "SERVICE_API_KEY is not set. Protected endpoints will fail closed."
        )

    async with AsyncExitStack() as stack:
        checkpointer = await stack.enter_async_context(
            checkpointer_lifespan(
                backend=settings.agent_checkpointer_backend,
                postgres_dsn=settings.agent_postgres_dsn_value,
            )
        )
        configure_agent_runtime(checkpointer)
        try:
            await initialize_http_client()
            stack.push_async_callback(close_http_client)
            yield
        finally:
            reset_singletons()
            logger.info("Application shutdown complete")


# ── 应用工厂 ────────────────────────────────────────────────────────────────

app = FastAPI(
    title="LangChain Search Agent",
    description=(
        "A production-ready REST API service powered by LangChain's new Agent "
        "architecture. Provides web-search-augmented AI chat with dynamic prompts, "
        "custom state management, structured output, and real-time SSE streaming."
    ),
    version="1.0.0",
    lifespan=lifespan,
    docs_url="/docs" if settings.docs_enabled else None,
    redoc_url="/redoc" if settings.docs_enabled else None,
)


# ── 中间件 ──────────────────────────────────────────────────────────────────

app.add_middleware(
    ResourceLimitMiddleware,
    max_body_bytes=settings.max_request_body_bytes,
    max_concurrent_requests=settings.max_concurrent_requests,
    queue_timeout_seconds=settings.request_queue_timeout_seconds,
)

if settings.cors_origin_allowlist:
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origin_allowlist,
        allow_credentials=True,
        allow_methods=["GET", "POST", "DELETE"],
        allow_headers=["Content-Type", "X-Agent-Service-Key", "X-Request-ID"],
    )


@app.middleware("http")
async def request_logging_middleware(request: Request, call_next):  # type: ignore
    """记录每个请求及耗时信息。"""
    supplied = request.headers.get("X-Request-ID", "")
    request_id = (
        supplied
        if re.fullmatch(r"[A-Za-z0-9._-]{1,128}", supplied)
        else generate_request_id()
    )
    token = set_request_id(request_id)
    start = time.perf_counter()
    try:
        response = await call_next(request)
        response.headers["X-Request-ID"] = request_id
        elapsed = time.perf_counter() - start
        logger.info(
            "%s %s | status=%d | elapsed=%.3fs",
            request.method,
            request.url.path,
            response.status_code,
            elapsed,
        )
        return response
    finally:
        reset_request_id(token)


# ── 异常处理器 ──────────────────────────────────────────────────────────────

app.add_exception_handler(AgentError, agent_error_handler)  # type: ignore
app.add_exception_handler(Exception, generic_error_handler)  # type: ignore
app.add_exception_handler(RequestValidationError, validation_exception_handler)  # type: ignore


# ── 路由注册 ────────────────────────────────────────────────────────────────

protected = [Depends(require_service_auth)]
app.include_router(chat_router, dependencies=protected)
app.include_router(extract_router, dependencies=protected)
app.include_router(video_router, dependencies=protected)
app.include_router(chapter_router, dependencies=protected)


@app.get("/health", response_model=HealthResponse, tags=["system"])
async def health_check() -> HealthResponse:
    """向后兼容的存活检查端点。"""
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


@app.get("/livez", response_model=HealthResponse, tags=["system"])
async def liveness_check() -> HealthResponse:
    """进程存活检查；不调用付费或远程提供商。"""
    return await health_check()


@app.get("/readyz", tags=["system"], response_model=None)
async def readiness_check():
    """配置和本地资源就绪检查，不调用提供商。"""
    missing: list[str] = []
    if not settings.service_api_key_value:
        missing.append("SERVICE_API_KEY")
    if not http_client_ready():
        missing.append("provider_http_client")
    if missing:
        return JSONResponse(
            status_code=503,
            content={
                "success": False,
                "error": {
                    "code": "NOT_READY",
                    "message": "Required service resources are not configured",
                },
                "missing": missing,
            },
        )
    return HealthResponse(
        success=True,
        message="ok",
        data=HealthData(
            status="ready",
            version="1.0.0",
            model=settings.model_name,
            search_tool="tavily" if settings.tavily_api_key else "unconfigured",
        ),
    )


# ── 命令行入口 ──────────────────────────────────────────────────────────────

if __name__ == "__main__":
    import uvicorn

    uvicorn.run(
        "app.main:app",
        host=settings.host,
        port=settings.port,
        reload=settings.debug,
        log_level="info",
    )
