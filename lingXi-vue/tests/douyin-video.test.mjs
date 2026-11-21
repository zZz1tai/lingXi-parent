import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { runInNewContext } from 'node:vm'

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

test('skips expired danmaku after seeking or receiving data late', () => {
  const list = [100, 800, 1000, 1500, 2000, 2001].map((offsetMs) => ({ offsetMs }))
  const result = collectDueDanmaku(list, 0, 2000, 1000)
  assert.deepEqual(result.due.map(({ offsetMs }) => offsetMs), [1000, 1500, 2000])
  assert.equal(result.nextPointer, 5)
  assert.deepEqual(collectDueDanmaku(list, 0, 800, 0).due, list.slice(0, 2))
})

const explorePage = readFileSync(new URL('../src/views/aiVedio/douyinExplore/index.vue', import.meta.url), 'utf8')
const exploreScript = explorePage.match(/<script setup[^>]*>([\s\S]*?)<\/script>/)[1]
  .replace(/^import\s+[\s\S]*?\s+from\s+['"][^'"]+['"]\s*$/gm, '')

function createExploreState(api) {
  return runInNewContext(`${exploreScript}
    ;({ playCard, loadMoreComments, comments, commentsPage, commentsLoading, pageLoading, keyword, doSearch, loadList });`, {
    ref: (value) => ({ value }),
    computed: (get) => ({ get value() { return get() } }),
    getCurrentInstance: () => ({ proxy: { $modal: { msgWarning() {} } } }),
    useRouter: () => ({}),
    onMounted() {},
    onBeforeUnmount() {},
    douyinStreamUrl: (token) => token,
    getDouyinVideoPage: async () => ({ data: { commentsHasMore: true } }),
    ...api
  })
}

function deferred() {
  let resolve
  const promise = new Promise((done) => { resolve = done })
  return { promise, resolve }
}

test('切换并重新打开同一个视频时，迟到的评论只会留在原播放轮次', async () => {
  const oldPage = deferred()
  const newPage = deferred()
  const pendingPages = [oldPage, newPage]
  const requestedPages = []
  const ui = createExploreState({
    getDouyinComments: (awemeId, page) => {
      requestedPages.push(page)
      if (page === 0) {
        return Promise.resolve({ data: { items: [{ cid: awemeId + '-first' }], hasMore: true } })
      }
      return pendingPages.shift().promise
    }
  })
  const video = { awemeId: 'a', playToken: 'a' }

  await ui.playCard(video)
  const oldRequest = ui.loadMoreComments()
  await ui.playCard({ awemeId: 'b', playToken: 'b' })
  await ui.playCard(video)
  const newRequest = ui.loadMoreComments()

  oldPage.resolve({ data: { items: [{ cid: 'old' }], hasMore: false } })
  await oldRequest
  assert.deepEqual(Array.from(ui.comments.value, (item) => item.cid), ['a-first'])
  assert.equal(ui.commentsPage.value, 0)
  assert.equal(ui.commentsLoading.value, true)

  newPage.resolve({ data: { items: [{ cid: 'new' }], hasMore: false } })
  await newRequest
  assert.deepEqual(Array.from(ui.comments.value, (item) => item.cid), ['a-first', 'new'])
  assert.equal(ui.commentsPage.value, 1)
  assert.equal(ui.commentsLoading.value, false)
  assert.deepEqual(requestedPages, [0, 1, 0, 0, 1])
})

test('首屏评论不等待推荐，推荐返回后也不会覆盖评论', async () => {
  const details = deferred()
  const ui = createExploreState({
    getDouyinVideoPage: () => details.promise,
    getDouyinComments: async () => ({ data: { items: [{ cid: 'first' }], hasMore: true } })
  })
  await ui.playCard({ awemeId: 'a', playToken: 'a' })
  assert.equal(ui.comments.value[0].cid, 'first')
  assert.equal(ui.pageLoading.value, true)

  details.resolve({ data: { comments: [{ cid: 'obsolete' }] } })
  await new Promise((resolve) => setImmediate(resolve))
  assert.equal(ui.pageLoading.value, false)
  assert.equal(ui.comments.value[0].cid, 'first')
})

test('编辑搜索框但未提交时，下一页仍使用已提交的关键词', async () => {
  const requests = []
  const firstPage = deferred()
  const ui = createExploreState({
    searchDouyin: (keyword, page) => {
      requests.push([keyword, page])
      return page === 0 ? firstPage.promise : Promise.resolve({ data: { items: [] } })
    }
  })

  ui.keyword.value = '第一条'
  const request = ui.doSearch()
  ui.keyword.value = '尚未提交'
  firstPage.resolve({ data: { items: [{ awemeId: 'a' }], hasMore: true } })
  await request
  await ui.loadList(false)
  assert.deepEqual(requests, [['第一条', 0], ['第一条', 1]])
})
