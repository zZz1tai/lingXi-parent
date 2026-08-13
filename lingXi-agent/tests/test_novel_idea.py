"""小说构思 Agent：块协议解析、校验与提示词的离线测试。"""

from __future__ import annotations

import asyncio
import pytest
from langchain_core.messages import AIMessage
from pydantic import ValidationError
from types import SimpleNamespace

from app.agents.novel_idea import (
    IDEA_ASK_CLOSE,
    IDEA_ASK_OPEN,
    IDEA_DOC_CLOSE,
    IDEA_DOC_OPEN,
    IdeaTagScrubber,
    compose_novel_idea_system_prompt,
    validate_idea_ask,
    validate_idea_doc,
)
from app.agents.state import AgentContext
from app.main import app
from app.schemas.request import NovelIdeaRequest
from app.schemas.response import StreamEvent


class _FakeIdeaAgent:
    async def astream(self, *args, **kwargs):
        del args, kwargs
        yield (
            "messages",
            (
                AIMessage(
                    content=(
                        f'{IDEA_ASK_OPEN}{"{\"questions\":[{\"question\":\"主角是谁？\"}]}"}'
                        f"{IDEA_ASK_CLOSE}"
                    )
                ),
                {},
            ),
        )


class _FakeUnclosedIdeaAgent:
    async def astream(self, *args, **kwargs):
        del args, kwargs
        yield (
            "messages",
            (
                AIMessage(
                    content=(
                        f'{IDEA_ASK_OPEN}{"{\"questions\":[{\"question\":\"主角是谁？\"}]}"}'
                    )
                ),
                {},
            ),
        )


def test_scrubber_extracts_ask_block_keeps_clean_text() -> None:
    scrubber = IdeaTagScrubber()
    text = (
        f"好的，我先确认一下。{IDEA_ASK_OPEN}"
        '{"questions": [{"question": "主角是什么职业？", "hint": "例如退伍军人/考古学家"}]}'
        f"{IDEA_ASK_CLOSE}"
    )
    clean = scrubber.push(text)
    assert clean == "好的，我先确认一下。"
    assert scrubber.blocks == [
        (
            "ask",
            {
                "questions": [
                    {"question": "主角是什么职业？", "hint": "例如退伍军人/考古学家"}
                ]
            },
        )
    ]
    assert scrubber.flush_text() == ""


def test_scrubber_handles_tag_split_across_chunks() -> None:
    scrubber = IdeaTagScrubber()
    first = scrubber.push(f"开场白 {IDEA_ASK_OPEN}")
    assert first == "开场白 "
    assert scrubber.blocks == []
    second = scrubber.push(
        '{"questions":[{"question":"背景是古代还是现代？"}]}'
    )
    assert second == ""
    assert scrubber.blocks == []
    third = scrubber.push(IDEA_ASK_CLOSE + "（结束）")
    assert third == "（结束）"
    assert scrubber.blocks == [
        ("ask", {"questions": [{"question": "背景是古代还是现代？"}]})
    ]


def test_scrubber_does_not_hold_normal_text_tail() -> None:
    scrubber = IdeaTagScrubber()
    out = scrubber.push("这本小说讲一个在沙漠里卖伞的人。")
    assert out == "这本小说讲一个在沙漠里卖伞的人。"
    assert scrubber.flush_text() == ""


def test_scrubber_extracts_doc_block_and_flushes_unfinished_tail() -> None:
    scrubber = IdeaTagScrubber()
    clean = scrubber.push(
        f"{IDEA_DOC_OPEN}"
        '{"work_name":"沙海伞匠","genre":"末世","tone":"压抑坚韧"}'
        f"{IDEA_DOC_CLOSE}"
    )
    assert clean == ""
    assert scrubber.blocks[0][0] == "doc"
    assert scrubber.blocks[0][1]["work_name"] == "沙海伞匠"
    assert scrubber.flush_text() == ""


