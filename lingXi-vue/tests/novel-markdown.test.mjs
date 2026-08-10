import test from 'node:test'
import assert from 'node:assert/strict'

import {
  joinMarkdownHeading,
  renderNovelMarkdown,
  splitLeadingMarkdownHeading
} from '../src/views/novel/novelMarkdown.js'

test('splits a leading AI markdown heading from manuscript body', () => {
  assert.deepEqual(splitLeadingMarkdownHeading('# 我的抖音通地府\n\n第一段正文'), {
    level: 1,
    title: '我的抖音通地府',
    body: '第一段正文'
  })
})

test('joins the editable title and body without changing the storage format', () => {
  assert.equal(joinMarkdownHeading({
    level: 2,
    title: '第二章 雨夜',
    body: '雨落在窗外。'
  }), '## 第二章 雨夜\n\n雨落在窗外。')
})

test('renders headings without a visible hash and escapes model supplied html', () => {
  const html = renderNovelMarkdown('# 加粗标题\n<script>alert(1)</script>')

  assert.match(html, /<h1[^>]*>加粗标题<\/h1>/)
  assert.doesNotMatch(html, /># 加粗标题</)
  assert.match(html, /&lt;script&gt;alert\(1\)&lt;\/script&gt;/)
  assert.doesNotMatch(html, /<script>/)
})
