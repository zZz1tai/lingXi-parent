import test from 'node:test';
import assert from 'node:assert/strict';
import { appendFollowUp, QUOTE_LIMIT, QUESTION_LIMIT } from '../src/utils/chatFollowUp.js';

test('引用追问保留已有草稿，逐行标记引用且不修改原消息', () => {
  const quote = '第一段\r\n\n第二段 <script>\n> 原有引用';
  const result = appendFollowUp('原有草稿  ', quote, '  请解释  ');
  assert.ok(result.startsWith('原有草稿  \n\n'));
  assert.ok(result.includes('> 第一段\n> \n> 第二段 <script>\n> > 原有引用'));
  assert.ok(result.endsWith('我的追问：请解释'));
  assert.ok(appendFollowUp('', '引用', '问题').startsWith('针对以下助手回答片段'));
});

test('空白与过长输入被拒绝，边界长度允许', () => {
  for (const args of [['', ' ', '问题'], ['', '引用', '\n'], ['', 'x'.repeat(QUOTE_LIMIT + 1), '问题'], ['', '引用', 'x'.repeat(QUESTION_LIMIT + 1)]]) {
    assert.throws(() => appendFollowUp(...args));
  }
  assert.ok(appendFollowUp('', '引'.repeat(QUOTE_LIMIT), '问'.repeat(QUESTION_LIMIT)));
});
