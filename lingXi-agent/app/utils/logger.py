"""
统一日志配置模块。

提供一致的日志格式，包含时间戳、日志级别、
模块名称以及可选的请求 ID 用于链路追踪。
"""

from __future__ import annotations

import logging
import sys
from contextvars import ContextVar, Token
from uuid import uuid4


_LOG_FORMAT = "%(asctime)s | %(levelname)-8s | %(name)s | [%(request_id)s] %(message)s"
_DATE_FORMAT = "%Y-%m-%d %H:%M:%S"
_request_id_context: ContextVar[str] = ContextVar("request_id", default="-")


class _RequestIdFilter(logging.Filter):
    """向每条日志记录注入 request_id。

    如果记录已经携带了 ``request_id`` 属性，则保留原值；
    否则使用默认的 ``"-"`` 占位符，确保格式字符串
    不会抛出 ``KeyError``。
    """

    def filter(self, record: logging.LogRecord) -> bool:  # noqa: A003
        if not hasattr(record, "request_id") or record.request_id == "-":  # type: ignore[attr-defined]
            record.request_id = _request_id_context.get()  # type: ignore[attr-defined]
        return True


def setup_logger(name: str = "agent_service") -> logging.Logger:
    """创建并配置应用日志记录器。

    返回一个带有单一 stdout 处理程序和标准格式的日志记录器。
    多次使用相同 *name* 调用此函数是安全的——
    现有的处理程序将被复用。
    """
    logger = logging.getLogger(name)

    if not logger.handlers:
        handler = logging.StreamHandler(sys.stdout)
        handler.setFormatter(logging.Formatter(_LOG_FORMAT, datefmt=_DATE_FORMAT))
        handler.addFilter(_RequestIdFilter())
        logger.addHandler(handler)
        logger.setLevel(logging.INFO)

    logger.propagate = False
    return logger


# 模块级日志实例，业务代码可直接导入使用。
logger = setup_logger()


def generate_request_id() -> str:
    """生成用于日志追踪的短唯一请求标识符。"""
    return uuid4().hex[:12]


def set_request_id(request_id: str) -> Token[str]:
    """将请求 ID 绑定到当前异步执行上下文。"""
    return _request_id_context.set(request_id)


def reset_request_id(token: Token[str]) -> None:
    """请求完成后恢复请求 ID 上下文。"""
    _request_id_context.reset(token)


def get_request_id() -> str:
    """返回绑定到当前执行上下文的请求 ID。"""
    return _request_id_context.get()


def bind_request_id(log_record: logging.LogRecord, request_id: str) -> None:
    """向后兼容的辅助函数，用于显式构造日志记录。"""
    log_record.request_id = request_id  # type: ignore[attr-defined]
