"""CSV 统计边界与真实 Agent → 工具 → HTTP/SSE 离线集成验证。"""
from __future__ import annotations

import asyncio
import json
from unittest.mock import AsyncMock, patch

import httpx
import pytest
from langchain_core.language_models import BaseChatModel
from langchain_core.messages import AIMessage, ToolMessage
from langchain_core.outputs import ChatGeneration, ChatResult

from app.agents.builder import build_search_agent
from app.agents.prompts import compose_system_prompt
from app.agents.state import AgentContext
from app.agents.tools.csv_analysis import CsvAnalysisError, analyze_csv_text, create_csv_analysis_tool
from app.api import dependencies
from app.api.v1 import chat as chat_api
from app.main import app
from pydantic import SecretStr

SAMPLE = '地区,金额\n华东,10.10\n华东,20.20\n华北,\n华北,待补'


def test_exact_statistics_and_groups_do_not_treat_missing_as_zero():
    result = analyze_csv_text(SAMPLE, group_by="地区", value_column="金额")
    amount = result["columns"][1]
    assert amount == {"name":"金额", "row_count":4, "numeric_count":2, "missing_count":1,
                      "non_numeric_count":1, "sum":"30.3", "mean":"15.15", "min":"10.1", "max":"20.2"}
    assert result["groups"][0]["sum"] == "30.3"
    assert result["groups"][1]["sum"] is None
    assert result["source"] == "user_provided_csv"


def test_csv_quotes_bom_tsv_negative_values_and_rounding():
    result = analyze_csv_text('\ufeff类别;数值\n"含;分号\n和换行";-0.1\n普通;0.2\n普通;0', ';')
    assert result["row_count"] == 3
    assert result["columns"][1]["sum"] == "0.1"
    assert result["columns"][1]["mean"] == "0.033333"
    assert analyze_csv_text('a\tb\n甲\t2', '\t')["columns"][1]["sum"] == '2'
    values = analyze_csv_text('值\n10%\n"1,000"\n1e3\nNaN\n=1+1\n0')["columns"][0]
    assert values["non_numeric_count"] == 5
    assert values["numeric_count"] == 1
    assert values["sum"] == '0'


@pytest.mark.parametrize('text,kwargs', [
    ('a,a\n1,2', {}), ('a,\n1,2', {}), ('a,b\n1', {}), ('a\n"unclosed', {}), ('a', {}),
    ('a\n1\n\n2', {}), ('a\n' + '1\n' * 1001, {}), ('x' * 50001, {}), ('a\n\x00', {}),
    ('a\n1', {'delimiter':'|'}), ('a\n1', {'group_by':'a'}),
    ('a\n1', {'group_by':'b', 'value_column':'a'}),
    ('a\n1000000000001', {}), ('a\n0.0000001', {}),
    ('a,b\n' + '\n'.join(f'{i},1' for i in range(51)), {'group_by':'a','value_column':'b'}),
    (','.join(f'c{i}' for i in range(21)) + '\n' + ','.join('1' for _ in range(21)), {}),
], ids=['duplicate-header', 'empty-header', 'ragged-row', 'bad-quote', 'header-only',
        'blank-row', 'too-many-rows', 'too-long', 'nul', 'delimiter', 'missing-value-column',
        'unknown-column', 'large-number', 'precision', 'too-many-groups', 'too-many-columns'])
def test_invalid_or_oversized_data_is_rejected_without_partial_results(text, kwargs):
    with pytest.raises(CsvAnalysisError):
        analyze_csv_text(text, **kwargs)


def test_schema_hides_runtime_and_prompt_only_advertises_registered_tool():
    tool = create_csv_analysis_tool()
    schema = tool.tool_call_schema.model_json_schema()
    assert 'runtime' not in schema['properties']
    assert schema['properties']['csv_text']['maxLength'] == 50000
    assert 'analyze_csv' not in compose_system_prompt(None, search_available=False)
    assert 'analyze_csv' in compose_system_prompt(None, search_available=False, csv_analysis_available=True)


