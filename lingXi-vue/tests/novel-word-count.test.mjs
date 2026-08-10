import test from 'node:test'
import assert from 'node:assert/strict'
import { countNovelCharacters } from '../src/views/novel/novelWordCount.js'

test('counts manuscript characters without whitespace', () => {
  assert.equal(countNovelCharacters('无常，\n 欠我\t一炷香。'), 9)
})

test('ignores unicode spaces used by browser whitespace matching', () => {
  assert.equal(countNovelCharacters('甲\u00a0乙\u3000丙\ufeff丁'), 4)
})

test('matches browser UTF-16 length semantics for supplementary characters', () => {
  assert.equal(countNovelCharacters('甲😀乙'), 4)
  assert.equal(countNovelCharacters(null), 0)
})
