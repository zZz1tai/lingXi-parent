"""文本差异边界和真实工具经 Agent、HTTP、SSE 的离线验证。"""
import asyncio
import json
from unittest.mock import AsyncMock, patch

import httpx
import pytest
from langchain_core.language_models import BaseChatModel
from langchain_core.messages import AIMessage, ToolMessage
from langchain_core.outputs import ChatGeneration, ChatResult
from pydantic import SecretStr, ValidationError

from app.agents.builder import build_search_agent
from app.agents.prompts import compose_system_prompt
from app.agents.state import AgentContext
from app.agents.tools.text_compare import TextCompareError, compare_texts, create_text_compare_tool
from app.api import dependencies
from app.api.v1 import chat as chat_api
from app.main import app


def test_replacement_and_line_ranges():
    data = compare_texts('第一章\n旧结局\n', '第一章\n新结局\n补充\n')
    assert data['removed_line_count'] == 1
    assert data['added_line_count'] == 2
    assert data['changes'] == [{'kind': 'replace', 'before_range': {'start': 2, 'count': 1},
        'after_range': {'start': 2, 'count': 2}, 'before_text': '旧结局\n',
        'after_text': '新结局\n补充\n', 'truncated': False}]


def test_empty_insert_delete_and_repeated_lines():
    assert compare_texts('', '')['identical']
    inserted = compare_texts('', '正文')['changes'][0]
    assert inserted['kind'] == 'insert'
    assert inserted['before_range'] == {'start': 1, 'count': 0}
    assert compare_texts('正文', '')['changes'][0]['kind'] == 'delete'
    assert compare_texts('重复\n重复\n', '重复\n重复\n')['changed_block_count'] == 0


def test_line_endings_whitespace_and_case():
    data = compare_texts('甲\r\n乙\r', '甲\n乙\n')
    assert not data['identical'] and data['equivalent_under_options']
    assert not compare_texts('甲', '甲\n')['equivalent_under_options']
    assert not compare_texts(' a  b ', 'a b')['equivalent_under_options']
    assert compare_texts(' a\t b \n', 'a b', True)['equivalent_under_options']
    assert not compare_texts('a', 'A', True)['equivalent_under_options']
    assert not compare_texts('a\n\nb', 'a\nb', True)['equivalent_under_options']


@pytest.mark.parametrize('value', ['x' * 20001, 'x\n' * 301, '私密原文\x00'], ids=['characters', 'lines', 'nul'])
def test_invalid_inputs_rejected_on_either_side(value):
    for before, after in [(value, ''), ('', value)]:
        with pytest.raises(TextCompareError) as error:
            compare_texts(before, after)
        assert '私密原文' not in str(error.value)


def test_limits_and_explicit_truncation():
    assert compare_texts('x\n' * 300, 'x\n' * 300)['identical']
    data = compare_texts('甲' * 20000, '乙' * 20000)
    assert data['truncated'] and len(data['changes'][0]['before_text']) == 1000
    before = ''.join(f'原{i}\n固定{i}\n' for i in range(25))
    after = ''.join(f'新{i}\n固定{i}\n' for i in range(25))
    data = compare_texts(before, after)
    assert data['changed_block_count'] == 25 and data['returned_block_count'] == 20
    assert data['removed_line_count'] == data['added_line_count'] == 25
    assert data['truncated']
    before = ''.join('甲' * 900 + f'\n固定{i}\n' for i in range(10))
    after = ''.join('乙' * 900 + f'\n固定{i}\n' for i in range(10))
    data = compare_texts(before, after)
    assert sum(len(c['before_text']) + len(c['after_text']) for c in data['changes']) == 8000
    assert data['truncated'] and data['changes'][-1]['truncated']


