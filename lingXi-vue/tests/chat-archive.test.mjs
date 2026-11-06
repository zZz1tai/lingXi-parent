import test from 'node:test';
import assert from 'node:assert/strict';
import { marked } from 'marked';
import { createChatArchive } from '../src/utils/chatArchive.js';

test('按顺序导出原文、角色、附件名和引用，不搬运内部地址及字段', () => {
  const messages = [
    {isUser:true, content:'请解释\n  缩进', attachments:[{name:'资料.pdf', previewUrl:'secret-url'}]},
    {content:'回答', citations:[{source_id:'guide#1', version:'v1', excerpt:'证据', source_uri:'secret-storage'}], uiRenders:[{secret:'internal-ui'}]},
    {content:'', status:'FAILED'}
  ];
  const archive = createChatArchive(messages, {title:'讨论', exportedAt:'2026-11-06'});
  assert.equal(archive.count, 2);
  for (const value of ['## 1. 用户', '## 2. 灵犀助手', '请解释\n  缩进', '资料.pdf', 'guide#1', 'v1', '证据']) assert.ok(archive.markdown.includes(value));
  for (const value of ['secret-url', 'secret-storage', 'internal-ui']) assert.ok(!archive.markdown.includes(value));
  assert.equal(createChatArchive(messages, {assistantOnly:true}).count, 1);
});

test('正文和标题中的 HTML、链接及围栏保持文字，不形成可执行内容或外部图片', () => {
  const attack = '```\n<img src="https://example.com/track" onerror="alert(1)">\n![image](https://example.com/image)\n````\n# 冒充标题';
  const result = createChatArchive([{content:attack}], {title:attack});
  const html = marked.parse(result.markdown);
  assert.ok(!html.includes('<img'));
  assert.ok(!html.includes('<h1>冒充标题'));
  assert.ok(html.includes('&lt;img'));
  assert.ok(result.markdown.includes(attack));
});

test('空会话、仅助手无结果、部分失败消息和安全文件名', () => {
  assert.equal(createChatArchive([]).count, 0);
  assert.equal(createChatArchive([{isUser:true, content:'问题'}], {assistantOnly:true}).count, 0);
  assert.ok(createChatArchive([{content:'部分回答', status:'FAILED'}]).markdown.includes('未正常完成'));
  const file = createChatArchive([], {title:'../CON:<x>?\\文件. '}).filename;
  assert.ok(file.startsWith('AI对话-'));
  assert.ok(!/[<>:"/\\|?*]/.test(file));
  assert.ok(createChatArchive([], {title:'😀'.repeat(100)}).filename.length < 140);
});

test('超长代码消息不因大量围栏触发调用栈溢出，保留附件独立消息', () => {
  const content = '` x '.repeat(50000);
  assert.ok(createChatArchive([{content}]).markdown.includes(content));
  assert.equal(createChatArchive([{isUser:true, attachments:[{name:'图片.png'}]}]).count, 1);
});
