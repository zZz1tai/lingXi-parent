import assert from 'node:assert/strict'
import test from 'node:test'

import {
  DEFAULT_NOVEL_LAYOUT,
  NOVEL_LAYOUT_LIMITS,
  fitNovelLayout,
  parseNovelLayout,
  resizeNovelPanel
} from '../src/views/novel/novelLayout.js'

test('小说分栏缓存损坏时恢复默认尺寸', () => {
  assert.deepEqual(parseNovelLayout('{bad json'), DEFAULT_NOVEL_LAYOUT)
})

test('小说分栏缓存会过滤无效值并限制极端尺寸', () => {
  assert.deepEqual(parseNovelLayout(JSON.stringify({
    railWidth: -1,
    chatWidth: 'not-a-number',
    drawerWidth: 9999
  })), {
    railWidth: NOVEL_LAYOUT_LIMITS.railMin,
    chatWidth: DEFAULT_NOVEL_LAYOUT.chatWidth,
    drawerWidth: NOVEL_LAYOUT_LIMITS.drawerMax
  })
})

test('作品栏不能缩到文字被逐字挤成竖排的宽度', () => {
  const layout = resizeNovelPanel({
    panel: 'rail',
    startLayout: DEFAULT_NOVEL_LAYOUT,
    deltaX: -1000,
    bodyWidth: 1800,
    workspaceWidth: 1476
  })

  assert.equal(layout.railWidth, NOVEL_LAYOUT_LIMITS.railMin)
})

test('拖动作品栏时为主工作区保留最小宽度', () => {
  const layout = resizeNovelPanel({
    panel: 'rail',
    startLayout: DEFAULT_NOVEL_LAYOUT,
    deltaX: 500,
    bodyWidth: 1200,
    workspaceWidth: 892
  })

  assert.equal(
    layout.railWidth,
    1200 - NOVEL_LAYOUT_LIMITS.outerSplitter - NOVEL_LAYOUT_LIMITS.stageMin
  )
})

test('聊天与目录分隔条按各自方向调整尺寸', () => {
  const chatLayout = resizeNovelPanel({
    panel: 'chat',
    startLayout: DEFAULT_NOVEL_LAYOUT,
    deltaX: 60,
    workspaceWidth: 1500
  })
  const drawerLayout = resizeNovelPanel({
    panel: 'drawer',
    startLayout: DEFAULT_NOVEL_LAYOUT,
    deltaX: 40,
    workspaceWidth: 1500
  })

  assert.equal(chatLayout.chatWidth, DEFAULT_NOVEL_LAYOUT.chatWidth + 60)
  assert.equal(drawerLayout.drawerWidth, DEFAULT_NOVEL_LAYOUT.drawerWidth - 40)
})

test('恢复布局时不会挤占正文的最低可写宽度', () => {
  const workspaceWidth = 1100
  const layout = fitNovelLayout({ chatWidth: 760, drawerWidth: 420 }, { workspaceWidth })
  const paperWidth = workspaceWidth
    - NOVEL_LAYOUT_LIMITS.workspaceSplitters
    - layout.chatWidth
    - layout.drawerWidth

  assert.ok(paperWidth >= NOVEL_LAYOUT_LIMITS.paperMin)
})