def test_tool_schema_and_prompt():
    schema_type = create_text_compare_tool().tool_call_schema
    schema = schema_type.model_json_schema()
    assert 'runtime' not in schema['properties']
    assert schema['properties']['before']['maxLength'] == 20000
    with pytest.raises(ValidationError):
        schema_type.model_validate({'before': '', 'after': '', 'ignore_whitespace': 'false'})
    assert 'compare_text' not in compose_system_prompt(None, search_available=False)
    assert 'compare_text' in compose_system_prompt(None, search_available=False, text_compare_available=True)


class CompareModel(BaseChatModel):
    """仅替代模型决策，答案依赖实际工具结果。"""
    before: str = '第一章\n旧结局\n'

    @property
    def _llm_type(self):
        return 'offline-compare-model'

    def bind_tools(self, tools, **kwargs):
        assert 'compare_text' in [tool.name for tool in tools]
        return self

    def _generate(self, messages, stop=None, run_manager=None, **kwargs):
        if isinstance(messages[-1], ToolMessage):
            if messages[-1].status == 'error':
                message = AIMessage(content=messages[-1].content)
            else:
                data = json.loads(messages[-1].content)
                assert data['changes'][0]['before_range']['start'] == 2
                message = AIMessage(content=f"发现 {data['changed_block_count']} 处变化")
        else:
            message = AIMessage(content='', tool_calls=[{'name': 'compare_text',
                'args': {'before': self.before, 'after': '第一章\n新结局\n'},
                'id': 'compare-call', 'type': 'tool_call'}])
        return ChatResult(generations=[ChatGeneration(message=message)])


@pytest.mark.parametrize('path', ['/api/v1/chat/invoke', '/api/v1/chat/stream'])
def test_authenticated_http_real_tool(path):
    async def run():
        with patch.object(dependencies, 'get_default_tools', return_value=[]), patch.object(dependencies.settings, 'weather_enabled', False):
            graph = build_search_agent(model=CompareModel(), tools=dependencies._runtime_tools())
        with (
            patch('app.api.auth.settings.service_api_key', SecretStr('offline-test-key')),
            patch.object(chat_api, 'get_agent', return_value=graph),
            patch.object(chat_api, 'create_agent_context', return_value=AgentContext(user_id='compare-test', thread_id='compare-thread')),
            patch.object(chat_api, '_recall_preferences', AsyncMock(return_value=[])),
            patch.object(chat_api, '_capture_preferences', AsyncMock(return_value=[])),
        ):
            async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url='http://testserver') as client:
                response = await client.post(path, headers={'X-Agent-Service-Key': 'offline-test-key'},
                    json={'message': '核对两版原文', 'user_id': 'compare-test', 'thread_id': 'compare-thread'})
        assert response.status_code == 200, response.text
        if path.endswith('/invoke'):
            assert '发现 1 处变化' in response.json()['data']['response']
        else:
            events = [json.loads(line[6:]) for line in response.text.splitlines() if line.startswith('data: ') and line != 'data: [DONE]']
            assert [e['data']['status'] for e in events if e['type'] == 'tool_progress' and e['tool'] == 'compare_text'] == ['started', 'completed']
            assert any(e['type'] == 'done' for e in events)
            assert not any(e['type'] == 'error' for e in events)
            assert '发现 1 处变化' in response.text and '[DONE]' in response.text
    asyncio.run(run())


def test_safe_tool_failure_and_progress():
    async def run():
        graph = build_search_agent(model=CompareModel(before='私密原文\x00'), tools=[create_text_compare_tool()])
        events, errors = [], []
        async for mode, chunk in graph.astream({'messages': [('user', '对比')]}, context=AgentContext(user_id='test'), stream_mode=['custom', 'updates']):
            if mode == 'custom':
                events.append(chunk)
            elif 'tools' in chunk:
                errors.append(chunk['tools']['messages'][0])
        assert len(errors) == 1 and errors[0].status == 'error'
        assert 'TOOL_INVALID_INPUT' in errors[0].content and '私密原文' not in errors[0].content
        assert [event['status'] for event in events] == ['started', 'failed']
    asyncio.run(run())
