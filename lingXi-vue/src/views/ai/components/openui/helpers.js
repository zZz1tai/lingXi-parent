// OpenUI 渲染层共用的小工具：媒体地址校验、文本截断。
import { isSafeExternalUrl } from '../../../../utils/markdownSafety.js'

export function isSafeMediaUrl(url) {
  return typeof url === 'string' && url.length <= 2048 && isSafeExternalUrl(url)
}

export function clampText(text, max = 4096) {
  const value = String(text ?? '')
  return value.length > max ? value.slice(0, max) : value
}

export const NOTICE_TONES = ['info', 'success', 'warning', 'error']

export function noticeToneClass(tone) {
  return NOTICE_TONES.includes(tone) ? tone : 'info'
}
