from __future__ import annotations

import asyncio
import json
from datetime import date
from decimal import Decimal

import pytest
from langchain_core.messages import AIMessage, ToolMessage
from langgraph.graph import END, START, MessagesState, StateGraph
from langgraph.prebuilt import ToolNode
from pydantic import ValidationError

from app.agents.state import AgentContext
from app.agents.tools.general import (
    CalculatorInput,
    CurrentDateTimeInput,
    DateCalculatorInput,
    UnitConversionInput,
    create_general_tools,
)


def _tool_graph():
    builder = StateGraph(MessagesState, context_schema=AgentContext)
    builder.add_node(
        "tools", ToolNode(create_general_tools(), handle_tool_errors=False)
    )
    builder.add_edge(START, "tools")
    builder.add_edge("tools", END)
    return builder.compile()


def _tool_state(name: str, arguments: dict[str, object]) -> dict[str, object]:
    return {
        "messages": [
            AIMessage(
                content="",
                tool_calls=[
                    {
                        "name": name,
                        "args": arguments,
                        "id": f"{name}-call-1",
                        "type": "tool_call",
                    }
                ],
            )
        ]
    }


async def _current_datetime_runs_through_tool_node_with_progress() -> None:
    events: list[dict[str, object]] = []
    final_update: dict[str, object] | None = None

    async for mode, chunk in _tool_graph().astream(
        _tool_state("get_current_datetime", {"timezone": "Asia/Shanghai"}),
        context=AgentContext(user_id="42"),
        stream_mode=["custom", "updates"],
    ):
        if mode == "custom":
            events.append(chunk)
        else:
            final_update = chunk

    assert [event["status"] for event in events] == ["started", "completed"]
    assert final_update is not None
    message = final_update["tools"]["messages"][0]  # type: ignore[index]
    assert isinstance(message, ToolMessage)
    assert message.status == "success"
    assert message.artifact["provider"] == "local"
    content = json.loads(message.content)
    assert content["timezone"] == "Asia/Shanghai"
    assert content["utc_offset"] == "+08:00"
    assert content["weekday"].startswith("星期")


def test_current_datetime_runs_through_tool_node_with_progress() -> None:
    asyncio.run(_current_datetime_runs_through_tool_node_with_progress())


async def _calculator_is_exact_and_rejects_code() -> None:
    output = await _tool_graph().ainvoke(
        _tool_state("calculate", {"expression": "(12.5 + 7.5) * 3 / 2"}),
        context=AgentContext(user_id="42"),
    )
    message = output["messages"][-1]
    assert isinstance(message, ToolMessage)
    assert json.loads(message.content)["result"] == "30"

    tool = next(item for item in create_general_tools() if item.name == "calculate")
    assert tool.coroutine is not None
    floor_content, _ = await tool.coroutine(
        expression="-3 // 2",
        runtime=type("Runtime", (), {"stream_writer": lambda *_args: None})(),
    )
    modulo_content, _ = await tool.coroutine(
        expression="-3 % 2",
        runtime=type("Runtime", (), {"stream_writer": lambda *_args: None})(),
    )
    assert json.loads(floor_content)["result"] == "-2"
    assert json.loads(modulo_content)["result"] == "1"

    with pytest.raises(ValueError, match="不支持"):
        await tool.coroutine(
            expression="__import__('os').system('whoami')",
            runtime=type("Runtime", (), {"stream_writer": lambda *_args: None})(),
        )


def test_calculator_is_exact_and_rejects_code() -> None:
    asyncio.run(_calculator_is_exact_and_rejects_code())


async def _unit_conversion_supports_chinese_aliases_and_temperature() -> None:
    tools = {tool.name: tool for tool in create_general_tools()}
    runtime = type("Runtime", (), {"stream_writer": lambda *_args: None})()

    convert = tools["convert_units"]
    assert convert.coroutine is not None
    length_content, _ = await convert.coroutine(
        value=Decimal(5),
        from_unit="公里",
        to_unit="米",
        runtime=runtime,
    )
    temperature_content, _ = await convert.coroutine(
        value=Decimal(100),
        from_unit="摄氏度",
        to_unit="华氏度",
        runtime=runtime,
    )

    assert json.loads(length_content)["output"] == {"value": "5000", "unit": "m"}
    assert json.loads(temperature_content)["output"] == {"value": "212", "unit": "f"}

    with pytest.raises(ValueError, match="同一换算维度"):
        await convert.coroutine(
            value=Decimal(1),
            from_unit="kg",
            to_unit="m",
            runtime=runtime,
        )


def test_unit_conversion_supports_chinese_aliases_and_temperature() -> None:
    asyncio.run(_unit_conversion_supports_chinese_aliases_and_temperature())


async def _date_calculator_adds_and_compares_dates() -> None:
    tool = next(
        item for item in create_general_tools() if item.name == "date_calculator"
    )
    assert tool.coroutine is not None
    runtime = type("Runtime", (), {"stream_writer": lambda *_args: None})()

    added_content, _ = await tool.coroutine(
        operation="add_days",
        start_date=date(2026, 7, 31),
        days=10,
        end_date=None,
        include_end=False,
        runtime=runtime,
    )
    difference_content, _ = await tool.coroutine(
        operation="days_between",
        start_date=date(2026, 7, 31),
        days=None,
        end_date=date(2026, 8, 10),
        include_end=True,
        runtime=runtime,
    )

    assert json.loads(added_content)["result_date"] == "2026-08-10"
    assert json.loads(difference_content)["signed_days"] == 10
    assert json.loads(difference_content)["inclusive_days"] == 11


def test_date_calculator_adds_and_compares_dates() -> None:
    asyncio.run(_date_calculator_adds_and_compares_dates())


def test_general_tool_runtime_is_hidden_and_inputs_are_bounded() -> None:
    for tool in create_general_tools():
        assert "runtime" in tool.get_input_schema().model_fields
        assert "runtime" not in tool.tool_call_schema.model_json_schema()["properties"]

    with pytest.raises(ValidationError):
        CalculatorInput.model_validate({"expression": "1\n+2", "runtime": object()})
    with pytest.raises(ValidationError):
        CurrentDateTimeInput.model_validate(
            {"timezone": "Mars/Olympus", "runtime": object()}
        )
    with pytest.raises(ValidationError):
        UnitConversionInput.model_validate(
            {
                "value": "Infinity",
                "from_unit": "m",
                "to_unit": "km",
                "runtime": object(),
            }
        )
    with pytest.raises(ValidationError):
        DateCalculatorInput.model_validate(
            {
                "operation": "days_between",
                "start_date": "2026-07-31",
                "runtime": object(),
            }
        )
