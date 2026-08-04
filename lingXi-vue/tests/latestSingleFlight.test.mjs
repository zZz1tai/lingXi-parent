import assert from 'node:assert/strict'
import test from 'node:test'

import {
  createLatestSingleFlight,
  smartQuestionRequestKey
} from '../src/utils/latestSingleFlight.js'

test('deduplicates concurrent requests with the same key', async () => {
  const loader = createLatestSingleFlight()
  let calls = 0
  let resolveRequest
  const task = () => {
    calls += 1
    return new Promise(resolve => {
      resolveRequest = resolve
    })
  }

  const first = loader.run('session:history', task)
  const duplicate = loader.run('session:history', task)

  assert.strictEqual(first, duplicate)
  assert.equal(calls, 0)
  await Promise.resolve()
  assert.equal(calls, 1)
  resolveRequest(['问题一'])
  assert.deepEqual(await first, { status: 'applied', value: ['问题一'] })
  assert.deepEqual(await loader.run('session:history', task), { status: 'cached' })
  assert.equal(calls, 1)
})

test('marks an older request stale when a newer history starts', async () => {
  const loader = createLatestSingleFlight()
  let resolveOld
  const oldRequest = loader.run('old', () => new Promise(resolve => {
    resolveOld = resolve
  }))
  await Promise.resolve()

  const latestRequest = loader.run('latest', async () => ['最新问题'])
  resolveOld(['过期问题'])

  assert.deepEqual(await oldRequest, { status: 'stale', value: ['过期问题'] })
  assert.deepEqual(await latestRequest, { status: 'applied', value: ['最新问题'] })
})

test('builds a stable key from session, roles, and message content only', () => {
  const first = smartQuestionRequestKey('session-1', [
    { id: 1, messageType: 'user', content: '你好' },
    { id: 2, messageType: 'assistant', content: '你好，有什么可以帮你？' }
  ])
  const sameConversation = smartQuestionRequestKey('session-1', [
    { id: 99, isUser: true, content: '你好' },
    { id: 100, role: 'assistant', content: '你好，有什么可以帮你？' }
  ])

  assert.equal(first, sameConversation)
  assert.notEqual(first, smartQuestionRequestKey('session-2', [
    { messageType: 'user', content: '你好' }
  ]))
})
