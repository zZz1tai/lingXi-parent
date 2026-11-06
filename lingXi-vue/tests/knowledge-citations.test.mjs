import assert from 'node:assert/strict'
import test from 'node:test'
import { readFile } from 'node:fs/promises'
import { parse } from '@vue/compiler-sfc'
import {
  applyStreamEvent,
  createStreamDraft,
  restoreCitationsFromHistory,
  restoreUiRendersFromHistory
} from '../src/store/modules/agentStreamDraft.js'

const citation = {
  source_id: 'sop:repair:1', title: '维修规范', section: '3.2 排查网络', version: '2026-11',
  document_type: 'SOP', excerpt: '先检查设备网络。\n  确认连接后继续排查。', excerpt_truncated: false, score: 0.8
}
const restore = citations => restoreCitationsFromHistory(JSON.stringify({ citations }))

test('实时与历史使用同一证据契约，保留片段换行并丢弃内部字段', () => {
  const raw = { ...citation, source_uri: 'private://document', visibility_roles: ['admin'], content: '完整内部文档' }
  const draft = createStreamDraft({ message: '如何排查？' })
  applyStreamEvent(draft, { type: 'citation', data: raw })
  assert.deepEqual(draft.citations, [citation])
  assert.deepEqual(restore([raw]), [citation])
  raw.excerpt = '外部对象被修改'
  assert.equal(draft.citations[0].excerpt, citation.excerpt)
})

test('引用片段按 Unicode 码点限长，并明确标记截断', () => {
  const [result] = restore([{ ...citation, title: '题'.repeat(301), excerpt: '😀'.repeat(2001) }])
  assert.equal(result.title.length, 300)
  assert.equal(Array.from(result.excerpt).length, 2000)
  assert.equal(result.excerpt, '😀'.repeat(2000))
  assert.equal(result.excerpt_truncated, true)
  assert.equal(restore([{ ...citation, excerpt: '短片段', excerpt_truncated: true }])[0].excerpt_truncated, true)
})

test('按来源首次去重并限制20条，实时与历史结果一致', () => {
  const entries = [citation, { ...citation, excerpt: '不可覆盖首份证据' },
    ...Array.from({ length: 25 }, (_, i) => ({ ...citation, source_id: `source-${i}` }))]
  const draft = createStreamDraft({ message: '排查' })
  entries.forEach(data => applyStreamEvent(draft, { type: 'citation', data }))
  assert.equal(draft.citations.length, 20)
  assert.equal(draft.citations[0].excerpt, citation.excerpt)
  assert.deepEqual(draft.citations, restore(entries))
  // 标识保持原样，不能因前端自行修剪而与其他来源发生碰撞。
  assert.equal(restore([{ ...citation, source_id: 'source' }, { ...citation, source_id: 'source ' }]).length, 2)
})

test('旧历史、非法JSON、异常类型和过长来源标识都安全处理', () => {
  for (const value of [null, '', '{}', 'null', '{broken', '[]', '{"citations":{}}']) {
    assert.deepEqual(restoreCitationsFromHistory(value), [])
  }
  assert.deepEqual(restore([null, [], 1, {}, { source_id: true }, { source_id: ' ' }, { source_id: 'x'.repeat(513) }]), [])
  const [result] = restore([{ source_id: 'old', excerpt: {}, title: 1, score: true, excerpt_truncated: 'true' }])
  assert.deepEqual(result, { source_id: 'old', excerpt_truncated: false })
  const draft = createStreamDraft({ message: '' })
  applyStreamEvent(draft, { type: 'citation', data: { ...citation, score: Infinity } })
  assert.equal(draft.citations[0].score, undefined)
})

test('知识引用与既有OpenUI历史共存，单独的引用也可恢复', () => {
  const stored = JSON.stringify({
    renders: [{ render_id: 'ui-1', schema_version: 1, spec: [{ type: 'Text', text: '图表说明' }] }],
    citations: [citation]
  })
  assert.deepEqual(restoreCitationsFromHistory(stored), [citation])
  assert.equal(restoreUiRendersFromHistory(stored)[0].renderId, 'ui-1')
  assert.deepEqual(restoreCitationsFromHistory('{"renders":[]}'), [])
  assert.deepEqual(restoreUiRendersFromHistory(JSON.stringify({ citations: [citation] })), [])
})

test('证据展示使用原生可键盘展开的详情及纯文本插值', async () => {
  const component = await readFile(new URL('../src/views/ai/components/KnowledgeCitations.vue', import.meta.url), 'utf8')
  assert.deepEqual(parse(component).errors, [])
  assert.ok(component.includes('<details'))
  assert.ok(component.includes('<summary>'))
  assert.ok(component.includes('{{ citation.excerpt }}'))
  assert.ok(!component.includes('v-html'))
  assert.ok(!component.includes(':href'))
  assert.ok(component.includes('该引用未包含原文片段'))
})
