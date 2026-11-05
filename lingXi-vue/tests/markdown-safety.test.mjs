import test from 'node:test';
import assert from 'node:assert/strict';
import { Marked } from 'marked';
import {
  escapeHtml,
  isSafeExternalUrl,
  sanitizeRawHtmlBlock,
  safeMarkdownRenderer
} from '../src/utils/markdownSafety.js';

test('escapeHtml escapes angle brackets and quotes', () => {
  assert.equal(escapeHtml('<script>alert(1)</script>'), '&lt;script&gt;alert(1)&lt;/script&gt;');
  assert.equal(escapeHtml('a & "b"'), 'a &amp; &quot;b&quot;');
  assert.equal(escapeHtml(undefined), '');
});

test('isSafeExternalUrl allows https and loopback http only', () => {
  assert.equal(isSafeExternalUrl('https://cdn.example.com/a.png'), true);
  assert.equal(isSafeExternalUrl('http://localhost:9000/a.png'), true);
  assert.equal(isSafeExternalUrl('http://127.0.0.1:8080/a.png'), true);
  assert.equal(isSafeExternalUrl('http://[::1]/a.png'), true);
  assert.equal(isSafeExternalUrl('http://evil.example.com/a.png'), false);
  assert.equal(isSafeExternalUrl('javascript:alert(1)'), false);
  assert.equal(isSafeExternalUrl('data:text/html,<script>'), false);
  assert.equal(isSafeExternalUrl(''), false);
  assert.equal(isSafeExternalUrl(undefined), false);
});

test('sanitizeRawHtmlBlock escapes arbitrary raw html', () => {
  assert.equal(
    sanitizeRawHtmlBlock('<script>alert(1)</script>'),
    '&lt;script&gt;alert(1)&lt;/script&gt;'
  );
  assert.equal(
    sanitizeRawHtmlBlock('<img src=x onerror=alert(1)>'),
    '&lt;img src=x onerror=alert(1)&gt;'
  );
  assert.equal(
    sanitizeRawHtmlBlock('<video><source src="x">'),
    '&lt;video&gt;&lt;source src=&quot;x&quot;&gt;'
  );
});

test('sanitizeRawHtmlBlock allows only https video blocks', () => {
  const safe = '<video src="https://cdn.example.com/a.mp4"></video>';
  assert.equal(sanitizeRawHtmlBlock(safe), safe);
});

test('sanitizeRawHtmlBlock blocks unsafe video blocks', () => {
  assert.equal(
    sanitizeRawHtmlBlock('<video src="http://evil.example.com/a.mp4"></video>'),
    '&lt;video src=&quot;http://evil.example.com/a.mp4&quot;&gt;&lt;/video&gt;'
  );
  assert.equal(
    sanitizeRawHtmlBlock('<video src="https://cdn.example.com/a.mp4" autoplay></video>'),
    '&lt;video src=&quot;https://cdn.example.com/a.mp4&quot; autoplay&gt;&lt;/video&gt;'
  );
  assert.equal(
    sanitizeRawHtmlBlock('<video>https://cdn.example.com/a.mp4</video>'),
    '&lt;video&gt;https://cdn.example.com/a.mp4&lt;/video&gt;'
  );
});

test('拒绝伪装成本地回环的主机、用户信息和无效 URL', () => {
  for (const url of [
    'http://localhost.attacker.invalid/a',
    'http://127.0.0.1.attacker.invalid/a',
    'http://localhost@attacker.invalid/a',
    'http://127.0.0.1:80@attacker.invalid/a',
    'https://user:password@example.com/a',
    'https://',
    'https://exa\nmple.com/a',
    'http://localhost\\@attacker.invalid/a'
  ]) {
    assert.equal(isSafeExternalUrl(url), false, url);
  }
});

test('视频回退正文及闭合标签后的嵌套 HTML 都必须转义', () => {
  for (const body of [
    '<img src=x onerror=alert(1)>',
    '</video><img src=x onerror=alert(1)><video>'
  ]) {
    const result = sanitizeRawHtmlBlock(`<video src="https://example.com/a.mp4">${body}</video>`);
    assert.equal(result, `<video src="https://example.com/a.mp4">${escapeHtml(body)}</video>`);
    assert.ok(!result.includes('<img'));
  }
});

test('链接标签中的 HTML 不能绕过渲染器，嵌套 Markdown 保持正常', () => {
  const parser = new Marked({ renderer: safeMarkdownRenderer });
  const html = parser.parse('[<img src=x onerror=alert(1)>](https://example.com) [**正常**](https://example.com)');
  assert.ok(!html.includes('<img'));
  assert.ok(html.includes('&lt;img'));
  assert.ok(html.includes('<strong>正常</strong>'));
  assert.ok(!parser.parse('![x](javascript:alert(1))').includes('<img'));
});
