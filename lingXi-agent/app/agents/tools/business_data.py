"""LangChain v1 的 Java 只读业务数据工具包装器。"""

from __future__ import annotations

import hashlib
import json
from typing import Any, Literal

from langchain.tools import ToolRuntime, tool
from langchain_core.tools import BaseTool
from langgraph.types import interrupt
from pydantic import BaseModel, ConfigDict, Field

from app.agents.state import AgentContext
from app.config.settings import settings
from app.services.agent_tool_client import (
    AgentToolClient,
    AgentToolClientError,
    ToolCallResult,
)


class _ToolInput(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)


class _RuntimeToolInput(_ToolInput):
    """保留 ToolNode 注入值，同时从模型可见 schema 中隐藏它。"""

    model_config = ConfigDict(arbitrary_types_allowed=True)
    runtime: ToolRuntime[AgentContext]


class SalesSummaryInput(_RuntimeToolInput):
    start: str = Field(..., pattern=r"^\d{4}-\d{2}-\d{2}$")
    end: str = Field(..., pattern=r"^\d{4}-\d{2}-\d{2}$")
    granularity: Literal["day", "month"] = "day"
    region_id: int | None = Field(default=None, ge=1)


class TaskStatisticsInput(_RuntimeToolInput):
    start: str = Field(..., pattern=r"^\d{4}-\d{2}-\d{2}$")
    end: str = Field(..., pattern=r"^\d{4}-\d{2}-\d{2}$")
    task_type: int | None = Field(default=None, ge=1, le=4)
    region_id: int | None = Field(default=None, ge=1)


class AbnormalDevicesInput(_RuntimeToolInput):
    limit: int = Field(default=10, ge=1, le=20)
    region_id: int | None = Field(default=None, ge=1)


class DeviceLookupInput(_RuntimeToolInput):
    inner_code: str = Field(
        ..., min_length=1, max_length=64, pattern=r"^[A-Za-z0-9_-]+$"
    )
    region_id: int | None = Field(default=None, ge=1)


class MaintenanceTaskProposalInput(_RuntimeToolInput):
    inner_code: str = Field(
        ..., min_length=1, max_length=64, pattern=r"^[A-Za-z0-9_-]+$"
    )
    description: str = Field(..., min_length=1, max_length=500)


class _ActionTarget(_ToolInput):
    inner_code: str = Field(
        ..., min_length=1, max_length=64, pattern=r"^[A-Za-z0-9_-]+$"
    )


class _ActionResult(_ToolInput):
    task_id: int = Field(..., ge=1)
    task_code: str = Field(..., min_length=1, max_length=64)


class _PublicAction(_ToolInput):
    action_id: str = Field(..., min_length=1, max_length=64)
    action_type: Literal["CREATE_MAINTENANCE_TASK"]
    status: Literal["PENDING", "APPROVED", "REJECTED", "SUCCEEDED", "FAILED", "EXPIRED"]
    target: _ActionTarget
    description: str = Field(..., min_length=1, max_length=500)
    impact: str = Field(..., min_length=1, max_length=256)
    expires_at: str | None = Field(default=None, max_length=128)
    result: _ActionResult | None = None


class _ResumeDecision(_ToolInput):
    action_id: str = Field(..., min_length=1, max_length=64)
    decision: Literal["approve", "reject"]


