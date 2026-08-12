// 聊天正文 Markdown 渲染的安全边界。
// LLM 生成的正文经 v-html 输出，必须：
// - 转义所有原始 HTML（仅放行受限的 https <video> 块，用于视频包装功能）；
// - 链接与图片只允许 https 与本地回环 http。

export function escapeHtml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/"/g, '&quot;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

export function isSafeExternalUrl(url) {
  if (typeof url !== 'string' || !url) return false
  const trimmed = url.trim()
  if (trimmed.startsWith('https://')) return true
  return (
    trimmed.startsWith('http://localhost')
    || trimmed.startsWith('http://127.0.0.1')
    || trimmed.startsWith('http://[::1]')
  )
}

// 只放行完整的 https <video src="...">…</video> 块（无任何额外属性），
// 其余原始 HTML 一律转义为纯文本。
export function sanitizeRawHtmlBlock(raw) {
  const trimmed = String(raw ?? '').trim()
  if (!/^<video\s+src="[^"]+">[\s\S]*<\/video>$/.test(trimmed)) {
    return escapeHtml(raw)
  }
  const srcMatch = trimmed.match(/^<video\s+src="([^"]+)">/)
  if (!srcMatch || !isSafeExternalUrl(srcMatch[1])) {
    return escapeHtml(raw)
  }
  return trimmed
}
