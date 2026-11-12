import test from 'node:test'
import assert from 'node:assert/strict'

import {
  collectDueDanmaku,
  formatCount,
  formatDurationMs,
  formatRelativeTime
} from '../src/utils/douyinVideo.js'

test('formats millisecond duration as mm:ss', () => {
  assert.equal(formatDurationMs(21567), '00:21')
  assert.equal(formatDurationMs(65000), '01:05')
})

test('formats durations over one hour as h:mm:ss', () => {
  assert.equal(formatDurationMs(3661000), '1:01:01')
  assert.equal(formatDurationMs(7200000), '2:00:00')
})

test('treats invalid input as zero duration', () => {
  assert.equal(formatDurationMs(null), '00:00')
  assert.equal(formatDurationMs(undefined), '00:00')
  assert.equal(formatDurationMs('not-a-number'), '00:00')
  assert.equal(formatDurationMs(-500), '00:00')
})

test('accepts numeric strings from JSON payloads', () => {
  assert.equal(formatDurationMs('61000'), '01:01')
})

test('formats counts with chinese units', () => {
  assert.equal(formatCount(999), '999')
  assert.equal(formatCount(12345), '1.2万')
  assert.equal(formatCount(120000), '12万')
  assert.equal(formatCount(340000000), '3.4亿')
  assert.equal(formatCount(null), '0')
  assert.equal(formatCount('bad'), '0')
})

test('formats relative time across buckets', () => {
  const now = Date.UTC(2026, 10, 11, 12, 0, 0)
  const sec = (ms) => (now - ms) / 1000
  assert.equal(formatRelativeTime(sec(0), now), '刚刚')
  assert.equal(formatRelativeTime(sec(30 * 1000), now), '刚刚')
  assert.equal(formatRelativeTime(sec(5 * 60 * 1000), now), '5分钟前')
  assert.equal(formatRelativeTime(sec(3 * 60 * 60 * 1000), now), '3小时前')
  assert.equal(formatRelativeTime(sec(7 * 24 * 60 * 60 * 1000), now), '7天前')
  assert.equal(formatRelativeTime(0, now), '')
})

test('collects due danmaku in order and advances the pointer', () => {
  const list = [
    { offsetMs: 0, text: 'a' },
    { offsetMs: 100, text: 'b' },
    { offsetMs: 500, text: 'c' },
    { offsetMs: 900, text: 'd' }
  ]
  const first = collectDueDanmaku(list, 0, 500)
  assert.deepEqual(first.due.map((d) => d.text), ['a', 'b', 'c'])
  assert.equal(first.nextPointer, 3)
  const second = collectDueDanmaku(list, first.nextPointer, 400)
  assert.deepEqual(second.due, [])
  assert.equal(second.nextPointer, 3)
  const third = collectDueDanmaku(list, second.nextPointer, 1000)
  assert.deepEqual(third.due.map((d) => d.text), ['d'])
  assert.equal(third.nextPointer, 4)
})

test('collectDueDanmaku tolerates bad input', () => {
  assert.deepEqual(collectDueDanmaku(null, 0, 100), { due: [], nextPointer: 0 })
  assert.deepEqual(collectDueDanmaku([], 5, 100), { due: [], nextPointer: 0 })
  const out = collectDueDanmaku([{ offsetMs: 'not-a-number', text: 'x' }], 0, 100)
  assert.equal(out.due.length, 1)
  assert.equal(out.nextPointer, 1)
})
