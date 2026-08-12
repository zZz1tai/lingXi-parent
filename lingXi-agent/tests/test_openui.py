"""OpenUI 表现层契约测试：构建、校验与 SSE 事件发射。

覆盖 Python 构建端 → validate 端 → SSE 发射端的端到端不变量：
- 构建产物只含白名单组件；
- 校验器清洗未知字段/节点并施加硬性限制；
- 发射端仅在数据分析模式下产出 ui_* 事件，失败不打断正文流。
"""

from __future__ import annotations

import unittest
from typing import Any
from unittest.mock import patch

from app.api.v1.chat import _emit_openui_events
from app.openui.build import build_data_analysis_spec
from app.openui.schema import (
    OPENUI_ALLOWED_TYPES,
    OPENUI_MAX_DEPTH,
    OPENUI_MAX_SPEC_BYTES,
)
from app.openui.validate import validate_spec


def _sales_artifacts() -> list[dict[str, Any]]:
    return [
        {
            "tool": "query_sales_summary",
            "data": {
                "scope": {"region_names": ["上海一区"]},
                "time_range": {"start": "2026-07-01T00:00:00Z", "end": "2026-07-31T23:59:59Z"},
                "metrics": {
                    "order_count": 128,
                    "order_amount_cent": 1_286_000,
                    "average_order_amount_cent": 10_046,
                },
                "dimensions": {
                    "trend": [
                        {"period": "2026-07-01", "order_count": 10, "order_amount_cent": 100_000},
                        {"period": "2026-07-02", "order_count": 15, "order_amount_cent": 180_000},
                    ]
                },
            },
        }
    ]


def _task_artifacts() -> list[dict[str, Any]]:
    return [
        {
            "tool": "query_task_statistics",
            "data": {
                "metrics": {
                    "total": 40,
                    "pending_count": 12,
                    "progress_count": 8,
                    "completed_count": 18,
                    "canceled_count": 2,
                    "worker_count": 6,
                }
            },
        }
    ]


def _abnormal_device_artifacts() -> list[dict[str, Any]]:
    return [
        {
            "tool": "query_abnormal_devices",
            "data": {
                "metrics": {"current_abnormal_count": 3},
                "statistics_basis": "近 1 小时心跳异常",
                "rows": [
                    {
                        "inner_code": "A001",
                        "vm_type_name": "VM-8C16G",
                        "running_status": '{"statusCode": "1002"}',
                        "updated_at": "2026-07-25T08:30:00Z",
                    }
                ],
            },
        }
    ]


def _device_artifacts() -> list[dict[str, Any]]:
    return [
        {
            "tool": "lookup_device",
            "data": {
                "scope": {"region_names": ["上海一区"]},
                "rows": [
                    {
                        "inner_code": "A001",
                        "vm_type_name": "VM-8C16G",
                        "running_status": '{"statusCode": "1001"}',
                        "updated_at": "2026-07-25T08:30:00Z",
                    }
                ],
            },
        }
    ]


class BuildContractTest(unittest.TestCase):
    """build_data_analysis_spec 只产出白名单组件。"""

    def assert_allowed(self, spec: list[dict[str, Any]]) -> None:
        for section in spec:
            self.assertIn(section["type"], OPENUI_ALLOWED_TYPES)

    def test_sales_artifacts_build_metric_grid_with_typed_cards_and_trend_chart(self) -> None:
        spec = build_data_analysis_spec(_sales_artifacts())
        self.assert_allowed(spec)
        types = [item["type"] for item in spec]
        self.assertIn("MetricGrid", types)
        self.assertIn("BarChart", types)
        grid = next(item for item in spec if item["type"] == "MetricGrid")
        self.assertTrue(all(card["type"] == "MetricCard" for card in grid["cards"]))
        self.assertEqual(grid["cards"][0]["label"], "订单数")

    def test_task_artifacts_build_metric_grid_and_status_pie(self) -> None:
        spec = build_data_analysis_spec(_task_artifacts())
        self.assert_allowed(spec)
        types = [item["type"] for item in spec]
        self.assertIn("MetricGrid", types)
        self.assertIn("PieChart", types)

    def test_abnormal_devices_build_grid_notice_and_table(self) -> None:
        spec = build_data_analysis_spec(_abnormal_device_artifacts())
        self.assert_allowed(spec)
        types = [item["type"] for item in spec]
        self.assertIn("MetricGrid", types)
        self.assertIn("Notice", types)
        self.assertIn("DataTable", types)

    def test_lookup_device_builds_status_card(self) -> None:
        spec = build_data_analysis_spec(_device_artifacts())
        self.assert_allowed(spec)
        self.assertEqual(spec[0]["type"], "DeviceStatusCard")
        self.assertEqual(spec[0]["status"], "运行正常")

    def test_non_https_image_artifact_is_skipped(self) -> None:
        spec = build_data_analysis_spec(
            [
                {
                    "tool": "generate_image",
                    "data": {"image_url": "http://insecure.invalid/a.png"},
                }
            ]
        )
        self.assertEqual(spec, [])

    def test_empty_and_junk_artifacts_build_empty_spec(self) -> None:
        self.assertEqual(build_data_analysis_spec([]), [])
        self.assertEqual(
            build_data_analysis_spec([{"tool": "unknown_tool", "data": {"x": 1}}]),
            [],
        )


