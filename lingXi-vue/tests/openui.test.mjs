import test from 'node:test';
import assert from 'node:assert/strict';
import {
  isSafeMediaUrl,
  clampText,
  noticeToneClass
} from '../src/views/ai/components/openui/helpers.js';
import { renderOpenUiMarkdown } from '../src/views/ai/components/openui/safeMarkdown.js';

test('allows https and loopback http media urls only', () => {
  assert.equal(isSafeMediaUrl('https://cdn.example.com/a.png'), true);
  assert.equal(isSafeMediaUrl('http://localhost:9000/thumb.jpg'), true);
  assert.equal(isSafeMediaUrl('http://127.0.0.1:8080/thumb.jpg'), true);
  assert.equal(isSafeMediaUrl('http://[::1]/thumb.jpg'), true);
  assert.equal(isSafeMediaUrl('http://evil.example.com/a.png'), false);
  assert.equal(isSafeMediaUrl('javascript:alert(1)'), false);
  assert.equal(isSafeMediaUrl('data:text/html;base64,xxxx'), false);
  assert.equal(isSafeMediaUrl(''), false);
  assert.equal(isSafeMediaUrl('https://' + 'x'.repeat(2100) + '.com/a.png'), false);
});

test('clamps text to the configured maximum', () => {
  assert.equal(clampText('short'), 'short');
  assert.equal(clampText('x'.repeat(5000), 4096).length, 4096);
  assert.equal(clampText(undefined), '');
});

test('falls back unknown notice tones to info', () => {
  assert.equal(noticeToneClass('warning'), 'warning');
  assert.equal(noticeToneClass('error'), 'error');
  assert.equal(noticeToneClass('custom'), 'info');
  assert.equal(noticeToneClass(undefined), 'info');
});

test('escapes raw html in openui markdown', () => {
  const html = renderOpenUiMarkdown('hi <script>alert(1)</script>');
  assert.ok(!html.includes('<script>'));
  assert.ok(html.includes('&lt;script&gt;'));
});

test('drops unsafe links and keeps safe ones', () => {
  const html = renderOpenUiMarkdown(
    '[safe](https://example.com/a) [bad](javascript:alert(1))'
  );
  assert.ok(html.includes('href="https://example.com/a"'));
  assert.ok(!html.includes('href="javascript:'));
  assert.ok(html.includes('rel="noopener noreferrer"'));
});

test('drops unsafe image sources', () => {
  const html = renderOpenUiMarkdown('![ok](https://a.com/x.png) ![bad](javascript:x)');
  assert.ok(html.includes('src="https://a.com/x.png"'));
  assert.ok(!html.includes('src="javascript:'));
});
