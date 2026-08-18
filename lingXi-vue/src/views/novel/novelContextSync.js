export const CONTEXT_SYNC_HASH_STORAGE_KEY = 'lingxi-novel-context-sync-hashes-v1'
export const CONTEXT_APPLIED_HASH_STORAGE_KEY = 'lingxi-novel-context-applied-hashes-v1'

const RESOURCE_TYPES = new Set(['setting', 'foreshadow'])
const OPERATIONS = new Set(['ADD', 'UPDATE'])
const APPLY_FIELDS = [
  'resourceType', 'operation', 'targetId', 'settingType', 'title', 'content',
  'description', 'status', 'priority', 'keyword', 'resolveChapterNo', 'evidence', 'reason'
]

/**
 * 生成仅用于浏览器去重的稳定正文指纹；事务防过期使用服务端返回的 SHA-256。
 */
export function fingerprintNovelContent(content) {
  const bytes = new TextEncoder().encode(String(content ?? ''))
  let hash = 14695981039346656037n
  const prime = 1099511628211n
  const mask = 0xffffffffffffffffn
  for (const byte of bytes) {
    hash ^= BigInt(byte)
    hash = (hash * prime) & mask
  }
  return hash.toString(16).padStart(16, '0')
}

export function buildContextSyncKey({ userId, workId, chapterId }) {
  if (userId == null || workId == null || chapterId == null) return ''
  return String(userId) + ':' + String(workId) + ':' + String(chapterId)
}

export function parseContextSyncHashes(raw) {
  if (!raw) return {}
  try {
    const parsed = JSON.parse(raw)
    if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') return {}
    return Object.fromEntries(
      Object.entries(parsed)
        .filter(([key, value]) =>
          key.length <= 160 && typeof value === 'string' && /^[a-f0-9]{8,128}$/i.test(value)
        )
        .slice(-500)
    )
  } catch {
    return {}
  }
}

export function normalizeContextChanges(changes, existing = {}) {
  if (!Array.isArray(changes)) return []
  const settings = Array.isArray(existing.settings) ? existing.settings : []
  const foreshadows = Array.isArray(existing.foreshadows) ? existing.foreshadows : []
  const normalizedTitle = value =>
    typeof value === 'string' ? value.trim().toLowerCase() : ''
  const settingTitleIds = new Map(
    settings.map(item => [normalizedTitle(item.title), item.settingId])
  )
  const foreshadowTitleIds = new Map(
    foreshadows.map(item => [normalizedTitle(item.title), item.foreshadowId])
  )
  return changes
    .filter(change =>
      change && RESOURCE_TYPES.has(change.resourceType) && OPERATIONS.has(change.operation)
      && typeof change.title === 'string' && change.title.trim()
    )
    .slice(0, 40)
    .map((change, index) => {
      const title = change.title.trim()
      const matchedId = change.operation === 'ADD'
        ? (change.resourceType === 'setting'
            ? settingTitleIds.get(normalizedTitle(title))
            : foreshadowTitleIds.get(normalizedTitle(title)))
        : undefined
      const mergedFromAdd = matchedId != null
      return {
        ...change,
        title,
        operation: mergedFromAdd ? 'UPDATE' : change.operation,
        targetId: mergedFromAdd ? matchedId : change.targetId,
        mergedFromAdd,
        clientId: [
          change.resourceType,
          mergedFromAdd ? 'UPDATE' : change.operation,
          mergedFromAdd ? matchedId : (change.targetId ?? 'new'),
          index
        ].join(':'),
        defaultSelected: change.operation === 'ADD'
          || mergedFromAdd
          || (change.resourceType === 'foreshadow' && change.operation === 'UPDATE')
      }
    })
}

/** 移除仅供界面使用的字段，提交给 Java 的仍是严格白名单。 */
export function toContextApplyChanges(changes) {
  return (Array.isArray(changes) ? changes : []).map(change =>
    Object.fromEntries(
      APPLY_FIELDS
        .filter(field => change[field] !== undefined)
        .map(field => [field, change[field]])
    )
  )
}
