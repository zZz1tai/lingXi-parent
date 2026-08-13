import test from 'node:test'
import assert from 'node:assert/strict'

import {
  cleanNovelIdeaDisplayText,
  hasNovelIdeaProtocol
} from '../src/utils/novelIdeaProtocol.js'

test('normal idea reply remains visible', () => {
  assert.equal(cleanNovelIdeaDisplayText('再确认两个关键细节。'), '再确认两个关键细节。')
})

test('protocol-only reply is hidden even when closing tag is missing', () => {
  const leaked = '[IDEA_ASK]{"questions":[{"question":"主角是谁？"}]}'
  assert.equal(cleanNovelIdeaDisplayText(leaked), '')
  assert.equal(hasNovelIdeaProtocol(leaked), true)
})

test('natural prefix is kept while protocol payload is removed', () => {
  const leaked = '明白了，我再确认一下。[IDEA_ASK]{"questions":[]}'
  assert.equal(cleanNovelIdeaDisplayText(leaked), '明白了，我再确认一下。')
})
