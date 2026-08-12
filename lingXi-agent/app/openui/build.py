"""把业务工具产物确定性映射为 OpenUI Spec。

该模块只消费工具 artifact 的白名单字段，输出固定结构的组件树；
随后必须经过 ``validate.validate_spec`` 才能进入 SSE。
"""

from __future__ import annotations

import json
from typing import Any

from app.openui.schema import (
    OPENUI_MAX_CARDS,
    OPENUI_MAX_COLUMNS,
    OPENUI_MAX_LABEL_CHARS,
    OPENUI_MAX_LABELS,
    OPENUI_MAX_MEDIA_URL_CHARS,
    OPENUI_MAX_ROWS,
    OPENUI_MAX_SERIES,
)


def build_data_analysis_spec(artifacts: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """按工具产物顺序生成数据分析看板 Spec。"""
    sections: list[dict[str, Any]] = []
    for artifact in artifacts:
        if not isinstance(artifact, dict):
            continue
        tool = artifact.get("tool")
        data = artifact.get("data")
        if not isinstance(data, dict):
            continue
        if tool == "query_sales_summary":
            sections.extend(_sales_sections(data))
        elif tool == "query_task_statistics":
            sections.extend(_task_sections(data))
        elif tool == "query_abnormal_devices":
            sections.extend(_abnormal_device_sections(data))
        elif tool == "lookup_device":
            sections.extend(_device_sections(data))
        elif tool == "generate_image":
            sections.extend(_image_sections(data))
    return sections


def _region_label(data: dict[str, Any]) -> str:
    scope = data.get("scope")
    if not isinstance(scope, dict):
        return ""
    names = scope.get("region_names")
    if isinstance(names, list):
        return "、".join(str(item) for item in names if item)[:128]
    return ""


def _time_range_label(data: dict[str, Any]) -> str:
    time_range = data.get("time_range")
    if not isinstance(time_range, dict):
        return ""
    start = time_range.get("start")
    end = time_range.get("end")
    if isinstance(start, str) and isinstance(end, str):
        return f"{start[:10]} 至 {end[:10]}"
    return ""


def _num(value: Any) -> int | float | None:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        return None
    return value


def _fmt_money(cent: Any) -> str | None:
    value = _num(cent)
    if value is None:
        return None
    yuan = value / 100
    return f"{yuan:,.2f}".rstrip("0").rstrip(".")


def _safe_str(value: Any, max_chars: int = 256) -> str | None:
    if not isinstance(value, str):
        return None
    return value[:max_chars]


def _running_status_text(raw: Any) -> str:
    if not isinstance(raw, str):
        return "状态未知"
    try:
        parsed = json.loads(raw)
    except ValueError:
        return "状态异常"
    if not isinstance(parsed, dict):
        return "状态异常"
    code = parsed.get("statusCode")
    if code == "1001":
        return "运行正常"
    if isinstance(code, str) and code:
        return f"状态异常（{code[:32]}）"
    return "状态异常"


def _sales_sections(data: dict[str, Any]) -> list[dict[str, Any]]:
    sections: list[dict[str, Any]] = []
    metrics = data.get("metrics")
    if not isinstance(metrics, dict):
        return sections
    parts = [
        part for part in (_time_range_label(data), _region_label(data)) if part
    ]
    if parts:
        sections.append({"type": "Text", "text": " · ".join(parts)})
    cards: list[dict[str, Any]] = []
    order_count = _num(metrics.get("order_count"))
    if order_count is not None:
        cards.append(
            {
                "type": "MetricCard",
                "label": "订单数",
                "value": f"{int(order_count):,}",
                "unit": "单",
            }
        )
    amount = _fmt_money(metrics.get("order_amount_cent"))
    if amount is not None:
        cards.append(
            {"type": "MetricCard", "label": "销售额", "value": amount, "unit": "元"}
        )
    average = _fmt_money(metrics.get("average_order_amount_cent"))
    if average is not None:
        cards.append(
            {"type": "MetricCard", "label": "客单价", "value": average, "unit": "元"}
        )
    if cards:
        sections.append(
            {"type": "MetricGrid", "title": "销售汇总", "cards": cards[:OPENUI_MAX_CARDS]}
        )
    trend_chart = _trend_chart(metrics, data.get("dimensions"))
    if trend_chart is not None:
        sections.append(trend_chart)
    return sections


def _trend_chart(
    metrics: dict[str, Any],
    dimensions: Any,
) -> dict[str, Any] | None:
    if not isinstance(dimensions, dict):
        return None
    trend = dimensions.get("trend")
    if not isinstance(trend, list) or not trend:
        return None
    labels: list[str] = []
    order_series: list[int] = []
    amount_series: list[float] = []
    for row in trend[:OPENUI_MAX_LABELS]:
        if not isinstance(row, dict):
            continue
        period = row.get("period")
        if not isinstance(period, str) or not period:
            continue
        labels.append(period[:OPENUI_MAX_LABEL_CHARS])
        order = _num(row.get("order_count"))
        amount = _num(row.get("order_amount_cent"))
        order_series.append(int(order) if order is not None else 0)
        amount_series.append(round(amount / 100, 2) if amount is not None else 0.0)
    if not labels:
        return None
    series: list[dict[str, Any]] = []
    if any(order_series):
        series.append({"name": "订单数", "data": order_series})
    if any(amount_series):
        series.append({"name": "销售额（元）", "data": amount_series})
    if not series:
        return None
    return {
        "type": "BarChart",
        "title": "销售趋势",
        "labels": labels[:OPENUI_MAX_LABELS],
        "series": series[:OPENUI_MAX_SERIES],
    }


def _task_sections(data: dict[str, Any]) -> list[dict[str, Any]]:
    sections: list[dict[str, Any]] = []
    metrics = data.get("metrics")
    if not isinstance(metrics, dict):
        return sections
    parts = [
        part for part in (_time_range_label(data), _region_label(data)) if part
    ]
    if parts:
        sections.append({"type": "Text", "text": " · ".join(parts)})
    defined = (
        ("total", "工单总数", ""),
        ("pending_count", "待处理", ""),
        ("progress_count", "进行中", ""),
        ("completed_count", "已完成", ""),
        ("canceled_count", "已取消", ""),
        ("worker_count", "参与人数", ""),
    )
    cards: list[dict[str, Any]] = []
    for key, label, unit in defined:
        value = _num(metrics.get(key))
        if value is not None:
            cards.append(
                {"type": "MetricCard", "label": label, "value": f"{int(value):,}", "unit": unit}
            )
    average = _num(metrics.get("average_completion_minutes"))
    if average is not None:
        cards.append(
            {
                "type": "MetricCard",
                "label": "平均完成时长",
                "value": f"{average:g}",
                "unit": "分钟",
            }
        )
    if cards:
        sections.append(
            {"type": "MetricGrid", "title": "工单统计", "cards": cards[:OPENUI_MAX_CARDS]}
        )
    pie = _status_pie(metrics)
    if pie is not None:
        sections.append(pie)
    return sections


def _status_pie(metrics: dict[str, Any]) -> dict[str, Any] | None:
    statuses = (
        ("待处理", "pending_count"),
        ("进行中", "progress_count"),
        ("已完成", "completed_count"),
        ("已取消", "canceled_count"),
    )
    items: list[dict[str, Any]] = []
    for name, key in statuses:
        value = _num(metrics.get(key))
        if value is not None and value > 0:
            items.append({"name": name, "value": int(value)})
    if not items:
        return None
    return {
        "type": "PieChart",
        "title": "工单状态分布",
        "series": [{"name": "工单状态", "data": items}],
    }


def _abnormal_device_sections(data: dict[str, Any]) -> list[dict[str, Any]]:
    sections: list[dict[str, Any]] = []
    metrics = data.get("metrics")
    count = None
    if isinstance(metrics, dict):
        count = _num(metrics.get("current_abnormal_count"))
    if count is not None:
        sections.append(
            {
                "type": "MetricGrid",
                "cards": [
                    {
                        "type": "MetricCard",
                        "label": "当前异常设备",
                        "value": f"{int(count):,}",
                        "unit": "台",
                        "tone": "warning",
                    }
                ],
            }
        )
    basis = _safe_str(data.get("statistics_basis"), 512)
    if basis:
        sections.append({"type": "Notice", "tone": "info", "text": basis})
    rows = data.get("rows")
    if isinstance(rows, list):
        table_rows: list[list[str]] = []
        for row in rows[:OPENUI_MAX_ROWS]:
            if not isinstance(row, dict):
                continue
            table_rows.append(
                [
                    _safe_str(row.get("inner_code"), 64) or "",
                    _safe_str(row.get("vm_type_name"), 64) or "",
                    _running_status_text(row.get("running_status")),
                    _safe_str(row.get("updated_at"), 19) or "",
                ]
            )
        if table_rows:
            sections.append(
                {
                    "type": "DataTable",
                    "title": "异常设备列表",
                    "columns": ["设备编号", "型号", "运行状态", "更新时间"][
                        :OPENUI_MAX_COLUMNS
                    ],
                    "rows": table_rows,
                }
            )
    return sections


def _device_sections(data: dict[str, Any]) -> list[dict[str, Any]]:
    rows = data.get("rows")
    if not isinstance(rows, list) or not rows or not isinstance(rows[0], dict):
        return []
    device = rows[0]
    region = _region_label(data)
    status = _running_status_text(device.get("running_status"))
    if status == "运行正常":
        status = "运行正常"
    return [
        {
            "type": "DeviceStatusCard",
            "inner_code": _safe_str(device.get("inner_code"), 64) or "",
            "name": _safe_str(device.get("vm_type_name"), 64) or "未知型号",
            "region": region or "未知区域",
            "status": status,
            "updated_at": _safe_str(device.get("updated_at"), 19) or "",
        }
    ]


def _image_sections(data: dict[str, Any]) -> list[dict[str, Any]]:
    image_url = data.get("image_url")
    if not isinstance(image_url, str) or not image_url.startswith("https://"):
        return []
    return [
        {
            "type": "ImageResult",
            "src": image_url[:OPENUI_MAX_MEDIA_URL_CHARS],
            "alt": "生成的图片",
        }
    ]