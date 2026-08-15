import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

import {
  nextNovelChapterNo,
  pickNovelChapter,
  planNovelContinuation
} from '../src/views/novel/novelChapter.js'

const pageSource = await readFile(
  new URL('../src/views/novel/index.vue', import.meta.url),
  'utf8'
)

test('刷新章节后能定位刚创建的下一章', () => {
  const chapters = [
    { chapterId: 11, chapterNo: 1 },
    { chapterId: 12, chapterNo: 2 }
  ]

  assert.equal(pickNovelChapter(chapters, { chapterNo: 2 }).chapterId, 12)
  assert.equal(pickNovelChapter(chapters, { chapterId: '11' }).chapterNo, 1)
  assert.equal(pickNovelChapter(chapters).chapterId, 11)
  assert.equal(nextNovelChapterNo([{ chapterNo: 1 }, { chapterNo: 3 }]), 4)
})

test('上次失败留下空末章时直接复用，不重复创建章节', () => {
  const first = { chapterId: 11, chapterNo: 1, content: '第一章结尾' }
  const emptySecond = { chapterId: 12, chapterNo: 2, content: '' }

  assert.deepEqual(planNovelContinuation([first, emptySecond]), {
    createNew: false,
    chapterNo: 2,
    sourceChapter: first,
    targetChapter: emptySecond
  })
  assert.equal(planNovelContinuation([first]).createNew, true)
})

test('自动续写持有创作面板引用并等待章节切换完成', () => {
  assert.match(pageSource, /const chatComposerRef = ref\(null\)/)
  assert.match(pageSource, /const plan = planNovelContinuation\(chapters\.value\)/)
  assert.match(pageSource, /await nextTick\(\)[\s\S]*?activeComposer\.send\(/)
  assert.match(pageSource, /完整章节正文，目标约 2500 字（建议 2300～2700 字）/)
})
