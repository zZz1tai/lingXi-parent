// OpenUI 渲染层共用的小工具：媒体地址校验、文本截断。

export function isSafeMediaUrl(url) {
  if (typeof url !== 'string' || !url) return false
  if (url.length > 2048) return false
  if (url.startsWith('https://')) return true
  return (
    url.startsWith('http://localhost')
    || url.startsWith('http://127.0.0.1')
    || url.startsWith('http://[::1]')
  )
}

export function clampText(text, max = 4096) {
  const value = String(text ?? '')
  return value.length > max ? value.slice(0, max) : value
}

export const NOTICE_TONES = ['info', 'success', 'warning', 'error']

export function noticeToneClass(tone) {
  return NOTICE_TONES.includes(tone) ? tone : 'info'
}
