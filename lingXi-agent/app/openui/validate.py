"""OpenUI Spec 白名单校验与清洗。

策略：
- 未知节点类型：丢弃该节点；
- 未知字段：丢弃；
- 文本超长：截断；
- URL 非白名单协议：丢弃该节点；
- 硬性超限（非数组结构、超过深度/节点数/字节数）：整个 Spec 判定失败，
  调用方降级为 Markdown。
"""

from __future__ import annotations

import json
from typing import Any

from app.openui.schema import (
    OPENUI_ALLOWED_TYPES,
    OPENUI_CARD_TONES,
    OPENUI_MAX_CARDS,
    OPENUI_MAX_COLUMNS,
    OPENUI_MAX_DEPTH,
    OPENUI_MAX_LABEL_CHARS,
    OPENUI_MAX_LABELS,
    OPENUI_MAX_MEDIA_URL_CHARS,
    OPENUI_MAX_NODES,
    OPENUI_MAX_NUMBER_ABS,
    OPENUI_MAX_ROWS,
    OPENUI_MAX_SERIES,
    OPENUI_MAX_SPEC_BYTES,
    OPENUI_MAX_TEXT_CHARS,
    OPENUI_MAX_TITLE_CHARS,
    OPENUI_SPEC_FIELDS,
)


def validate_spec(spec: Any) -> tuple[list[dict[str, Any]] | None, str | None]:
    """校验并清洗 Spec；返回 (清洗后的分节列表, 错误码|None)。"""
    if not isinstance(spec, list):
        return None, "OPENUI_BAD_STRUCTURE"
    try:
        encoded = json.dumps(
            spec, ensure_ascii=False, separators=(",", ":"), default=str
        )
    except (TypeError, ValueError):
        return None, "OPENUI_BAD_STRUCTURE"
    if len(encoded.encode("utf-8")) > OPENUI_MAX_SPEC_BYTES:
        return None, "OPENUI_TOO_LARGE"

    cleaned, failed = _clean_sections(spec, depth=1)
    if failed is not None:
        return None, failed
    return cleaned, None


def _clean_sections(
    sections: Any,
    *,
    depth: int,
) -> tuple[list[dict[str, Any]], str | None]:
    if depth > OPENUI_MAX_DEPTH:
        return [], "OPENUI_TOO_DEEP"
    if not isinstance(sections, list):
        return [], "OPENUI_BAD_STRUCTURE"
    state = {"nodes": 0}
    cleaned: list[dict[str, Any]] = []
    for section in sections:
        node, failed = _clean_node(section, depth=depth, state=state)
        if failed is not None:
            return [], failed
        if node is not None:
            cleaned.append(node)
    return cleaned, None


def _clean_node(node: Any, *, depth: int, state: dict[str, int]) -> tuple[Any, str | None]:
    if depth > OPENUI_MAX_DEPTH:
        return None, "OPENUI_TOO_DEEP"
    if not isinstance(node, dict):
        return None, None
    node_type = node.get("type")
    if not isinstance(node_type, str) or node_type not in OPENUI_ALLOWED_TYPES:
        return None, None
    state["nodes"] += 1
    if state["nodes"] > OPENUI_MAX_NODES:
        return None, "OPENUI_TOO_MANY_NODES"

    allowed = OPENUI_SPEC_FIELDS[node_type]
    cleaned: dict[str, Any] = {"type": node_type}
    text_fields = {
        "text": OPENUI_MAX_TEXT_CHARS,
        "title": OPENUI_MAX_TITLE_CHARS,
        "label": OPENUI_MAX_LABEL_CHARS,
        "value": OPENUI_MAX_LABEL_CHARS,
        "unit": OPENUI_MAX_LABEL_CHARS,
        "name": OPENUI_MAX_LABEL_CHARS,
        "status": OPENUI_MAX_LABEL_CHARS,
        "notes": OPENUI_MAX_LABEL_CHARS,
        "task_code": OPENUI_MAX_LABEL_CHARS,
        "device_name": OPENUI_MAX_LABEL_CHARS,
        "type": OPENUI_MAX_LABEL_CHARS,
        "priority": OPENUI_MAX_LABEL_CHARS,
        "inner_code": OPENUI_MAX_LABEL_CHARS,
        "region": OPENUI_MAX_LABEL_CHARS,
        "updated_at": OPENUI_MAX_LABEL_CHARS,
        "x_label": OPENUI_MAX_LABEL_CHARS,
        "y_label": OPENUI_MAX_LABEL_CHARS,
        "alt": OPENUI_MAX_LABEL_CHARS,
    }
    for key, value in node.items():
        if key == "type" or key not in allowed:
            continue
        if key in text_fields:
            cleaned[key] = _clean_text(value, text_fields[key])
            if not cleaned[key]:
                return None, None
            if key == "tone" and cleaned[key] not in OPENUI_CARD_TONES:
                cleaned[key] = "neutral"
        elif key in {"src", "poster"}:
            url = _clean_media_url(value)
            if url is None:
                return None, None
            cleaned[key] = url
        elif key == "columns":
            columns = _clean_text_list(value, OPENUI_MAX_COLUMNS, 128)
            if columns is None:
                return None, None
            cleaned[key] = columns
        elif key == "rows":
            rows = _clean_rows(value)
            if rows is None:
                return None, None
            cleaned[key] = rows
        elif key == "cards":
            cards, failed = _clean_cards(value, depth=depth, state=state)
            if failed is not None:
                return None, failed
            if cards is None:
                return None, None
            cleaned[key] = cards
        elif key == "labels":
            labels = _clean_text_list(value, OPENUI_MAX_LABELS, OPENUI_MAX_LABEL_CHARS)
            if labels is None:
                return None, None
            cleaned[key] = labels
        elif key == "series":
            series, failed = _clean_series(value, depth=depth, state=state)
            if failed is not None:
                return None, failed
            if series is None:
                return None, None
            cleaned[key] = series
    return cleaned, None


