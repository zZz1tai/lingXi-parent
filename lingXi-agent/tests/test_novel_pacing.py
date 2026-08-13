"""节奏档位定义、上下文注入与分析链的离线测试。"""

from __future__ import annotations

from datetime import date

import pytest
from pydantic import ValidationError

from app.agents.novel_prompts import (
    NOVEL_PACING_ANALYSIS_SYSTEM_PROMPT,
    NOVEL_PACING_LEVELS,
    compose_novel_pacing_analysis_prompt,
    compose_novel_system_prompt,
    get_novel_pacing_level,
)
from app.agents.state import AgentContext
from app.main import app
from app.schemas.request import NovelPacingRequest, NovelWorkContext
from app.schemas.response import NovelPacingResponse


def test_pacing_levels_cover_five_levels_with_unique_ids() -> None:
    assert len(NOVEL_PACING_LEVELS) == 5
    ids = [level["id"] for level in NOVEL_PACING_LEVELS]
    assert len(set(ids)) == len(ids)
    assert set(ids) == {"relaxed", "steady", "balanced", "intense", "rapid"}
    for level in NOVEL_PACING_LEVELS:
        assert level["name"]
        assert level["description"]
        assert level["guidance"]


def test_get_novel_pacing_level_returns_known_or_none() -> None:
    assert get_novel_pacing_level("intense")["name"] == "紧凑"
    assert get_novel_pacing_level("rapid")["name"] == "激烈"
    assert get_novel_pacing_level("forged") is None
    assert get_novel_pacing_level(None) is None


def test_pacing_context_injected_into_system_prompt_when_set() -> None:
    context = AgentContext(
        novel_context={"work_name": "拾遗录", "pacing_level": "intense"}
    )
    prompt = compose_novel_system_prompt(
        context,
        search_available=True,
        general_tools_available=True,
        current_date=date(2026, 8, 7),
    )

    assert "## 作品节奏档位" in prompt
    assert "紧凑" in prompt
    assert "事件密度高、对话利落" in prompt


def test_pacing_context_omitted_when_level_missing_or_unknown() -> None:
    prompt = compose_novel_system_prompt(
        AgentContext(style="professional"),
        search_available=True,
        general_tools_available=True,
    )
    assert "## 作品节奏档位" not in prompt

    prompt = compose_novel_system_prompt(
        AgentContext(novel_context={"work_name": "x", "pacing_level": "forged"}),
        search_available=True,
        general_tools_available=True,
    )
    assert "## 作品节奏档位" not in prompt


def test_novel_work_context_accepts_pacing_level() -> None:
    context = NovelWorkContext(
        work_name="拾遗录",
        pacing_level="rapid",
    )
    assert context.pacing_level == "rapid"

    context = NovelWorkContext.model_validate(
        {"workName": "拾遗录", "pacingLevel": "relaxed"}
    )
    assert context.pacing_level == "relaxed"

    with pytest.raises(ValidationError):
        NovelWorkContext(work_name="拾遗录", pacing_level="forged")


def test_pacing_analysis_prompt_contains_target_level_and_content() -> None:
    prompt = compose_novel_pacing_analysis_prompt(
        work_name="拾遗录",
        genre="悬疑",
        chapter_title="第三章 雾中来客",
        pacing_level="intense",
        content="他推开了门。",
    )

    assert "《拾遗录》" in prompt
    assert "悬疑" in prompt
    assert "第三章 雾中来客" in prompt
    assert "目标档位：紧凑" in prompt
    assert "他推开了门。" in prompt


def test_pacing_analysis_prompt_defaults_without_target_level() -> None:
    prompt = compose_novel_pacing_analysis_prompt(
        work_name="拾遗录",
        content="正文。",
    )
    assert "未指定目标档位" in prompt


def test_pacing_analysis_system_prompt_declares_json_contract() -> None:
    for keyword in (
        "score",
        "levelNote",
        "事件密度",
        "PLODDING",
        "精修模板",
    ):
        assert keyword in NOVEL_PACING_ANALYSIS_SYSTEM_PROMPT


def test_pacing_request_validates_and_accepts_camel_case() -> None:
    request = NovelPacingRequest.model_validate(
        {
            "workName": "拾遗录",
            "chapterTitle": "第三章",
            "pacingLevel": "intense",
            "content": "正文内容。",
        }
    )
    assert request.pacing_level == "intense"
    assert request.content == "正文内容。"

    with pytest.raises(ValidationError):
        NovelPacingRequest(
            work_name="拾遗录",
            content="   ",
        )
    with pytest.raises(ValidationError):
        NovelPacingRequest(
            work_name="拾遗录",
            content="正文",
            pacing_level="forged",
        )


def test_pacing_analyze_route_is_registered() -> None:
    paths = app.openapi()["paths"]
    assert "/api/v1/novel/pacing/analyze" in paths
    assert "post" in paths["/api/v1/novel/pacing/analyze"]


def test_pacing_response_schema_accepts_camel_case() -> None:
    response = NovelPacingResponse.model_validate(
        {
            "success": True,
            "message": "analyzed",
            "data": {
                "score": 72,
                "scoreNote": "中等偏上",
                "level": "intense",
                "levelNote": "紧凑",
                "summary": "总体节奏紧凑。",
                "dimensions": [
                    {"name": "事件密度", "score": 80, "note": "事件密集"}
                ],
                "issues": [
                    {
                        "type": "NO_HOOK",
                        "position": "章末",
                        "issue": "缺钩子",
                        "suggestion": "补一个悬念",
                    }
                ],
                "suggestions": ["可用「章末钩子」模板精修"],
            },
        }
    )
    assert response.data.score == 72
    assert response.data.score_note == "中等偏上"
    assert response.data.issues[0]["type"] == "NO_HOOK"
