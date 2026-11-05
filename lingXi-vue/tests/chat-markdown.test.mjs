import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { runInNewContext } from 'node:vm';
import { Marked } from 'marked';
import { escapeHtml, isSafeExternalUrl, safeMarkdownRenderer } from '../src/utils/markdownSafety.js';

// 直接执行页面中独立的渲染逻辑，无需编译 Vue 或启动浏览器。
const page = await readFile(new URL('../src/views/ai/qwen-chat.vue', import.meta.url), 'utf8');
const panel = await readFile(new URL('../src/components/AiChatPanel/index.vue', import.meta.url), 'utf8');
const renderMarkdown = runInNewContext(
  page.slice(page.indexOf('const chatMediaRenderer'), page.indexOf('// 聊天相关状态')) + '\nrenderMarkdown;',
  { Marked, URL, isSafeExternalUrl, safeMarkdownRenderer }
);
const renderMd = runInNewContext(
  panel.slice(panel.indexOf('const chatPanelMarked'), panel.indexOf('// 滚动到底部')) + '\nrenderMd;',
  { Marked, escapeHtml, safeMarkdownRenderer }
);

test('聊天图片使用当前 Marked token 参数并保留预览与下载地址', () => {
  const html = renderMarkdown('![图片](https://cdn.example.com/image.png)');
  assert.ok(html.includes('<img src="https://cdn.example.com/image.png"'));
  assert.ok(html.includes('data-src="https://cdn.example.com/image.png"'));
  assert.ok(html.includes('data-action="preview"'));
  assert.ok(!html.includes('[object Object]'));
});

test('聊天页面和侧栏都阻止链接正文、视频回退和图片的脚本注入', () => {
  for (const render of [renderMarkdown, renderMd]) {
    assert.ok(!render('[<img src=x onerror=alert(1)>](https://example.com)').includes('<img'));
    assert.ok(!render('<video src="https://example.com/a.mp4"><img src=x onerror=alert(1)></video>').includes('<img'));
    assert.ok(!render('![x](javascript:alert(1))').includes('<img'));
    assert.ok(!render('![x](http://localhost.attacker.invalid/a.png)').includes('<img'));
  }
});