class CsvToolModel(BaseChatModel):
    """仅模拟模型决策，统计和返回答案均读取真实工具结果。"""
    csv_text: str = SAMPLE
    @property
    def _llm_type(self):
        return 'offline-csv-tool-model'

    def bind_tools(self, tools, **kwargs):
        assert 'analyze_csv' in [tool.name for tool in tools]
        return self

    def _generate(self, messages, stop=None, run_manager=None, **kwargs):
        if isinstance(messages[-1], ToolMessage):
            if messages[-1].status == 'error':
                return ChatResult(generations=[ChatGeneration(message=AIMessage(content=messages[-1].content))])
            data = json.loads(messages[-1].content)
            assert data['groups'][0]['sum'] == '30.3'
            message = AIMessage(content=f"销售额合计 {data['columns'][1]['sum']}，空值 1 项，非数字 1 项。")
        else:
            message = AIMessage(content='', tool_calls=[{'name':'analyze_csv', 'args':{'csv_text':self.csv_text, 'group_by':'地区', 'value_column':'金额'}, 'id':'csv-call', 'type':'tool_call'}])
        return ChatResult(generations=[ChatGeneration(message=message)])


async def _http_agent_roundtrip(path):
    with patch.object(dependencies, 'get_default_tools', return_value=[]), patch.object(dependencies.settings, 'weather_enabled', False):
        graph = build_search_agent(model=CsvToolModel(), tools=dependencies._runtime_tools())
    with (
        patch('app.api.auth.settings.service_api_key', SecretStr('offline-test-key')),
        patch.object(chat_api, 'get_agent', return_value=graph),
        patch.object(chat_api, 'create_agent_context', return_value=AgentContext(user_id='csv-test', thread_id='csv-thread')),
        patch.object(chat_api, '_recall_preferences', AsyncMock(return_value=[])),
        patch.object(chat_api, '_capture_preferences', AsyncMock(return_value=[])),
    ):
        async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url='http://testserver') as client:
            response = await client.post(path, headers={'X-Agent-Service-Key':'offline-test-key'}, json={'message':'按地区统计金额\n' + SAMPLE, 'user_id':'csv-test', 'thread_id':'csv-thread'})
    assert response.status_code == 200, response.text
    if path.endswith('/invoke'):
        assert '30.3' in response.json()['data']['response']
    else:
        events = [json.loads(line[6:]) for line in response.text.splitlines() if line.startswith('data: ') and line != 'data: [DONE]']
        progress = [e['data']['status'] for e in events if e['type'] == 'tool_progress' and e['tool'] == 'analyze_csv']
        assert progress == ['started', 'completed']
        assert any(e['type'] == 'done' for e in events)
        assert '30.3' in response.text
        assert '[DONE]' in response.text
        assert not any(e['type'] == 'error' for e in events)


@pytest.mark.parametrize('path', ['/api/v1/chat/invoke', '/api/v1/chat/stream'])
def test_authenticated_http_agent_executes_real_csv_tool(path):
    asyncio.run(_http_agent_roundtrip(path))


def test_agent_reports_safe_validation_error_instead_of_partial_statistics():
    async def run():
        graph = build_search_agent(model=CsvToolModel(csv_text='地区,金额\n私密分组,1,2'), tools=[create_csv_analysis_tool()])
        events = []
        async for mode, chunk in graph.astream({'messages':[('user', '统计表格')]}, context=AgentContext(user_id='test'), stream_mode=['custom', 'updates']):
            if mode == 'custom':
                events.append(chunk)
            elif 'tools' in chunk:
                message = chunk['tools']['messages'][0]
                assert message.status == 'error'
                assert 'TOOL_INVALID_INPUT' in message.content
                assert '列数' in message.content
                assert '私密分组' not in message.content
        assert [event['status'] for event in events] == ['started', 'failed']
    asyncio.run(run())
