"""有界的逐行文本差异工具；不读文件、不执行代码、不做语义相似度推断。"""

from __future__ import annotations

import json
from difflib import SequenceMatcher
from typing import Any

from langchain.tools import ToolRuntime, tool
from langchain_core.tools import BaseTool
from pydantic import BaseModel, ConfigDict, Field

from app.agents.state import AgentContext


class TextCompareInput(BaseModel):
    model_config = ConfigDict(arbitrary_types_allowed=True, extra="forbid")

    before: str = Field(max_length=20000, description="修改前的完整原文，可为空；不能擅自概括或改写")
    after: str = Field(max_length=20000, description="修改后的完整原文，可为空；不能擅自概括或改写")
    ignore_whitespace: bool = Field(default=False, strict=True, description="仅用户要求忽略空白时启用；按行压缩空白并忽略行首尾空白，不删除空白行")
    runtime: ToolRuntime[AgentContext]


class TextCompareError(ValueError):
    code = "TOOL_INVALID_INPUT"

    def __init__(self, message: str) -> None:
        self.public_message = message
        super().__init__(message)


def compare_texts(before: str, after: str, ignore_whitespace: bool = False) -> dict[str, Any]:
    """返回完整变化计数和有界片段；行号从 1 开始，count=0 表示插入位置。"""
    if any(len(text) > 20000 or "\x00" in text for text in (before, after)):
        raise TextCompareError("每版文本最多 20000 字符，且不能包含空字符；请按章节或小节拆分")
    normalized = [text.replace("\r\n", "\n").replace("\r", "\n") for text in (before, after)]
    original_lines = [text.splitlines(keepends=True) for text in normalized]
    if any(len(lines) > 300 for lines in original_lines):
        raise TextCompareError("每版文本最多 300 行，请按章节或小节拆分")
    compared_lines = [
        [" ".join(line.split()) for line in lines] if ignore_whitespace else lines
        for lines in original_lines
    ]
    # ponytail: 最多 300 行控制 SequenceMatcher 最坏复杂度；大文件另做分块对比。
    opcodes = SequenceMatcher(None, *compared_lines, autojunk=False).get_opcodes()
    changed = [item for item in opcodes if item[0] != "equal"]
    changes = []
    remaining = 8000
    truncated = False
    for kind, start_old, end_old, start_new, end_new in changed[:20]:
        fragments = []
        fragment_truncated = False
        for lines, start, end in ((original_lines[0], start_old, end_old), (original_lines[1], start_new, end_new)):
            full = "".join(lines[start:end])
            excerpt = full[:min(1000, remaining)]
            remaining -= len(excerpt)
            fragment_truncated |= len(excerpt) < len(full)
            fragments.append(excerpt)
        changes.append({
            "kind": kind,
            "before_range": {"start": start_old + 1, "count": end_old - start_old},
            "after_range": {"start": start_new + 1, "count": end_new - start_new},
            "before_text": fragments[0], "after_text": fragments[1],
            "truncated": fragment_truncated,
        })
        truncated |= fragment_truncated
    return {
        "source": "user_provided_texts",
        "identical": before == after,
        "equivalent_under_options": not changed,
        "ignore_whitespace": ignore_whitespace,
        "comparison_policy": "逐行比较；CRLF/CR 统一为 LF；行号从 1 开始，范围 count=0 表示该行之前的插入位置。忽略空白时按行压缩空白，不删除空白行。变化不代表语义或质量判断。",
        "before_line_count": len(original_lines[0]), "after_line_count": len(original_lines[1]),
        "removed_line_count": sum(end_old - start_old for _, start_old, end_old, _, _ in changed),
        "added_line_count": sum(end_new - start_new for _, _, _, start_new, end_new in changed),
        "changed_block_count": len(changed), "returned_block_count": len(changes),
        "truncated": truncated or len(changed) > len(changes),
        "changes": changes,
    }


def create_text_compare_tool() -> BaseTool:
    @tool(
        "compare_text", args_schema=TextCompareInput, response_format="content_and_artifact",
        description=(
            "比较用户提供的修改前 before 与修改后 after 两版文字，返回逐行新增、删除、替换片段及行号。"
            "适用于小说改稿、需求文档和提示词版本核对；不评价语义或优劣。每版最多 20000 字符、300 行。"
            "最多返回 20 个变化块，每侧片段最多 1000 字符，总片段 8000 字符；truncated 为真时必须说明片段不完整。"
            "只在用户要求时启用 ignore_whitespace。只传原文，不读路径或 URL，不执行代码。"
        ),
    )
    async def compare_text(before: str, after: str, runtime: ToolRuntime[AgentContext],
                           ignore_whitespace: bool = False) -> tuple[str, dict[str, Any]]:
        runtime.stream_writer({"type": "tool_progress", "tool": "compare_text", "status": "started"})
        try:
            data = compare_texts(before, after, ignore_whitespace)
        except TextCompareError:
            runtime.stream_writer({"type": "tool_progress", "tool": "compare_text", "status": "failed"})
            raise
        runtime.stream_writer({"type": "tool_progress", "tool": "compare_text", "status": "completed"})
        return json.dumps(data, ensure_ascii=False, separators=(",", ":")), {"provider": "local", "tool": "compare_text", "data": data}
    return compare_text
