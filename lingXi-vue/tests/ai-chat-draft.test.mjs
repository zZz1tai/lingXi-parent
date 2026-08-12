import test from 'node:test';
import assert from 'node:assert/strict';
import {
  applyStreamEvent,
  createStreamDraft,
  restoreUiRendersFromHistory
} from '../src/store/modules/agentStreamDraft.js';

const newDraft = () => createStreamDraft({
  message: '分析下销售',
  userMessageId: 'msg-user-1',
  assistantMessageId: 'msg-assistant-1'
});

test('createStreamDraft produces the initial streaming shape', () => {
  const draft = newDraft();
  assert.equal(draft.status, 'streaming');
  assert.equal(draft.assistantContent, '');
  assert.deepEqual(draft.uiRenders, []);
  assert.deepEqual(draft.activities, []);
  assert.equal(draft.pendingAction, null);
});

test('ui_start opens a streaming render with schema version', () => {
  const draft = newDraft();
  applyStreamEvent(draft, {
    type: 'ui_start',
    render_id: 'ui-render-1',
    schema_version: 1
  });
  assert.equal(draft.uiRenders.length, 1);
  assert.equal(draft.uiRenders[0].renderId, 'ui-render-1');
  assert.equal(draft.uiRenders[0].schemaVersion, 1);
  assert.equal(draft.uiRenders[0].status, 'streaming');
  assert.deepEqual(draft.uiRenders[0].sections, []);
});

test('ui_delta appends sections while streaming and ignores unknown renders', () => {
  const draft = newDraft();
  applyStreamEvent(draft, { type: 'ui_start', render_id: 'ui-render-1' });
  applyStreamEvent(draft, {
    type: 'ui_delta',
    render_id: 'ui-render-1',
    sequence: 1,
    delta: [{ type: 'Text', text: 'a' }, { type: 'Text', text: 'b' }]
  });
  applyStreamEvent(draft, {
    type: 'ui_delta',
    render_id: 'ui-render-unknown',
    delta: [{ type: 'Script' }]
  });
  assert.equal(draft.uiRenders[0].sections.length, 2);
  assert.equal(draft.uiRenders[0].sections[1].text, 'b');
});

test('ui_complete replaces accumulated sections and marks the render complete', () => {
  const draft = newDraft();
  applyStreamEvent(draft, { type: 'ui_start', render_id: 'ui-render-1', schema_version: 1 });
  applyStreamEvent(draft, {
    type: 'ui_delta',
    render_id: 'ui-render-1',
    delta: [{ type: 'Text', text: 'streaming fragment' }]
  });
  applyStreamEvent(draft, {
    type: 'ui_complete',
    render_id: 'ui-render-1',
    schema_version: 1,
    spec: [{ type: 'MetricGrid', cards: [] }]
  });
  assert.equal(draft.uiRenders[0].status, 'complete');
  assert.deepEqual(draft.uiRenders[0].sections, [{ type: 'MetricGrid', cards: [] }]);
});

test('ui_error marks an existing render or creates an error entry', () => {
  const draft = newDraft();
  applyStreamEvent(draft, { type: 'ui_start', render_id: 'ui-render-1' });
  applyStreamEvent(draft, {
    type: 'ui_error',
    render_id: 'ui-render-1',
    code: 'OPENUI_TOO_LARGE'
  });
  assert.equal(draft.uiRenders[0].status, 'error');
  assert.equal(draft.uiRenders[0].errorCode, 'OPENUI_TOO_LARGE');

  applyStreamEvent(draft, {
    type: 'ui_error',
    render_id: 'ui-render-2',
    code: 'OPENUI_FILTER_REJECTED'
  });
  assert.equal(draft.uiRenders.length, 2);
  assert.equal(draft.uiRenders[1].status, 'error');
  assert.equal(draft.uiRenders[1].errorCode, 'OPENUI_FILTER_REJECTED');
});

test('ui_delta after complete is ignored', () => {
  const draft = newDraft();
  applyStreamEvent(draft, { type: 'ui_start', render_id: 'ui-render-1' });
  applyStreamEvent(draft, { type: 'ui_complete', render_id: 'ui-render-1', spec: [] });
  applyStreamEvent(draft, {
    type: 'ui_delta',
    render_id: 'ui-render-1',
    delta: [{ type: 'Text', text: 'late' }]
  });
  assert.deepEqual(draft.uiRenders[0].sections, []);
});

test('token and done events accumulate assistant content', () => {
  const draft = newDraft();
  applyStreamEvent(draft, { type: 'token', content: '你好' });
  applyStreamEvent(draft, { type: 'token', content: '，灵犀' });
  assert.equal(draft.assistantContent, '你好，灵犀');
  applyStreamEvent(draft, { type: 'done', content: '完整回复' });
  assert.equal(draft.assistantContent, '你好，灵犀');
});

test('done fills content only when no tokens were emitted', () => {
  const draft = newDraft();
  applyStreamEvent(draft, { type: 'done', content: '兜底回复' });
  assert.equal(draft.assistantContent, '兜底回复');
});

