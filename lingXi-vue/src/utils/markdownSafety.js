// 聊天正文 Markdown 渲染的安全边界。
// LLM 生成的正文经 v-html 输出，必须：
// - 转义所有原始 HTML（仅重建安全地址的 <video> 块，用于视频包装功能）；
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
  if (/[\u0000-\u001f\u007f\\]/.test(url)) return false
  try {
    const parsed = new URL(url.trim())
    if (parsed.username || parsed.password) return false
    return parsed.protocol === 'https:' || (
      parsed.protocol === 'http:'
      && ['localhost', '127.0.0.1', '[::1]'].includes(parsed.hostname)
    )
  } catch {
    return false
  }
}

// 只接受完整的安全地址 <video src="...">…</video> 块（无任何额外属性），
// 其余原始 HTML 一律转义为纯文本。
export function sanitizeRawHtmlBlock(raw) {
  const trimmed = String(raw ?? '').trim()
  const match = trimmed.match(/^<video\s+src="([^"]+)">([\s\S]*)<\/video>$/)
  if (!match || !isSafeExternalUrl(match[1])) {
    return escapeHtml(raw)
  }
  // 重建标签并转义回退内容，不能把 video 内嵌的脚本或事件属性原样放行。
  return `<video src="${escapeHtml(match[1])}">${escapeHtml(match[2])}</video>`
}

// 所有聊天渲染器共享安全边界；链接正文仍须逐个解析 token，不能直接拼接原始 text。
export const safeMarkdownRenderer = {
  html({ text }) {
    return sanitizeRawHtmlBlock(text || '')
  },
  link({ href, title, text, tokens }) {
    if (!isSafeExternalUrl(href)) return escapeHtml(text || href || '')
    const attrs = `href="${escapeHtml(href)}"`
      + (title ? ` title="${escapeHtml(title)}"` : '')
      + ' rel="noopener noreferrer" target="_blank"'
    return `<a ${attrs}>${this.parser.parseInline(tokens)}</a>`
  },
  image({ href, title, text }) {
    if (!isSafeExternalUrl(href)) return escapeHtml(text || '')
    const attrs = `src="${escapeHtml(href)}" alt="${escapeHtml(text || '')}" loading="lazy"`
      + (title ? ` title="${escapeHtml(title)}"` : '')
    return `<img ${attrs} />`
  }
}