class ValidateContractTest(unittest.TestCase):
    """validate_spec 清洗未知内容并施加硬性限制。"""

    def test_clean_spec_passes_unchanged_and_unknown_fields_are_dropped(self) -> None:
        spec = [
            {
                "type": "MetricGrid",
                "title": "销售汇总",
                "cards": [
                    {"type": "MetricCard", "label": "订单数", "value": "128", "unit": "单"},
                ],
                "evil": {"script": "alert(1)"},
            }
        ]
        cleaned, error_code = validate_spec(spec)
        self.assertIsNone(error_code)
        assert cleaned is not None
        self.assertEqual(cleaned[0]["type"], "MetricGrid")
        self.assertNotIn("evil", cleaned[0])

    def test_unknown_component_and_unsafe_media_url_are_dropped(self) -> None:
        spec = [
            {"type": "Script", "src": "javascript:alert(1)"},
            {"type": "ImageResult", "src": "javascript:alert(1)", "alt": "x"},
            {"type": "Text", "text": "正常正文"},
        ]
        cleaned, error_code = validate_spec(spec)
        self.assertIsNone(error_code)
        assert cleaned is not None
        self.assertEqual([item["type"] for item in cleaned], ["Text"])

    def test_too_deep_nesting_is_rejected(self) -> None:
        inner: dict[str, Any] = {"type": "MetricCard", "label": "a", "value": "b"}
        for _ in range(OPENUI_MAX_DEPTH + 2):
            inner = {"type": "MetricGrid", "cards": [inner]}
        cleaned, error_code = validate_spec([inner])
        self.assertIsNone(cleaned)
        self.assertEqual(error_code, "OPENUI_TOO_DEEP")

    def test_too_many_nodes_is_rejected(self) -> None:
        cards = [
            {"type": "MetricCard", "label": f"指标{i}", "value": str(i)}
            for i in range(12)
        ]
        spec = [
            {"type": "MetricGrid", "cards": cards}
            for _ in range(10)
        ]
        cleaned, error_code = validate_spec(spec)
        self.assertIsNone(cleaned)
        self.assertEqual(error_code, "OPENUI_TOO_MANY_NODES")

    def test_oversized_spec_is_rejected(self) -> None:
        spec = [
            {"type": "Text", "text": "x" * (OPENUI_MAX_SPEC_BYTES + 1)},
            {"type": "Text", "text": "y"},
        ]
        cleaned, error_code = validate_spec(spec)
        self.assertIsNone(cleaned)
        self.assertEqual(error_code, "OPENUI_TOO_LARGE")

    def test_non_list_spec_is_rejected(self) -> None:
        self.assertEqual(validate_spec({"type": "Text"}), (None, "OPENUI_BAD_STRUCTURE"))

    def test_built_spec_survives_validation_with_all_cards(self) -> None:
        artifacts = _sales_artifacts() + _task_artifacts() + _abnormal_device_artifacts()
        spec = build_data_analysis_spec(artifacts)
        cleaned, error_code = validate_spec(spec)
        self.assertIsNone(error_code)
        assert cleaned is not None
        grids = [item for item in cleaned if item["type"] == "MetricGrid"]
        for grid in grids:
            self.assertTrue(all(card["type"] == "MetricCard" for card in grid["cards"]))


class EmitContractTest(unittest.IsolatedAsyncioTestCase):
    """_emit_openui_events 按 ui_start → ui_delta* → ui_complete 顺序发射。"""

    async def test_happy_path_emits_start_delta_and_complete(self) -> None:
        events = [
            event
            async for event in _emit_openui_events(
                _sales_artifacts(),
                request_id="req-1",
                thread_id="thread-1",
            )
        ]
        self.assertTrue(events)
        types = []
        for line in events:
            self.assertTrue(line.startswith("data: "))
            import json

            payload = json.loads(line[len("data: ") :].strip())
            types.append(payload["type"])
            self.assertTrue(payload["type"].startswith("ui_"))
            self.assertEqual(payload["render_id"], "ui-req-1")
        self.assertEqual(types[0], "ui_start")
        self.assertEqual(types[-1], "ui_complete")
        self.assertTrue(any(item == "ui_delta" for item in types))
        complete = json.loads(events[-1][len("data: ") :].strip())
        self.assertEqual(complete["schema_version"], 1)
        self.assertIn("MetricGrid", [item["type"] for item in complete["spec"]])

    async def test_empty_artifacts_emit_nothing(self) -> None:
        events = [
            event
            async for event in _emit_openui_events(
                [],
                request_id="req-2",
                thread_id="thread-2",
            )
        ]
        self.assertEqual(events, [])

    async def test_build_failure_does_not_emit_any_ui_event(self) -> None:
        with patch(
            "app.api.v1.chat.build_data_analysis_spec",
            side_effect=RuntimeError("boom"),
        ):
            events = [
                event
                async for event in _emit_openui_events(
                    _sales_artifacts(),
                    request_id="req-3",
                    thread_id="thread-3",
                )
            ]
        self.assertEqual(events, [])

    async def test_invalid_spec_degrades_to_single_ui_error(self) -> None:
        deep_spec: list[dict[str, Any]] = []
        inner: dict[str, Any] = {"type": "MetricCard", "label": "a", "value": "b"}
        for _ in range(OPENUI_MAX_DEPTH + 2):
            inner = {"type": "MetricGrid", "cards": [inner]}
        with patch(
            "app.api.v1.chat.build_data_analysis_spec",
            return_value=[inner],
        ):
            events = [
                event
                async for event in _emit_openui_events(
                    _sales_artifacts(),
                    request_id="req-4",
                    thread_id="thread-4",
                )
            ]
        self.assertEqual(len(events), 1)
        import json

        payload = json.loads(events[0][len("data: ") :].strip())
        self.assertEqual(payload["type"], "ui_error")
        self.assertEqual(payload["code"], "OPENUI_TOO_DEEP")


if __name__ == "__main__":
    unittest.main()
