import test from 'node:test'
import assert from 'node:assert/strict'

import {
  buildPolishMessage,
  buildStyleMessage,
  extractPolishBody,
  extractPolishChanges,
  findPolishTemplate,
  parsePolishMessage,
  parseStyleMessage,
  polishLabel,
  POLISH_CATEGORIES,
  POLISH_TEMPLATES
} from '../src/utils/novelPolish.js'

test('polish template library has 22 scenarios with unique ids', () => {
  assert.equal(POLISH_TEMPLATES.length, 22)
  const ids = POLISH_TEMPLATES.map(template => template.id)
  assert.equal(new Set(ids).size, ids.length)
  for (const template of POLISH_TEMPLATES) {
    assert.ok(template.id)
    assert.ok(template.name)
    assert.ok(template.description)
    assert.ok(POLISH_CATEGORIES.includes(template.category))
  }
})

test('findPolishTemplate returns the known template or null', () => {
  assert.equal(findPolishTemplate('de_ai_flavor')?.name, '去AI味')
  assert.equal(findPolishTemplate('not-exist'), null)
})

test('buildPolishMessage wraps template id and target with the marker', () => {
  const message = buildPolishMessage('de_ai_flavor', '他走进了房间。')
  assert.equal(message, '【精修】de_ai_flavor\n他走进了房间。')
})

test('parsePolishMessage returns template and target', () => {
  const parsed = parsePolishMessage('【精修】de_ai_flavor\n他走进了房间。')
  assert.equal(parsed.template.id, 'de_ai_flavor')
  assert.equal(parsed.target, '他走进了房间。')
})

test('parsePolishMessage tolerates leading whitespace', () => {
  const parsed = parsePolishMessage('  【精修】  pace_accelerate  \n  目标文字  ')
  assert.equal(parsed.template.id, 'pace_accelerate')
  assert.equal(parsed.target, '目标文字')
})

test('parsePolishMessage rejects plain or unknown messages', () => {
  assert.equal(parsePolishMessage('续写下一段'), null)
  assert.equal(parsePolishMessage('【精修】forged\n正文'), null)
  assert.equal(parsePolishMessage('【精修】\n正文'), null)
  assert.equal(parsePolishMessage(''), null)
})

test('extractPolishBody returns text before the change marker', () => {
  const content = '精修后正文内容\n\n## 修改点标注\n- [a] → [b]（原因：x）'
  assert.equal(extractPolishBody(content), '精修后正文内容')
})

test('extractPolishBody falls back to full content without marker', () => {
  assert.equal(extractPolishBody('普通回复全文'), '普通回复全文')
  assert.equal(extractPolishBody(''), '')
})

test('extractPolishChanges returns the change section only', () => {
  const content = '正文\n## 修改点标注\n- [a] → [b]（原因：x）'
  assert.equal(extractPolishChanges(content), '## 修改点标注\n- [a] → [b]（原因：x）')
  assert.equal(extractPolishChanges('没有标注的回复'), '')
})

test('buildStyleMessage wraps style card and target with the marker', () => {
  const message = buildStyleMessage(
    { title: '冷峻硬派', content: '短句、少修饰、克制留白。' },
    '他推开了门。'
  )
  assert.equal(message, '【文风】冷峻硬派\n短句、少修饰、克制留白。\n\n他推开了门。')
})

test('parseStyleMessage returns title, body and target', () => {
  const parsed = parseStyleMessage('【文风】冷峻硬派\n短句留白。\n\n他推开了门。')
  assert.equal(parsed.styleTitle, '冷峻硬派')
  assert.equal(parsed.styleBody, '短句留白。')
  assert.equal(parsed.target, '他推开了门。')
})

test('parseStyleMessage tolerates missing target and malformed messages', () => {
  assert.equal(parseStyleMessage('【文风】诙谐\n多用比喻。').target, '')
  assert.equal(parseStyleMessage('续写下一段'), null)
  assert.equal(parseStyleMessage('【文风】只有标题'), null)
  assert.equal(parseStyleMessage('【文风】\n正文'), null)
})

test('polishLabel renders readable names for marker messages', () => {
  assert.equal(polishLabel('【精修】de_ai_flavor\n正文'), '【精修】去AI味')
  assert.equal(polishLabel('【文风】冷峻硬派\n短句留白。\n\n正文'), '【文风】冷峻硬派')
  assert.equal(polishLabel('续写下一段'), '续写下一段')
})