def test_scrubber_recovers_valid_unclosed_block_without_leaking_protocol() -> None:
    scrubber = IdeaTagScrubber()
    assert scrubber.push(
        f'{IDEA_ASK_OPEN}{"{\"questions\":[{\"question\":\"主角是谁？\"}]}"}'
    ) == ""
    assert scrubber.flush_text() == ""
    assert scrubber.blocks == [
        ("ask", {"questions": [{"question": "主角是谁？"}]})
    ]


def test_scrubber_discards_invalid_unclosed_block_without_leaking_protocol() -> None:
    scrubber = IdeaTagScrubber()
    assert scrubber.push(f"{IDEA_ASK_OPEN}未闭合的内容") == ""
    assert scrubber.blocks == []
    assert scrubber.flush_text() == ""
    assert scrubber.blocks == []


def test_scrubber_ignores_invalid_json_block() -> None:
    scrubber = IdeaTagScrubber()
    scrubber.push(f"{IDEA_ASK_OPEN}not-json{IDEA_ASK_CLOSE}")
    assert scrubber.blocks == []
    assert scrubber.flush_text() == ""


def test_validate_idea_ask_accepts_one_or_two_questions() -> None:
    payload = validate_idea_ask(
        {
            "questions": [
                {"question": "主角是谁？", "hint": "给个身份"},
                {"question": "核心冲突？"},
            ]
        }
    )
    assert len(payload["questions"]) == 2
    assert payload["questions"][1]["hint"] == ""


def test_validate_idea_ask_rejects_blank_or_missing() -> None:
    with pytest.raises(ValueError):
        validate_idea_ask({"questions": []})
    with pytest.raises(ValueError):
        validate_idea_ask({"questions": [{"question": "  "}]})
    with pytest.raises(ValueError):
        validate_idea_ask({})


def test_validate_idea_doc_normalizes_full_document() -> None:
    doc = validate_idea_doc(
        {
            "work_name": "天气预报不准的第七年",
            "genre": "末世科幻",
            "one_liner": "在必死的雨区里，有人靠播报假天气活下去。",
            "logline": "少女气象员与逃兵在酸雨区求生。",
            "protagonists": [
                {
                    "name": "林晚晴",
                    "role": "气象员",
                    "trait": "冷静执拗",
                    "goal": "找到晴区",
                    "gimmick": "预知雷暴",
                }
            ],
            "supporting": [{"name": "老周", "role": "仓库管理员", "trait": "唠叨热心"}],
            "antagonists": [{"name": "白鸦", "role": "雨区军阀", "trait": "贪婪多疑"}],
            "core_conflict": "晴区是否真实存在",
            "theme": "希望与谎言",
            "tone": "压抑坚韧",
            "setting": {
                "world_building": "连年酸雨，划分雨区",
                "time_period": "近未来",
                "location": "旧上海废墟",
            },
            "magic_system": "预知雷暴二十四小时",
            "key_scenes": [
                {"title": "首次误报", "description": "报错天气导致一列车队团灭"}
            ],
            "ending_hint": "真相是晴区即基地实验场",
            "selling_points": ["天气金手指", "反套路求生"],
        }
    )
    assert doc["work_name"] == "天气预报不准的第七年"
    assert len(doc["protagonists"]) == 1
    assert doc["protagonists"][0]["gimmick"] == "预知雷暴"
    assert len(doc["antagonists"]) == 1
    assert doc["key_scenes"][0]["title"] == "首次误报"
    assert doc["selling_points"] == ["天气金手指", "反套路求生"]


def test_validate_idea_doc_rejects_missing_required_fields() -> None:
    with pytest.raises(ValueError):
        validate_idea_doc({"genre": "末世", "protagonists": []})
    with pytest.raises(ValueError):
        validate_idea_doc(
            {"work_name": "a", "genre": "末世", "protagonists": [{"trait": "x"}]}
        )
    with pytest.raises(ValueError):
        validate_idea_doc({"work_name": "", "genre": "", "protagonists": []})


