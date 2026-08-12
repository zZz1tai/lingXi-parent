"""OpenUI Spec 的字段白名单与硬性限制。

这些常量同时约束 Python 构建端与 Java 转发端：
- 未知事件类型一律丢弃；
- 未知字段一律丢弃；
- 超限节点/字段按拒绝或截断策略处理。
"""

from __future__ import annotations

OPENUI_SCHEMA_VERSION = 1

# 组件类型白名单：只允许模型组合这些组件。
OPENUI_ALLOWED_TYPES: frozenset[str] = frozenset(
    {
        "Text",
        "Markdown",
        "Notice",
        "MetricGrid",
        "MetricCard",
        "DataTable",
        "LineChart",
        "BarChart",
        "PieChart",
        "DeviceStatusCard",
        "MaintenanceTaskCard",
        "ImageResult",
        "VideoResult",
    }
)

# 个人字段白名单：每种组件只保留允许的属性，其余字段一律丢弃。
OPENUI_SPEC_FIELDS: dict[str, frozenset[str]] = {
    "Text": frozenset({"text"}),
    "Markdown": frozenset({"text"}),
    "Notice": frozenset({"tone", "text"}),
    "MetricGrid": frozenset({"title", "columns", "cards"}),
    "MetricCard": frozenset({"label", "value", "unit", "tone"}),
    "DataTable": frozenset({"title", "columns", "rows"}),
    "LineChart": frozenset({"title", "labels", "series", "x_label", "y_label"}),
    "BarChart": frozenset({"title", "labels", "series", "x_label", "y_label"}),
    "PieChart": frozenset({"title", "series"}),
    "DeviceStatusCard": frozenset(
        {"inner_code", "name", "region", "status", "updated_at"}
    ),
    "MaintenanceTaskCard": frozenset(
        {"task_code", "device_name", "type", "priority", "status", "notes"}
    ),
    "ImageResult": frozenset({"src", "alt"}),
    "VideoResult": frozenset({"src", "poster", "alt"}),
}

# 单条 UI Spec 不超过 256KB。
OPENUI_MAX_SPEC_BYTES = 256 * 1024
# 最大节点数。
OPENUI_MAX_NODES = 120
# 最大嵌套深度（根为 1）。
OPENUI_MAX_DEPTH = 8
# 单个文本属性不超过 4KB。
OPENUI_MAX_TEXT_CHARS = 4096
# 单个数值属性的量级上限，防止前端图表崩溃。
OPENUI_MAX_NUMBER_ABS = 1_000_000_000_000_000
# 结构性数组上限。
OPENUI_MAX_CARDS = 12
OPENUI_MAX_COLUMNS = 8
OPENUI_MAX_ROWS = 60
OPENUI_MAX_SERIES = 6
OPENUI_MAX_LABELS = 90
OPENUI_MAX_TITLE_CHARS = 200
OPENUI_MAX_LABEL_CHARS = 256
OPENUI_MAX_MEDIA_URL_CHARS = 2048

# 音调白名单。
OPENUI_NOTICE_TONES = frozenset({"info", "success", "warning", "danger"})
OPENUI_CARD_TONES = frozenset({"success", "warning", "danger", "neutral"})