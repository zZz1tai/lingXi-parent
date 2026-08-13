"""精修模板库与标记解析的离线测试。"""

from __future__ import annotations

import pytest

from app.agents.novel_prompts import (
    NOVEL_POLISH_CATEGORIES,
    NOVEL_POLISH_TEMPLATES,
    compose_novel_polish_instruction,
    compose_novel_style_instruction,
    get_novel_polish_template,
    parse_polish_message,
    parse_style_message,
)


def test_polish_template_library_has_22_scenarios() -> None:
    assert len(NOVEL_POLISH_TEMPLATES) == 22


def test_polish_template_ids_and_names_are_unique() -> None:
    ids = [template["id"] for template in NOVEL_POLISH_TEMPLATES]
    names = [template["name"] for template in NOVEL_POLISH_TEMPLATES]
    assert len(set(ids)) == len(ids)
    assert len(set(names)) == len(names)


def test_polish_template_fields_are_complete() -> None:
    for template in NOVEL_POLISH_TEMPLATES:
        assert template["id"]
        assert template["name"]
        assert template["description"]
        assert template["instruction"]
        assert template["category"] in NOVEL_POLISH_CATEGORIES


def test_polish_template_ids_match_marker_spec() -> None:
    for template in NOVEL_POLISH_TEMPLATES:
        assert template["id"].isidentifier()


def test_get_novel_polish_template_finds_known_id() -> None:
    template = get_novel_polish_template("de_ai_flavor")
    assert template is not None
    assert template["name"] == "去AI味"
    assert get_novel_polish_template("not-exist") is None


def test_parse_polish_message_returns_template_and_target() -> None:
    parsed = parse_polish_message(
        "【精修】de_ai_flavor\n他走进了房间，显得很生气。"
    )
    assert parsed == ("de_ai_flavor", "他走进了房间，显得很生气。")


def test_parse_polish_message_trims_surrounding_whitespace() -> None:
    parsed = parse_polish_message("  【精修】  pace_accelerate  \n  目标文字  ")
    assert parsed == ("pace_accelerate", "目标文字")


def test_parse_polish_message_without_target_returns_empty_string() -> None:
    parsed = parse_polish_message("【精修】chapter_open_hook")
    assert parsed == ("chapter_open_hook", "")


def test_parse_polish_message_rejects_unknown_template() -> None:
    assert parse_polish_message("【精修】not-a-template\n正文") is None
    assert parse_polish_message("【精修】\n正文") is None


def test_parse_polish_message_rejects_plain_message() -> None:
    assert parse_polish_message("续写下一段") is None
    assert parse_polish_message("") is None
    assert parse_polish_message("【精修】") is None


def test_compose_polish_instruction_contains_template_and_target() -> None:
    instruction = compose_novel_polish_instruction(
        "suspense_add", "他推开了那扇门。"
    )

    assert "「悬念加强」" in instruction
    assert "强化悬念" in instruction
    assert "## 目标文字" in instruction
    assert "他推开了那扇门。" in instruction
    assert "精修后正文" in instruction
    assert "修改点标注" in instruction


def test_compose_polish_instruction_requires_target_text() -> None:
    with pytest.raises(ValueError, match="目标文字"):
        compose_novel_polish_instruction("de_ai_flavor", "  ")


def test_compose_polish_instruction_rejects_unknown_template() -> None:
    with pytest.raises(ValueError, match="未知精修模板"):
        compose_novel_polish_instruction("forged", "正文")


def test_parse_style_message_returns_title_body_and_target() -> None:
    parsed = parse_style_message(
        "【文风】冷峻硬派\n短句、少修饰、克制留白。\n\n他推开了门。"
    )
    assert parsed == ("冷峻硬派", "短句、少修饰、克制留白。", "他推开了门。")


def test_parse_style_message_without_target_returns_empty_string() -> None:
    parsed = parse_style_message("【文风】诙谐\n多用比喻与吐槽。")
    assert parsed == ("诙谐", "多用比喻与吐槽。", "")


def test_parse_style_message_rejects_malformed_messages() -> None:
    assert parse_style_message("续写下一段") is None
    assert parse_style_message("【文风】只有标题") is None
    assert parse_style_message("【文风】\n正文") is None
    assert parse_style_message("") is None


def test_compose_style_instruction_contains_style_and_target() -> None:
    instruction = compose_novel_style_instruction(
        "冷峻硬派", "短句、少修饰、克制留白。", "他推开了门。"
    )

    assert "「冷峻硬派」文风" in instruction
    assert "短句、少修饰、克制留白。" in instruction
    assert "## 目标文字" in instruction
    assert "他推开了门。" in instruction
    assert "改写后正文" in instruction
    assert "修改点标注" in instruction


def test_compose_style_instruction_requires_all_fields() -> None:
    with pytest.raises(ValueError, match="文风标题"):
        compose_novel_style_instruction(" ", "内容", "正文")
    with pytest.raises(ValueError, match="文风内容"):
        compose_novel_style_instruction("标题", " ", "正文")
    with pytest.raises(ValueError, match="目标文字"):
        compose_novel_style_instruction("标题", "内容", "  ")
