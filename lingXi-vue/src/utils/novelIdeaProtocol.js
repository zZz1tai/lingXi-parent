const IDEA_PROTOCOL_MARKER = /\[\/?IDEA_(?:ASK|DOC)\]/

/**
 * 清理小说构思 Agent 的内部块协议。
 *
 * 正常情况下协议已由 Python 转为 clarification / idea_doc 事件；这里仅作为
 * 跨版本部署或异常分片时的最后一道展示兜底，绝不把标记和 JSON 放进气泡。
 */
export function cleanNovelIdeaDisplayText(value) {
  if (typeof value !== 'string') return ''
  const text = value.trim()
  if (!text) return ''

  const markerIndex = text.search(IDEA_PROTOCOL_MARKER)
  if (markerIndex < 0) return text
  return text.slice(0, markerIndex).trim()
}

export function hasNovelIdeaProtocol(value) {
  return typeof value === 'string' && IDEA_PROTOCOL_MARKER.test(value)
}
