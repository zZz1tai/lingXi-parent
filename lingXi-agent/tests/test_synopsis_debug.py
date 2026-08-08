from __future__ import annotations

import asyncio
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch

from langchain_core.messages import AIMessage

from app.api.v1 import novel as novel_api


def run(coro):
    return asyncio.run(coro)


def test_synopsis_endpoint_with_fake_llm() -> None:
    model = SimpleNamespace(
        ainvoke=AsyncMock(return_value=AIMessage(content="雾隐城下，少年剑客江离持一盏旧灯走入雾中……"))
    )

    with patch.object(novel_api, "create_llm", return_value=model) as create_llm:
        response = run(
            novel_api.novel_synopsis_generate(
                novel_api.NovelSynopsisRequest(
                    work_name="雾隐城",
                    work_type="novel",
                    genre="东方玄幻",
                ),
                request_id="req-synopsis-test",
            )
        )

    print("RESULT success:", response.success)
    print("RESULT synopsis:", repr(response.data.synopsis))
    print("RESULT create_llm called:", create_llm.call_args)
    messages = model.ainvoke.await_args.args[0]
    print("RESULT messages:", [m for m in messages])
    assert response.success
    assert "雾隐城" in response.data.synopsis


def test_synopsis_endpoint_handles_block_content() -> None:
    model = SimpleNamespace(
        ainvoke=AsyncMock(return_value=AIMessage(content=[{"type": "text", "text": "梗概A"}, {"type": "text", "text": "梗概B"}]))
    )

    with patch.object(novel_api, "create_llm", return_value=model):
        response = run(
            novel_api.novel_synopsis_generate(
                novel_api.NovelSynopsisRequest(work_name="雨夜来电", work_type="short"),
                request_id="req-synopsis-test-2",
            )
        )

    print("RESULT2 synopsis:", repr(response.data.synopsis))
    assert response.data.synopsis == "梗概A梗概B"