def test_validate_idea_doc_skips_invalid_members() -> None:
    doc = validate_idea_doc(
        {
            "work_name": "a",
            "genre": "悬疑",
            "protagonists": [
                {"name": "甲", "role": "侦探"},
                {"trait": "没有名字"},
                {"name": "乙", "role": "记者"},
            ],
            "supporting": [{"trait": "无名字被跳过"}],
        }
    )
    assert [p["name"] for p in doc["protagonists"]] == ["甲", "乙"]
    assert doc["supporting"] == []


def test_idea_system_prompt_includes_protocol_and_capabilities() -> None:
    prompt = compose_novel_idea_system_prompt(
        AgentContext(style="professional"),
        search_available=True,
        general_tools_available=True,
    )
    assert "【IDEA_ASK】" not in prompt
    assert "[IDEA_ASK]" in prompt
    assert "[IDEA_DOC]" in prompt
    assert "公网搜索" in prompt


def test_idea_request_rejects_blank_message() -> None:
    with pytest.raises(ValidationError):
        NovelIdeaRequest(
            message="   ",
            user_id="u1",
            thread_id="idea-1",
        )
    with pytest.raises(ValidationError):
        NovelIdeaRequest(
            message="x",
            user_id="u1",
            thread_id="bad thread id!",
        )


def test_idea_request_accepts_session_alias() -> None:
    request = NovelIdeaRequest(
        message="会下雨的沙漠",
        user_id="u1",
        session_id="idea-session-001",
    )
    assert request.thread_id == "idea-session-001"


def test_stream_event_type_includes_idea_doc() -> None:
    event = StreamEvent(
        type="idea_doc",
        content="构思完成",
        data={"doc": {"work_name": "a"}},
    )
    assert event.type == "idea_doc"
    assert event.data["doc"]["work_name"] == "a"


def test_idea_endpoint_registered() -> None:
    paths = app.openapi()["paths"]
    assert "/api/v1/novel/idea/stream" in paths
    assert "/api/v1/novel/write/stream" in paths


def test_idea_stream_turns_protocol_block_into_clarification(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    from app.api.v1 import novel

    monkeypatch.setattr(
        novel,
        "create_novel_agent_context",
        lambda **kwargs: SimpleNamespace(model=object()),
    )
    monkeypatch.setattr(
        novel,
        "get_novel_idea_agent",
        lambda **kwargs: _FakeIdeaAgent(),
    )
    request = NovelIdeaRequest(
        message="会下雨的沙漠",
        user_id="u1",
        thread_id="idea-stream-001",
    )

    async def collect() -> list[str]:
        return [
            event
            async for event in novel._stream_idea_events(request, "req-test")
        ]

    events = asyncio.run(collect())

    assert any('"type":"clarification"' in event for event in events)
    assert any('"type":"done"' in event for event in events)
    assert not any("[IDEA_ASK]" in event for event in events)


def test_idea_stream_recovers_unclosed_protocol_without_leaking(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    from app.api.v1 import novel

    monkeypatch.setattr(
        novel,
        "create_novel_agent_context",
        lambda **kwargs: SimpleNamespace(model=object()),
    )
    monkeypatch.setattr(
        novel,
        "get_novel_idea_agent",
        lambda **kwargs: _FakeUnclosedIdeaAgent(),
    )
    request = NovelIdeaRequest(
        message="抖音通三界",
        user_id="u1",
        thread_id="idea-stream-unclosed-001",
    )

    async def collect() -> list[str]:
        return [
            event
            async for event in novel._stream_idea_events(request, "req-unclosed")
        ]

    events = asyncio.run(collect())

    assert any('"type":"clarification"' in event for event in events)
    assert any("主角是谁？" in event for event in events)
    assert not any("[IDEA_ASK]" in event for event in events)
