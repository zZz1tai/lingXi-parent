import test from 'node:test';
import assert from 'node:assert/strict';
import {
  getAgentExecutionTraceState,
  normalizeAgentActivityStatus
} from '../src/utils/agentExecutionTrace.js';

test('keeps a running trace expanded and reports active steps', () => {
  const state = getAgentExecutionTraceState([
    { tool: 'query_orders', status: 'completed' },
    { tool: 'search_stock', status: 'running' },
    { tool: 'prepare_result' }
  ]);

  assert.equal(state.status, 'running');
  assert.equal(state.summaryText, '正在执行 2 个步骤');
  assert.equal(state.expandedByDefault, true);
  assert.equal(state.stateKey, 'running:3:0');
});

test('collapses a trace after every step completes', () => {
  const state = getAgentExecutionTraceState([
    { tool: 'query_orders', status: 'completed' },
    { tool: 'search_stock', status: 'completed' }
  ]);

  assert.equal(state.status, 'completed');
  assert.equal(state.summaryText, '已完成 2 个步骤');
  assert.equal(state.expandedByDefault, false);
});

test('gives an error precedence and keeps the failed trace expanded', () => {
  const state = getAgentExecutionTraceState([
    { tool: 'query_orders', status: 'running' },
    { tool: 'create_ticket', status: 'error' }
  ]);

  assert.equal(state.status, 'error');
  assert.equal(state.summaryText, '1 个步骤未完成');
  assert.equal(state.expandedByDefault, true);
  assert.equal(state.stateKey, 'error:2:1');
});

test('normalizes unknown activity states as pending', () => {
  assert.equal(normalizeAgentActivityStatus('queued'), 'pending');
  assert.equal(normalizeAgentActivityStatus(undefined), 'pending');
});
