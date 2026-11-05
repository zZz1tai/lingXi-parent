import { Marked } from 'marked'
import { escapeHtml, safeMarkdownRenderer } from '../../../../utils/markdownSafety.js'

// OpenUI 的 Markdown 严格渲染器：原始 HTML 一律转义为纯文本，
// 链接与图片只允许 https 与本地回环 http，防止脚本注入。

const renderer = {
  ...safeMarkdownRenderer,
  html({ text }) {
    return text ? escapeHtml(text) : ''
  }
}

const openUiMarked = new Marked({ gfm: true, breaks: true, renderer })

export function renderOpenUiMarkdown(content) {
  return content ? openUiMarked.parse(content) : ''
}
