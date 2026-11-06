import test from 'node:test';
import assert from 'node:assert/strict';
import { searchChatMessages } from '../src/utils/chatSearch.js';

test('搜索用户与助手正文，保留消息索引，英文不区分大小写', () => {
  const messages = [{isUser:true, content:'请介绍 AI 检索'}, {content:null}, {content:'ai 可以检索知识'}, {content:'无关'}];
  const results = searchChatMessages(messages, '  ai  ');
  assert.deepEqual(results.map(({index, role, match}) => ({index, role, match})), [
    {index:0, role:'你', match:'AI'}, {index:2, role:'灵犀助手', match:'ai'}
  ]);
});

test('查询按字面匹配，特殊符号不成为正则表达式', () => {
  const text = '示例 [a.*](x) <img src=x onerror=alert(1)> $^+?{x}|\\';
  for (const keyword of ['[a.*](x)', '<img src=x onerror=alert(1)>', '$^+?{x}|\\']) {
    const results = searchChatMessages([{content:text}], keyword);
    assert.equal(results.length, 1);
    assert.equal(results[0].match, keyword);
  }
  assert.deepEqual(searchChatMessages([{content:'abc'}], '.*'), []);
});

test('空查询和无匹配返回空结果，不搜索引用或附件', () => {
  const messages = [{content:'正文', citations:[{excerpt:'秘密'}], attachments:[{name:'秘密'}]}];
  for (const query of ['', '   ', '秘密']) assert.deepEqual(searchChatMessages(messages, query), []);
});

test('长消息片段截断且不切断 emoji，每条消息只返回一次', () => {
  const content = '😀'.repeat(70) + '关键字' + '🌟'.repeat(70) + '关键字';
  const [result, ...rest] = searchChatMessages([{content}], '关键字');
  assert.equal(rest.length, 0);
  assert.equal(result.before, '…' + '😀'.repeat(60));
  assert.equal(result.after, '🌟'.repeat(60) + '…');
  assert.equal(content, '😀'.repeat(70) + '关键字' + '🌟'.repeat(70) + '关键字');
});
