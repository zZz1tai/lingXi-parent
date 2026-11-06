import test from 'node:test'
import assert from 'node:assert/strict'
import { citationText } from '../src/utils/citationText.js'

test('复制证据保留原文和来源且不导出内部字段', () => {
  const result = citationText({ title: '规范', section: '检查', version: 'v1', source_id: 's1', excerpt: '第一行\n  第二行', excerpt_truncated: true, source_uri: 'private-secret' })
  assert.ok(result.includes('来源：s1'))
  assert.ok(result.includes('版本：v1'))
  assert.ok(result.includes('第一行\n  第二行'))
  assert.ok(result.includes('截断'))
  assert.ok(!result.includes('private-secret'))
})
test('旧引用导出明确说明缺少原文', () => {
  assert.ok(citationText({ source_id: 'old' }).includes('未包含原文片段'))
})