def create_business_data_tools(client: AgentToolClient) -> list[BaseTool]:
    """创建只从 ToolRuntime 读取身份与令牌的业务工具。"""

    @tool(
        "query_sales_summary",
        args_schema=SalesSummaryInput,
        response_format="content_and_artifact",
        description=(
            "Query exact sales order count, amount, average, and bounded trend from Java. "
            "Use for real-time sales analysis; dates are inclusive and at most 90 days."
        ),
    )
    async def query_sales_summary(
        start: str,
        end: str,
        runtime: ToolRuntime[AgentContext],
        granularity: str = "day",
        region_id: int | None = None,
    ) -> tuple[str, dict[str, Any]]:
        return await _invoke(
            client,
            runtime,
            "query_sales_summary",
            {
                "start": start,
                "end": end,
                "granularity": granularity,
                "region_id": region_id,
            },
        )

    @tool(
        "query_task_statistics",
        args_schema=TaskStatisticsInput,
        response_format="content_and_artifact",
        description=(
            "Query exact task counts by status and average completion minutes from Java. "
            "Use for current business task analysis within the caller's region."
        ),
    )
    async def query_task_statistics(
        start: str,
        end: str,
        runtime: ToolRuntime[AgentContext],
        task_type: int | None = None,
        region_id: int | None = None,
    ) -> tuple[str, dict[str, Any]]:
        return await _invoke(
            client,
            runtime,
            "query_task_statistics",
            {
                "start": start,
                "end": end,
                "task_type": task_type,
                "region_id": region_id,
            },
        )

    @tool(
        "query_abnormal_devices",
        args_schema=AbnormalDevicesInput,
        response_format="content_and_artifact",
        description=(
            "Query the current abnormal-device snapshot and a bounded safe device list from Java. "
            "This is current state, not historical fault-event counts."
        ),
    )
    async def query_abnormal_devices(
        runtime: ToolRuntime[AgentContext],
        limit: int = 10,
        region_id: int | None = None,
    ) -> tuple[str, dict[str, Any]]:
        return await _invoke(
            client,
            runtime,
            "query_abnormal_devices",
            {"limit": limit, "region_id": region_id},
        )

    @tool(
        "lookup_device",
        args_schema=DeviceLookupInput,
        response_format="content_and_artifact",
        description=(
            "Look up one vending machine's safe operational fields by exact inner code. "
            "Java enforces caller permission and region scope."
        ),
    )
    async def lookup_device(
        inner_code: str,
        runtime: ToolRuntime[AgentContext],
        region_id: int | None = None,
    ) -> tuple[str, dict[str, Any]]:
        return await _invoke(
            client,
            runtime,
            "lookup_device",
            {"inner_code": inner_code, "region_id": region_id},
        )

    @tool(
        "propose_maintenance_task",
        args_schema=MaintenanceTaskProposalInput,
        response_format="content_and_artifact",
        description=(
            "Propose creating one pending maintenance task for an exact device. "
            "This always pauses for the logged-in user's explicit approval and never "
            "changes inventory, device state, channels, or configuration."
        ),
    )
    async def propose_maintenance_task(
        inner_code: str,
        description: str,
        runtime: ToolRuntime[AgentContext],
    ) -> tuple[str, dict[str, Any]]:
        context = _require_write_context(runtime)
        tool_call_id = str(runtime.tool_call_id or "").strip()
        if not tool_call_id:
            raise AgentToolClientError(
                "ACTION_CONTEXT_REQUIRED", "受控写操作缺少稳定的工具调用标识"
            )
        idempotency_key = hashlib.sha256(
            f"{context.thread_id}:{tool_call_id}".encode()
        ).hexdigest()
        proposal_result = await _invoke_result(
            client,
            runtime,
            "propose_maintenance_task",
            {
                "inner_code": inner_code,
                "description": description,
                "idempotency_key": idempotency_key,
            },
        )
        proposal = _PublicAction.model_validate(proposal_result.data)
        resumed = _ResumeDecision.model_validate(
            interrupt(
                {
                    "type": "approval_required",
                    "action": proposal.model_dump(mode="json"),
                }
            )
        )
        if resumed.action_id != proposal.action_id:
            raise AgentToolClientError("ACTION_FORBIDDEN", "恢复决定与待确认动作不匹配")
        if resumed.decision == "reject":
            runtime.stream_writer(
                {
                    "type": "action_rejected",
                    "action": proposal.model_dump(mode="json"),
                }
            )
            return "用户已拒绝创建维修工单，未执行任何写操作。", {
                "provider": "lingxi-manage",
                "tool": "propose_maintenance_task",
                "status": "REJECTED",
            }

        executed_result = await _invoke_result(
            client,
            runtime,
            "execute_maintenance_task",
            {"action_id": proposal.action_id},
        )
        executed = _PublicAction.model_validate(executed_result.data)
        if executed.status != "SUCCEEDED" or executed.result is None:
            raise AgentToolClientError(
                "ACTION_EXECUTION_FAILED", "维修工单执行结果无效"
            )
        runtime.stream_writer(
            {
                "type": "action_completed",
                "action": executed.model_dump(mode="json"),
            }
        )
        return (
            f"维修工单已由登录用户批准并创建：工单编号 {executed.result.task_code}。",
            {
                "provider": "lingxi-manage",
                "tool": "propose_maintenance_task",
                "status": "SUCCEEDED",
                "task_code": executed.result.task_code,
            },
        )

    tools: list[BaseTool] = [
        query_sales_summary,
        query_task_statistics,
        query_abnormal_devices,
        lookup_device,
    ]
    if settings.agent_write_actions_enabled:
        tools.append(propose_maintenance_task)
    return tools


