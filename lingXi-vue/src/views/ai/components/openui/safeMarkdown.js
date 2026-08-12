import { Marked } from 'marked'

// OpenUI 的 Markdown 严格渲染器：原始 HTML 一律转义为纯文本，
// 链接与图片只允许 https 与本地回环 http，防止脚本注入。

function escapeHtml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/"/g, '&quot;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

function isSafeMarkdownUrl(url) {
  if (typeof url !== 'string' || !url) return false
  const trimmed = url.trim()
  if (trimmed.startsWith('https://')) return true
  return (
    trimmed.startsWith('http://localhost')
    || trimmed.startsWith('http://127.0.0.1')
    || trimmed.startsWith('http://[::1]')
  )
}

const renderer = {
  html({ text }) {
    return text ? escapeHtml(text) : ''
  },
  link({ href, title, text }) {
    if (!isSafeMarkdownUrl(href)) return escapeHtml(text || href || '')
    const attrs = `href="${escapeHtml(href)}"`
      + (title ? ` title="${escapeHtml(title)}"` : '')
      + ' rel="noopener noreferrer" target="_blank"'
    return `<a ${attrs}>${text}</a>`
  },
  image({ href, title, text }) {
    if (!isSafeMarkdownUrl(href)) return escapeHtml(text || '')
    const alt = escapeHtml(text || '')
    const attrs = `src="${escapeHtml(href)}" alt="${alt}" loading="lazy"`
      + (title ? ` title="${escapeHtml(title)}"` : '')
    return `<img ${attrs} />`
  }
}

const openUiMarked = new Marked({ gfm: true, breaks: true, renderer })

export function renderOpenUiMarkdown(content) {
  return content ? openUiMarked.parse(content) : ''
}
