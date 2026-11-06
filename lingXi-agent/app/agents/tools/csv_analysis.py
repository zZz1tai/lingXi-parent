"""用户提供的 CSV 文本统计；不执行公式、不访问文件、网络或业务库。"""

from __future__ import annotations

import csv
import io
import json
import re
from decimal import Decimal, ROUND_HALF_UP, localcontext
from typing import Any, Literal

from langchain.tools import ToolRuntime, tool
from langchain_core.tools import BaseTool
from pydantic import BaseModel, ConfigDict, Field

from app.agents.state import AgentContext


class CsvAnalysisInput(BaseModel):
    model_config = ConfigDict(arbitrary_types_allowed=True, extra="forbid")

    csv_text: str = Field(min_length=1, max_length=50000, description="用户提供的完整 CSV/TSV 文本，首行为唯一非空列名；不要编造或截断数据")
    delimiter: Literal[",", "\t", ";"] = Field(default=",", description="明确的字段分隔符：逗号、制表符或分号")
    group_by: str | None = Field(default=None, min_length=1, max_length=80, description="可选分组列名，必须与 value_column 一起提供")
    value_column: str | None = Field(default=None, min_length=1, max_length=80, description="可选分组统计数值列名，必须与 group_by 一起提供")
    runtime: ToolRuntime[AgentContext]


class CsvAnalysisError(ValueError):
    """向现有工具错误中间件提供不含原始数据的校验提示。"""

    code = "TOOL_INVALID_INPUT"

    def __init__(self, message: str) -> None:
        self.public_message = message
        super().__init__(message)


def _number(text: str) -> Decimal | None:
    if not re.fullmatch(r"[+-]?(?:[0-9]+(?:\.[0-9]*)?|\.[0-9]+)", text):
        return None
    value = Decimal(text)
    if abs(value) > Decimal("1e12") or value.as_tuple().exponent < -6:
        raise CsvAnalysisError("数值绝对值不能超过 10^12，且最多支持 6 位小数；请先明确转换口径")
    return value


def _format(value: Decimal) -> str:
    if value == 0:
        return "0"
    text = format(value, "f")
    return text.rstrip("0").rstrip(".") if "." in text else text


def _statistics(cells: list[str]) -> dict[str, Any]:
    values = [number for cell in cells if cell and (number := _number(cell)) is not None]
    missing = cells.count("")
    result: dict[str, Any] = {
        "row_count": len(cells), "numeric_count": len(values), "missing_count": missing,
        "non_numeric_count": len(cells) - missing - len(values),
        "sum": None, "mean": None, "min": None, "max": None,
    }
    if values:
        total = sum(values, Decimal(0))
        result.update(sum=_format(total), min=_format(min(values)), max=_format(max(values)),
                      mean=_format((total / len(values)).quantize(Decimal("0.000001"), rounding=ROUND_HALF_UP)))
    return result


def analyze_csv_text(csv_text: str, delimiter: str = ",", group_by: str | None = None,
                     value_column: str | None = None) -> dict[str, Any]:
    """解析有界表格，统计所有行；不静默截断、不将空值视作零。"""
    if not 1 <= len(csv_text) <= 50000 or "\x00" in csv_text:
        raise CsvAnalysisError("CSV 必须为 1～50000 字符且不能包含空字符")
    if delimiter not in {",", "\t", ";"}:
        raise CsvAnalysisError("仅支持逗号、制表符或分号分隔")
    try:
        rows = list(csv.reader(io.StringIO(csv_text.removeprefix("\ufeff"), newline=""), delimiter=delimiter, strict=True))
    except csv.Error as exc:
        raise CsvAnalysisError("CSV 引号或字段格式无效，请检查原始表格") from exc
    if not rows:
        raise CsvAnalysisError("CSV 缺少表头")
    headers = [cell.strip() for cell in rows[0]]
    if not 1 <= len(headers) <= 20 or any(not header or len(header) > 80 for header in headers) or len(set(headers)) != len(headers):
        raise CsvAnalysisError("表头必须包含 1～20 个互不重复的非空列名，每个不超过 80 字符")
    records = rows[1:]
    if not 1 <= len(records) <= 1000:
        raise CsvAnalysisError("CSV 必须包含 1～1000 行数据，不能只提供表头")
    if any(len(row) != len(headers) for row in records):
        raise CsvAnalysisError("每条记录的列数必须与表头一致，请检查空行或缺少的分隔符")
    records = [[cell.strip() for cell in row] for row in records]
    if (group_by is None) != (value_column is None):
        raise CsvAnalysisError("分组统计需要同时提供 group_by 和 value_column")
    if group_by is not None and (group_by not in headers or value_column not in headers):
        raise CsvAnalysisError("分组列和数值列必须与表头名称完全一致")
    # 输入数值范围和行数有界，40 位精度足以精确累加；只有均值按六位小数四舍五入。
    with localcontext() as context:
        context.prec = 40
        result: dict[str, Any] = {
            "source": "user_provided_csv", "row_count": len(records), "column_count": len(headers),
            "columns": [{"name": name, **_statistics([row[index] for row in records])} for index, name in enumerate(headers)],
            "numeric_policy": "只统计普通十进制数字；空值与非数字分别计数并排除。百分比、千分位、科学计数法、公式作为文本；均值保留最多六位小数，四舍五入。",
            "groups": [],
        }
        if group_by is not None:
            group_index, value_index = headers.index(group_by), headers.index(value_column)
            groups: dict[str, list[str]] = {}
            for row in records:
                key = row[group_index]
                if len(key) > 120:
                    raise CsvAnalysisError("分组值最长支持 120 字符")
                groups.setdefault(key, []).append(row[value_index])
                if len(groups) > 50:
                    raise CsvAnalysisError("分组最多支持 50 组，请缩小数据范围")
            result["group_by"] = group_by
            result["value_column"] = value_column
            result["groups"] = [{"value": key, **_statistics(cells)} for key, cells in groups.items()]
        return result


def create_csv_analysis_tool() -> BaseTool:
    @tool("analyze_csv", args_schema=CsvAnalysisInput, response_format="content_and_artifact",
          description="统计用户提供的 CSV/TSV 文本（首行为表头），返回各列合计、均值、最小最大值及空值/非数字数目；可指定 group_by 与 value_column 做分组汇总。最多 1000 行、20 列、50000 字符、50 组。只分析提供的原文，不读取文件路径或 URL，不执行公式。数值仅支持普通十进制，最多六位小数且绝对值不超过 10^12。")
    async def analyze_csv(csv_text: str, runtime: ToolRuntime[AgentContext], delimiter: Literal[",", "\t", ";"] = ",",
                          group_by: str | None = None, value_column: str | None = None) -> tuple[str, dict[str, Any]]:
        runtime.stream_writer({"type": "tool_progress", "tool": "analyze_csv", "status": "started"})
        try:
            data = analyze_csv_text(csv_text, delimiter, group_by, value_column)
        except CsvAnalysisError:
            runtime.stream_writer({"type": "tool_progress", "tool": "analyze_csv", "status": "failed"})
            raise
        runtime.stream_writer({"type": "tool_progress", "tool": "analyze_csv", "status": "completed"})
        return json.dumps(data, ensure_ascii=False, separators=(",", ":")), {"provider": "local", "tool": "analyze_csv", "data": data}
    return analyze_csv
