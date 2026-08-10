const MARKDOWN_HEADING_PATTERN = /^\s{0,3}(#{1,6})[\t ]+(.+?)[\t ]*#*[\t ]*$/

function normalizeLineBreaks(value) {
  return String(value || '').replace(/\r\n?/g, '\n')
}

function escapeHtml(value) {
  return String(value || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function renderInline(value) {
  return escapeHtml(value)
    .replace(/\*\*([^*\n]+)\*\*/g, '<strong>$1</strong>')
    .replace(/__([^_\n]+)__/g, '<strong>$1</strong>')
    .replace(/`([^`\n]+)`/g, '<code>$1</code>')
}

/**
 * 将开头的 Markdown 标题拆成单独字段，让手稿编辑器能隐藏 # 并加粗标题，
 * 同时仍以 Markdown 原文与后端交换，避免改变已有数据格式。
 */
export function splitLeadingMarkdownHeading(value) {
  const normalized = normalizeLineBreaks(value)
  const lines = normalized.split('\n')
  const headingIndex = lines.findIndex(line => line.trim())
  const match = headingIndex >= 0 ? lines[headingIndex].match(MARKDOWN_HEADING_PATTERN) : null

  if (!match) {
    return { level: 0, title: '', body: normalized }
  }

  const bodyLines = lines.slice(headingIndex + 1)
  while (bodyLines.length && !bodyLines[0].trim()) bodyLines.shift()

  return {
    level: match[1].length,
    title: match[2].trim(),
    body: bodyLines.join('\n')
  }
}

export function joinMarkdownHeading({ level, title, body }) {
  const normalizedBody = normalizeLineBreaks(body)
  const normalizedTitle = String(title || '').trim()
  if (!level || !normalizedTitle) return normalizedBody

  const heading = `${'#'.repeat(Math.min(6, Math.max(1, level)))} ${normalizedTitle}`
  return normalizedBody ? `${heading}\n\n${normalizedBody.replace(/^\n+/, '')}` : heading
}

/**
 * 小说对话只需要标题、行文本和少量行内强调。这里主动转义全部 HTML，
 * 避免把模型回复当作可执行 DOM 注入页面。
 */
export function renderNovelMarkdown(value) {
  return normalizeLineBreaks(value)
    .split('\n')
    .map(line => {
      const heading = line.match(MARKDOWN_HEADING_PATTERN)
      if (heading) {
        const level = heading[1].length
        return `<h${level} class="nk-md-heading is-level-${level}">${renderInline(heading[2].trim())}</h${level}>`
      }
      if (!line.trim()) return '<div class="nk-md-line is-empty"><br></div>'
      return `<div class="nk-md-line">${renderInline(line)}</div>`
    })
    .join('')
}