def _clean_text(value: Any, max_chars: int) -> str | None:
    if not isinstance(value, str):
        return None
    text = value.strip()
    if not text:
        return None
    return text[:max_chars]


def _clean_media_url(value: Any) -> str | None:
    if not isinstance(value, str) or not value:
        return None
    url = value[:OPENUI_MAX_MEDIA_URL_CHARS]
    if url.startswith("https://"):
        return url
    lowered = url.lower()
    if lowered.startswith("http://") and any(
        lowered.startswith(f"http://{host}") for host in ("localhost", "127.0.0.1", "[::1]")
    ):
        return url
    return None


def _clean_text_list(
    value: Any,
    max_items: int,
    max_chars: int,
) -> list[str] | None:
    if not isinstance(value, list):
        return None
    cleaned: list[str] = []
    for item in value[:max_items]:
        text = _clean_text(item, max_chars)
        if text is not None:
            cleaned.append(text)
    return cleaned or None


def _clean_rows(value: Any) -> list[list[str]] | None:
    if not isinstance(value, list):
        return None
    cleaned: list[list[str]] = []
    for row in value[:OPENUI_MAX_ROWS]:
        if not isinstance(row, list):
            return None
        cells = [str(item)[:128] for item in row[:OPENUI_MAX_COLUMNS]]
        cleaned.append(cells)
    if not cleaned:
        return None
    return cleaned


def _clean_cards(
    value: Any,
    *,
    depth: int,
    state: dict[str, int],
) -> tuple[list[dict[str, Any]] | None, str | None]:
    if not isinstance(value, list):
        return None, None
    cleaned: list[dict[str, Any]] = []
    for card in value[:OPENUI_MAX_CARDS]:
        node, failed = _clean_node(card, depth=depth + 1, state=state)
        if failed is not None:
            return None, failed
        if node is not None:
            cleaned.append(node)
    if not cleaned:
        return None, None
    return cleaned, None


def _clean_series(
    value: Any,
    *,
    depth: int,
    state: dict[str, int],
) -> tuple[list[dict[str, Any]] | None, str | None]:
    if not isinstance(value, list):
        return None, None
    cleaned: list[dict[str, Any]] = []
    for series in value[:OPENUI_MAX_SERIES]:
        if not isinstance(series, dict):
            return None, None
        name = _clean_text(series.get("name"), OPENUI_MAX_LABEL_CHARS)
        data_value = series.get("data")
        entries: list[Any] | None
        if isinstance(data_value, list) and data_value and all(
            isinstance(item, dict) for item in data_value
        ):
            entries = _clean_pie_slices(data_value)
        else:
            entries = _clean_numbers(data_value)
        if entries is None:
            return None, None
        cleaned.append({"name": name or "", "data": entries})
    if not cleaned:
        return None, None
    return cleaned, None


def _clean_pie_slices(value: list[Any]) -> list[dict[str, Any]] | None:
    cleaned: list[dict[str, Any]] = []
    for item in value[:OPENUI_MAX_LABELS]:
        if not isinstance(item, dict):
            return None
        name = _clean_text(item.get("name"), OPENUI_MAX_LABEL_CHARS)
        number = item.get("value")
        if isinstance(number, bool) or not isinstance(number, (int, float)):
            return None
        number = max(-OPENUI_MAX_NUMBER_ABS, min(OPENUI_MAX_NUMBER_ABS, number))
        cleaned.append({"name": name or "", "value": number})
    if not cleaned:
        return None
    return cleaned


def _clean_numbers(value: Any) -> list[int | float] | None:
    if not isinstance(value, list) or not value:
        return None
    cleaned: list[int | float] = []
    for item in value:
        if isinstance(item, bool) or not isinstance(item, (int, float)):
            return None
        number = max(-OPENUI_MAX_NUMBER_ABS, min(OPENUI_MAX_NUMBER_ABS, item))
        cleaned.append(number)
    return cleaned