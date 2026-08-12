import test from 'node:test';
import assert from 'node:assert/strict';
import {
  escapeHtml,
  isSafeExternalUrl,
  sanitizeRawHtmlBlock
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
