import test from 'node:test'
import assert from 'node:assert/strict'

import { formatDurationMs } from '../src/utils/douyinVideo.js'

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
