import assert from 'node:assert/strict'
import test from 'node:test'

import {
  buildContextSyncKey,
  fingerprintNovelContent,
  normalizeContextChanges,
  parseContextSyncHashes,
  toContextApplyChanges
} from '../src/views/novel/novelContextSync.js'

test('正文指纹稳定且能区分内容变化', () => {
  assert.equal(fingerprintNovelContent('江离推开门。'), fingerprintNovelContent('江离推开门。'))
  assert.notEqual(fingerprintNovelContent('江离推开门。'), fingerprintNovelContent('江离关上门。'))
  assert.equal(buildContextSyncKey({ userId: 2, workId: 7, chapterId: 31 }), '2:7:31')
})

test('候选清单只保留 ADD/UPDATE 且标记安全默认勾选项', () => {
  const changes = normalizeContextChanges([
    {
      resourceType: 'setting',
      operation: 'ADD',
      title: '  江离的新身份  ',
      content: '巡夜人。',
      evidence: '他亮出巡夜令',
      reason: '明确揭示身份'
    },
    {
      resourceType: 'foreshadow',
      operation: 'UPDATE',
      targetId: 22,
      title: '断手镯',
      description: '已经确认属于姐姐。',
      status: 'resolved',
      priority: 'high',
      evidence: '姐姐亲口承认',
      reason: '正文完成回收'
    },
    { resourceType: 'setting', operation: 'DELETE', title: '不应出现' }
  ])

  assert.equal(changes.length, 2)
  assert.equal(changes[0].title, '江离的新身份')
  assert.equal(changes[0].defaultSelected, true)
  assert.equal(changes[1].defaultSelected, true)
})

test('与现有资料重复的 ADD 转为更新并保持默认勾选', () => {
  const changes = normalizeContextChanges([
    {
      resourceType: 'setting',
      operation: 'ADD',
      title: ' 江离 ',
      settingType: 'character',
      content: '少年剑客，确认断手镯属于失踪的姐姐。',
      evidence: '江离认出断手镯属于失踪的姐姐',
      reason: '补充人物已确认的信息'
    },
    {
      resourceType: 'foreshadow',
      operation: 'ADD',
      title: '姐姐的去向',
      description: '断手镯证明姐姐曾到过井底。',
      status: 'buried',
      priority: 'high',
      evidence: '断手镯属于失踪的姐姐',
      reason: '形成可在后续回收的新线索'
    }
  ], {
    settings: [{ settingId: 11, title: '江离' }],
    foreshadows: [{ foreshadowId: 22, title: '断手镯' }]
  })

  assert.equal(changes[0].operation, 'UPDATE')
  assert.equal(changes[0].targetId, 11)
  assert.equal(changes[0].mergedFromAdd, true)
  assert.equal(changes[0].defaultSelected, true)
  assert.equal(changes[1].operation, 'ADD')
  assert.equal(changes[1].mergedFromAdd, false)
  assert.equal(changes[1].defaultSelected, true)
})

test('重复转更新后提交请求只含白名单字段且不含界面标记', () => {
  const [normalized] = normalizeContextChanges([
    {
      resourceType: 'foreshadow',
      operation: 'ADD',
      title: '断手镯',
      description: '已经确认属于姐姐。',
      status: 'resolved',
      priority: 'high',
      evidence: '姐姐亲口承认',
      reason: '正文完成回收'
    }
  ], { settings: [], foreshadows: [{ foreshadowId: 22, title: '断手镯' }] })

  const [payload] = toContextApplyChanges([normalized])

  assert.equal(payload.operation, 'UPDATE')
  assert.equal(payload.targetId, 22)
  assert.equal('mergedFromAdd' in payload, false)
  assert.equal('clientId' in payload, false)
})

test('应用请求移除 clientId 等界面字段', () => {
  const [payload] = toContextApplyChanges([
    {
      clientId: 'setting:ADD:new:0',
      defaultSelected: true,
      resourceType: 'setting',
      operation: 'ADD',
      title: '巡夜人',
      settingType: 'organization',
      content: '负责夜间巡城。',
      evidence: '巡夜人列队而过',
      reason: '出现了稳定组织设定'
    }
  ])

  assert.deepEqual(payload, {
    resourceType: 'setting',
    operation: 'ADD',
    settingType: 'organization',
    title: '巡夜人',
    content: '负责夜间巡城。',
    evidence: '巡夜人列队而过',
    reason: '出现了稳定组织设定'
  })
  assert.equal('clientId' in payload, false)
})

test('本地去重记录容错解析并过滤非法值', () => {
  assert.deepEqual(parseContextSyncHashes('{bad json'), {})
  assert.deepEqual(
    parseContextSyncHashes(JSON.stringify({
      '2:7:31': 'abcdef0123456789',
      invalid: 'not-a-hash'
    })),
    { '2:7:31': 'abcdef0123456789' }
  )
})