test('tool lifecycle tracks started and completed steps by call id', () => {
  const draft = newDraft();
  applyStreamEvent(draft, {
    type: 'tool_start',
    tool: 'lookup_device',
    call_id: 'call-1',
    sequence: 1,
    input_summary: 'A001'
  });
  applyStreamEvent(draft, {
    type: 'tool_progress',
    tool: 'lookup_device',
    call_id: 'call-1',
    data: { status: 'running' }
  });
  applyStreamEvent(draft, {
    type: 'tool_end',
    tool: 'lookup_device',
    call_id: 'call-1',
    elapsed_ms: 320,
    data: { status: 'success', result_count: 2 }
  });
  assert.equal(draft.activities.length, 1);
  assert.equal(draft.activities[0].status, 'completed');
  assert.equal(draft.activities[0].elapsedMs, 320);
  assert.equal(draft.activities[0].resultCount, 2);
  assert.equal(draft.activities[0].label, '查询设备状态');
});

test('citations deduplicate by source id', () => {
  const draft = newDraft();
  const citation = { source_id: 'sop#1', title: '操作规范' };
  applyStreamEvent(draft, { type: 'citation', data: citation });
  applyStreamEvent(draft, { type: 'citation', data: citation });
  applyStreamEvent(draft, {
    type: 'citation',
    data: { source_id: 'sop#2', title: '应急手册' }
  });
  assert.equal(draft.citations.length, 2);
});

test('approval flow transitions between pending and decided states', () => {
  const draft = newDraft();
  applyStreamEvent(draft, {
    type: 'approval_required',
    data: { action_id: 'action-1', task_summary: '创建工单' }
  });
  assert.equal(draft.pendingAction.decision, 'pending');
  assert.equal(draft.pendingAction.action_id, 'action-1');

  applyStreamEvent(draft, {
    type: 'action_completed',
    data: { action_id: 'action-1', task_code: 'T-001' }
  });
  assert.equal(draft.pendingAction.decision, 'approved');
  assert.equal(draft.pendingAction.task_code, 'T-001');

  const secondDraft = newDraft();
  applyStreamEvent(secondDraft, {
    type: 'approval_required',
    data: { action_id: 'action-2' }
  });
  applyStreamEvent(secondDraft, {
    type: 'action_rejected',
    data: { action_id: 'action-2' }
  });
  assert.equal(secondDraft.pendingAction.decision, 'rejected');
});

test('memory_saved events merge by preference', () => {
  const draft = newDraft();
  applyStreamEvent(draft, {
    type: 'memory_saved',
    data: { preference: 'answer_length', value: 'short' }
  });
  applyStreamEvent(draft, {
    type: 'memory_saved',
    data: { preference: 'answer_length', value: 'medium' }
  });
  assert.equal(draft.memorySaved.length, 1);
  assert.equal(draft.memorySaved[0].value, 'medium');
});

test('unknown and malformed events are ignored safely', () => {
  const draft = newDraft();
  applyStreamEvent(draft, { type: 'mystery', payload: 'x' });
  applyStreamEvent(draft, { type: 'ui_delta' });
  applyStreamEvent(draft, { type: 'ui_complete', render_id: 'nope' });
  applyStreamEvent(draft, null);
  applyStreamEvent(null, { type: 'token', content: 'x' });
  assert.equal(draft.assistantContent, '');
  assert.deepEqual(draft.uiRenders, []);
});

test('restoreUiRendersFromHistory restores completed renders from ui_json', () => {
  const renders = restoreUiRendersFromHistory(JSON.stringify({
    renders: [
      {
        type: 'ui_complete',
        render_id: 'ui-hist-1',
        schema_version: 1,
        spec: [{ type: 'BarChart', title: '销售趋势' }],
        fallback_markdown: '正文'
      },
      {
        type: 'ui_complete',
        render_id: 'ui-hist-2',
        schema_version: 3,
        spec: [{ type: 'Text', text: '补充' }]
      }
    ]
  }));
  assert.equal(renders.length, 2);
  assert.equal(renders[0].renderId, 'ui-hist-1');
  assert.equal(renders[0].schemaVersion, 1);
  assert.equal(renders[0].status, 'complete');
  assert.equal(renders[0].errorCode, '');
  assert.equal(renders[0].sections[0].title, '销售趋势');
  assert.equal(renders[1].schemaVersion, 3);
  assert.equal(renders[1].sections[0].text, '补充');
});

test('restoreUiRendersFromHistory tolerates missing ids, odd specs and junk input', () => {
  const withDefaults = restoreUiRendersFromHistory(JSON.stringify({
    renders: [
      { render_id: '', schema_version: 'bad', spec: 'not-an-array' },
      { spec: [{ type: 'Notice', text: 'x' }] }
    ]
  }));
  assert.equal(withDefaults.length, 2);
  assert.equal(withDefaults[0].renderId, 'ui-1');
  assert.equal(withDefaults[0].schemaVersion, null);
  assert.deepEqual(withDefaults[0].sections, []);
  assert.equal(withDefaults[1].renderId, 'ui-2');
  assert.equal(withDefaults[1].schemaVersion, null);
  assert.equal(withDefaults[1].sections[0].text, 'x');

  assert.deepEqual(restoreUiRendersFromHistory(null), []);
  assert.deepEqual(restoreUiRendersFromHistory(''), []);
  assert.deepEqual(restoreUiRendersFromHistory('not-json{{{'), []);
  assert.deepEqual(restoreUiRendersFromHistory('{"renders":"nope"}'), []);
  assert.deepEqual(restoreUiRendersFromHistory('{"other":1}'), []);
});