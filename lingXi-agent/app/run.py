"""跨平台 Agent 服务启动入口。"""

from __future__ import annotations

import asyncio
import os


def configure_event_loop() -> None:
    """在 Windows 上为 psycopg 异步连接选择兼容的事件循环。"""

    if os.name == "nt":
        asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())


def main() -> None:
    """在配置事件循环后启动 Uvicorn。"""

    configure_event_loop()

    import uvicorn

    from app.config.settings import settings

    uvicorn.run(
        "app.main:app",
        host=settings.host,
        port=settings.port,
        reload=settings.debug,
        log_level="info",
    )


if __name__ == "__main__":
    main()
