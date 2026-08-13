import test from 'node:test'
import assert from 'node:assert/strict'

import {
  DEFAULT_PACING_LEVEL,
  PACING_ISSUE_LABELS,
  PACING_LEVELS,
  buildPacingRequest,
  normalizePacingResult,
  pacingDesc,
  pacingLabel
} from '../src/views/novel/novelPacing.js'

test('pacing levels: 5 levels, unique ids, default is balanced', () => {
  assert.equal(PACING_LEVELS.length, 5)
  assert.equal(new Set(PACING_LEVELS.map(item => item.id)).size, 5)
  assert.equal(DEFAULT_PACING_LEVEL, 'balanced')
  for (const item of PACING_LEVELS) {
    assert.ok(item.id)
    assert.ok(item.label)
    assert.ok(item.desc)
  }
})

test('pacingLabel maps known ids and falls back for unknown', () => {
  assert.equal(pacingLabel('relaxed'), '舒缓')
  assert.equal(pacingLabel('steady'), '平稳')
  assert.equal(pacingLabel('balanced'), '均衡')
  assert.equal(pacingLabel('intense'), '紧凑')
  assert.equal(pacingLabel('rapid'), '激烈')
  assert.equal(pacingLabel('unknown'), '均衡')
  assert.equal(pacingLabel(''), '均衡')
})

test('pacingDesc returns description for known id', () => {
  assert.ok(pacingDesc('relaxed').length > 0)
  assert.equal(pacingDesc('nope'), '')
})

test('buildPacingRequest fills required fields and applies default level', () => {
  const request = buildPacingRequest({ workName: '夜航', content: '正文' })
  assert.equal(request.work_name, '夜航')
  assert.equal(request.content, '正文')
  assert.equal(request.pacing_level, 'balanced')
  assert.equal(request.genre, undefined)
  assert.equal(request.chapter_title, undefined)
})

test('buildPacingRequest passes optional fields when provided', () => {
  const request = buildPacingRequest({
    workName: '夜航',
    genre: '悬疑',
    chapterTitle: '第一章',
    pacingLevel: 'rapid',
    content: '正文'
  })
  assert.equal(request.genre, '悬疑')
  assert.equal(request.chapter_title, '第一章')
  assert.equal(request.pacing_level, 'rapid')
})

test('normalizePacingResult returns null for non-object input', () => {
  assert.equal(normalizePacingResult(null), null)
  assert.equal(normalizePacingResult(undefined), null)
  assert.equal(normalizePacingResult('x'), null)
})

test('normalizePacingResult defaults score/level and keeps issue labels', () => {
  const result = normalizePacingResult({
    scoreNote: '节奏尚可',
    issues: [{ type: 'PLODDING', issue: '开头拖沓', suggestion: '删减铺垫' }]
  })
  assert.equal(result.score, 0)
  assert.equal(result.level, 'balanced')
  assert.equal(result.levelLabel, '均衡')
  assert.equal(result.issues[0].typeLabel, '节奏拖沓')
})

test('normalizePacingResult clamps score and maps dimensions', () => {
  const result = normalizePacingResult({
    score: 150,
    level: 'intense',
    dimensions: [
      { name: '事件密度', score: 90, note: '密集' },
      { name: '情绪起伏', score: -5, note: '平淡' }
    ]
  })
  assert.equal(result.score, 100)
  assert.equal(result.levelLabel, '紧凑')
  assert.equal(result.dimensions.length, 2)
  assert.equal(result.dimensions[0].score, 90)
  assert.equal(result.dimensions[1].score, 0)
  assert.equal(result.dimensions[0].name, '事件密度')
})

test('normalizePacingResult keeps suggestions and keeps all issue types with fallback labels', () => {
  const result = normalizePacingResult({
    suggestions: ['加强钩子', '压缩过渡段'],
    issues: [
      { type: 'NO_HOOK', issue: '缺钩子', suggestion: '首段埋悬念' },
      { type: 'UNKNOWN_TYPE', issue: '未知' }
    ]
  })
  assert.deepEqual(result.suggestions, ['加强钩子', '压缩过渡段'])
  assert.equal(result.issues.length, 2)
  assert.equal(result.issues[0].typeLabel, PACING_ISSUE_LABELS.NO_HOOK)
  assert.equal(result.issues[1].typeLabel, 'UNKNOWN_TYPE')
})
