/**
 * AI 小说节奏控制系统前端纯逻辑。
 * 提供 5 档节奏档位元数据、请求参数构造与节奏分析结果归一化，
 * 供作品信息弹窗与章节节奏分析面板共用。
 */

/** 节奏档位元数据（id 与 Python NOVEL_PACING_LEVELS 保持一致）。 */
export const PACING_LEVELS = [
  { id: 'relaxed', label: '舒缓', desc: '慢热克制，场景细腻铺陈' },
  { id: 'steady', label: '平稳', desc: '匀速推进，娓娓道来' },
  { id: 'balanced', label: '均衡', desc: '张弛有度，主流节奏' },
  { id: 'intense', label: '紧凑', desc: '事件密集，冲突环环相扣' },
  { id: 'rapid', label: '激烈', desc: '全程高压，悬念不断' }
]

/** 默认档位。 */
export const DEFAULT_PACING_LEVEL = 'balanced'

/** 浏览器端节奏分析缓存命名空间。 */
export const PACING_CACHE_STORAGE_KEY = 'lingxi:novel:pacing-analysis:v1'

/**
 * 构造按用户、作品和章节隔离的节奏分析缓存键。
 * 短篇正文使用 work 作用域，长篇章节使用 chapter:<chapterId> 作用域。
 */
export function buildPacingCacheKey({ userId, workId, chapterId }) {
  const normalizedWorkId = String(workId ?? '').trim()
  if (!normalizedWorkId) return ''
  const normalizedUserId = String(userId ?? '').trim() || 'anonymous'
  const normalizedChapterId = String(chapterId ?? '').trim()
  const scope = normalizedChapterId ? `chapter:${normalizedChapterId}` : 'work'
  return `${normalizedUserId}:${normalizedWorkId}:${scope}`
}

/** 安全解析浏览器缓存，只保留包含分析结果的记录。 */
export function parsePacingCache(raw) {
  if (!raw) return {}
  try {
    const parsed = JSON.parse(raw)
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return {}
    return Object.fromEntries(
      Object.entries(parsed).filter(([key, record]) =>
        key && record && typeof record === 'object' &&
        record.result && typeof record.result === 'object' && !Array.isArray(record.result)
      )
    )
  } catch {
    return {}
  }
}

/** 档位中文名映射（含容错：未知档位回退到均衡）。 */
export function pacingLabel(level) {
  return PACING_LEVELS.find(item => item.id === level)?.label || '均衡'
}

/** 档位描述映射。 */
export function pacingDesc(level) {
  return PACING_LEVELS.find(item => item.id === level)?.desc || ''
}

/** 节奏分析问题类型的白名单映射（与 Python _PACING_ISSUE_TYPES 一致）。 */
export const PACING_ISSUE_LABELS = {
  PLODDING: '节奏拖沓',
  RUSHED: '推进过急',
  MONOTONE: '缺乏起伏',
  PADDING: '注水填充',
  NO_HOOK: '缺少钩子'
}

/**
 * 构造节奏分析请求。
 * @param {{workName: string, genre?: string, chapterTitle?: string, pacingLevel?: string, content: string}} params
 */
export function buildPacingRequest({ workName, genre, chapterTitle, pacingLevel, content }) {
  const request = { workName: workName || '', content: content || '' }
  if (genre) request.genre = genre
  if (chapterTitle) request.chapterTitle = chapterTitle
  request.pacingLevel = pacingLevel || DEFAULT_PACING_LEVEL
  return request
}

/**
 * 归一化 Python 节奏分析结果，保证前端面板渲染字段齐全。
 * @param {{score?: number, scoreNote?: string, level?: string, levelNote?: string,
 *   summary?: string, dimensions?: Array, issues?: Array, suggestions?: string[]}} raw
 */
export function normalizePacingResult(raw) {
  if (!raw || typeof raw !== 'object') return null
  const score = Number.isFinite(Number(raw.score)) ? Math.max(0, Math.min(100, Number(raw.score))) : 0
  const level = PACING_LEVELS.some(item => item.id === raw.level) ? raw.level : DEFAULT_PACING_LEVEL
  const dimensions = Array.isArray(raw.dimensions) ? raw.dimensions : []
  const issues = Array.isArray(raw.issues)
    ? raw.issues
        .filter(item => item && typeof item === 'object')
        .map(item => ({
          type: PACING_ISSUE_LABELS[item.type] ? item.type : item.type || '',
          typeLabel: PACING_ISSUE_LABELS[item.type] || item.type || '问题',
          position: String(item.position || ''),
          issue: String(item.issue || ''),
          suggestion: String(item.suggestion || '')
        }))
    : []
  const suggestions = Array.isArray(raw.suggestions)
    ? raw.suggestions.map(item => String(item || '')).filter(Boolean)
    : []
  return {
    score,
    scoreNote: String(raw.scoreNote || ''),
    level,
    levelLabel: pacingLabel(level),
    levelNote: String(raw.levelNote || ''),
    summary: String(raw.summary || ''),
    dimensions: dimensions
      .filter(item => item && typeof item === 'object')
      .map(item => ({
        name: String(item.name || ''),
        score: Number.isFinite(Number(item.score)) ? Math.max(0, Math.min(100, Number(item.score))) : 0,
        note: String(item.note || '')
      }))
      .filter(item => item.name),
    issues,
    suggestions
  }
}
