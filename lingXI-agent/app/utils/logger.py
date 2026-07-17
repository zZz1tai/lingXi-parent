"""
Unified logging configuration.

Provides a consistent log format with timestamps, log levels,
module names, and optional request IDs for tracing.
"""

from __future__ import annotations

import logging
import sys
from uuid import uuid4


_LOG_FORMAT = "%(asctime)s | %(levelname)-8s | %(name)s | [%(request_id)s] %(message)s"
_DATE_FORMAT = "%Y-%m-%d %H:%M:%S"


class _RequestIdFilter(logging.Filter):
    """Injects a request_id into every log record.

    If the record already carries a ``request_id`` attribute it is kept;
    otherwise a default ``"-"`` placeholder is used so the format string
    never raises ``KeyError``.
    """

    def filter(self, record: logging.LogRecord) -> bool:  # noqa: A003
        if not hasattr(record, "request_id"):
            record.request_id = "-"  # type: ignore[attr-defined]
        return True


def setup_logger(name: str = "agent_service") -> logging.Logger:
    """Create and configure the application logger.

    Returns a logger with a single stdout handler and the standard
    format.  Calling this function multiple times with the same *name*
    is safe — existing handlers are reused.
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


# Module-level logger instance — import and use directly.
logger = setup_logger()


def generate_request_id() -> str:
    """Generate a short unique request identifier for log tracing."""
    return uuid4().hex[:12]


def bind_request_id(log_record: logging.LogRecord, request_id: str) -> None:
    """Attach a request ID to a log record (convenience helper)."""
    log_record.request_id = request_id  # type: ignore[attr-defined]