def _require_write_context(runtime: ToolRuntime[AgentContext]) -> AgentContext:
    context = runtime.context
    if (
        context is None
        or not context.checkpointed
        or not context.user_id
        or not context.thread_id
        or context.tool_access_token is None
    ):
        raise AgentToolClientError(
            "ACTION_CONTEXT_REQUIRED",
            "受控写操作必须在已登录且可恢复的持久会话中进行",
        )
    return context


async def _invoke_result(
    client: AgentToolClient,
    runtime: ToolRuntime[AgentContext],
    tool_name: str,
    arguments: dict[str, Any],
) -> ToolCallResult:
    context = runtime.context
    if context is None or context.tool_access_token is None:
        raise AgentToolClientError(
            "TOOL_UNAUTHORIZED", "当前对话没有可用的业务数据访问凭据"
        )
    runtime.stream_writer(
        {"type": "tool_progress", "tool": tool_name, "status": "started"}
    )
    try:
        result = await client.invoke(
            tool=tool_name,
            arguments=arguments,
            token=context.tool_access_token.get_secret_value(),
            agent_request_id=context.agent_request_id,
            thread_id=context.thread_id,
        )
    except AgentToolClientError as exc:
        runtime.stream_writer(
            {
                "type": "tool_progress",
                "tool": tool_name,
                "status": "failed",
                "code": exc.code,
            }
        )
        raise
    runtime.stream_writer(
        {"type": "tool_progress", "tool": tool_name, "status": "completed"}
    )
    return result


async def _invoke(
    client: AgentToolClient,
    runtime: ToolRuntime[AgentContext],
    tool_name: str,
    arguments: dict[str, Any],
) -> tuple[str, dict[str, Any]]:
    context = runtime.context
    if context is None or context.tool_access_token is None:
        raise AgentToolClientError(
            "TOOL_UNAUTHORIZED",
            "当前对话没有可用的业务数据访问凭据",
        )
    runtime.stream_writer(
        {"type": "tool_progress", "tool": tool_name, "status": "started"}
    )
    try:
        result = await client.invoke(
            tool=tool_name,
            arguments=arguments,
            token=context.tool_access_token.get_secret_value(),
            agent_request_id=context.agent_request_id,
            thread_id=context.thread_id,
        )
    except AgentToolClientError as exc:
        runtime.stream_writer(
            {
                "type": "tool_progress",
                "tool": tool_name,
                "status": "failed",
                "code": exc.code,
            }
        )
        raise
    rows = result.data.get("rows")
    result_count = len(rows) if isinstance(rows, list) else 0
    runtime.stream_writer(
        {
            "type": "tool_progress",
            "tool": tool_name,
            "status": "completed",
            "result_count": result_count,
        }
    )
    return _model_content(result), {
        "provider": "lingxi-manage",
        "tool": tool_name,
        "data": result.data,
        "metadata": result.metadata.model_dump(mode="json"),
    }


def _model_content(result: ToolCallResult) -> str:
    content = json.dumps(
        result.data, ensure_ascii=False, separators=(",", ":"), default=str
    )
    if len(content) <= settings.agent_tool_model_text_chars:
        return content
    reduced = {
        "dataset": result.data.get("dataset"),
        "scope": result.data.get("scope"),
        "time_range": result.data.get("time_range"),
        "metrics": result.data.get("metrics"),
        "dimensions": result.data.get("dimensions"),
        "unit": result.data.get("unit"),
        "truncated": True,
        "notice": "Rows omitted from model-visible text because of the safety size limit.",
    }
    return json.dumps(reduced, ensure_ascii=False, separators=(",", ":"), default=str)
