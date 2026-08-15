/**
 * 小说工作台分栏尺寸计算。
 *
 * 尺寸使用像素保存，恢复时会按当前可用空间重新收敛，避免换屏幕后面板被挤出视口。
 */

export const NOVEL_LAYOUT_STORAGE_KEY = 'lingxi.novel.workspace-layout.v1'

export const DEFAULT_NOVEL_LAYOUT = Object.freeze({
  railWidth: 292,
  chatWidth: 520,
  drawerWidth: 272
})

export const NOVEL_LAYOUT_LIMITS = Object.freeze({
  railMin: 256,
  railMax: 420,
  chatMin: 300,
  chatMax: 760,
  paperMin: 360,
  drawerMin: 220,
  drawerMax: 420,
  stageMin: 908,
  outerSplitter: 16,
  workspaceSplitters: 28
})

function finiteNumber(value, fallback) {
  const number = Number(value)
  return Number.isFinite(number) ? number : fallback
}

function clamp(value, min, max) {
  const safeMax = Math.max(min, max)
  return Math.min(Math.max(value, min), safeMax)
}

export function normalizeNovelLayout(value = {}) {
  return {
    railWidth: clamp(
      finiteNumber(value.railWidth, DEFAULT_NOVEL_LAYOUT.railWidth),
      NOVEL_LAYOUT_LIMITS.railMin,
      NOVEL_LAYOUT_LIMITS.railMax
    ),
    chatWidth: clamp(
      finiteNumber(value.chatWidth, DEFAULT_NOVEL_LAYOUT.chatWidth),
      NOVEL_LAYOUT_LIMITS.chatMin,
      NOVEL_LAYOUT_LIMITS.chatMax
    ),
    drawerWidth: clamp(
      finiteNumber(value.drawerWidth, DEFAULT_NOVEL_LAYOUT.drawerWidth),
      NOVEL_LAYOUT_LIMITS.drawerMin,
      NOVEL_LAYOUT_LIMITS.drawerMax
    )
  }
}

export function parseNovelLayout(value) {
  if (!value) return { ...DEFAULT_NOVEL_LAYOUT }
  try {
    return normalizeNovelLayout(typeof value === 'string' ? JSON.parse(value) : value)
  } catch {
    return { ...DEFAULT_NOVEL_LAYOUT }
  }
}

/** 根据当前容器尺寸修正布局，始终为正文保留最低可写宽度。 */
export function fitNovelLayout(value, dimensions = {}) {
  const layout = normalizeNovelLayout(value)
  const bodyWidth = finiteNumber(dimensions.bodyWidth, Number.POSITIVE_INFINITY)
  const workspaceWidth = finiteNumber(dimensions.workspaceWidth, Number.POSITIVE_INFINITY)

  if (Number.isFinite(bodyWidth)) {
    const railMax = Math.min(
      NOVEL_LAYOUT_LIMITS.railMax,
      bodyWidth - NOVEL_LAYOUT_LIMITS.outerSplitter - NOVEL_LAYOUT_LIMITS.stageMin
    )
    layout.railWidth = clamp(layout.railWidth, NOVEL_LAYOUT_LIMITS.railMin, railMax)
  }

  if (Number.isFinite(workspaceWidth)) {
    const drawerMax = Math.min(
      NOVEL_LAYOUT_LIMITS.drawerMax,
      workspaceWidth
        - NOVEL_LAYOUT_LIMITS.workspaceSplitters
        - NOVEL_LAYOUT_LIMITS.chatMin
        - NOVEL_LAYOUT_LIMITS.paperMin
    )
    layout.drawerWidth = clamp(layout.drawerWidth, NOVEL_LAYOUT_LIMITS.drawerMin, drawerMax)

    const chatMax = Math.min(
      NOVEL_LAYOUT_LIMITS.chatMax,
      workspaceWidth
        - NOVEL_LAYOUT_LIMITS.workspaceSplitters
        - layout.drawerWidth
        - NOVEL_LAYOUT_LIMITS.paperMin
    )
    layout.chatWidth = clamp(layout.chatWidth, NOVEL_LAYOUT_LIMITS.chatMin, chatMax)
  }

  return layout
}

/**
 * 将分隔条的横向位移换算成新尺寸。
 * 目录分隔条向右移动时，目录本身会变窄，因此使用相反方向的增量。
 */
export function resizeNovelPanel({ panel, startLayout, deltaX, bodyWidth, workspaceWidth }) {
  const start = normalizeNovelLayout(startLayout)
  const next = { ...start }
  const movement = finiteNumber(deltaX, 0)

  if (panel === 'rail') {
    next.railWidth = start.railWidth + movement
    const fittedRail = fitNovelLayout(next, { bodyWidth })
    const workspaceDelta = fittedRail.railWidth - start.railWidth
    return fitNovelLayout(fittedRail, {
      bodyWidth,
      workspaceWidth: Number.isFinite(workspaceWidth) ? workspaceWidth - workspaceDelta : workspaceWidth
    })
  }
  if (panel === 'chat') next.chatWidth = start.chatWidth + movement
  if (panel === 'drawer') next.drawerWidth = start.drawerWidth - movement

  return fitNovelLayout(next, { bodyWidth, workspaceWidth })
}
