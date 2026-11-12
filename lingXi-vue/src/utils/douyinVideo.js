/**
 * 抖音解析工具的纯逻辑工具函数。
 */

/**
 * 将毫秒时长格式化为播放器风格的时间文本。
 *
 * @param {number|string|null|undefined} durationMs 时长（毫秒）
 * @returns {string} 形如 `00:21`、`1:01:01` 的文本；无效输入按 0 处理
 */
export function formatDurationMs(durationMs) {
  const totalSeconds = Math.max(0, Math.floor((Number(durationMs) || 0) / 1000))
  const hours = Math.floor(totalSeconds / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60
  const mm = String(minutes).padStart(2, '0')
  const ss = String(seconds).padStart(2, '0')
  return hours > 0 ? `${hours}:${mm}:${ss}` : `${mm}:${ss}`
}

function trimZero(text) {
  return text.replace(/\.0$/, '')
}

/**
 * 将点赞/播放等计数格式化为中文单位文本。
 *
 * @param {number|string|null|undefined} value 计数
 * @returns {string} 形如 `999`、`1.2万`、`3.4亿` 的文本
 */
export function formatCount(value) {
  const n = Number(value) || 0
  if (n >= 100000000) return trimZero((n / 100000000).toFixed(1)) + '亿'
  if (n >= 10000) return trimZero((n / 10000).toFixed(1)) + '万'
  return String(Math.max(0, Math.floor(n)))
}

/**
 * 将秒级时间戳格式化为相对时间文本。
 *
 * @param {number|string|null|undefined} secondsTs 秒级时间戳
 * @param {number} nowMs 当前时刻（毫秒），便于测试注入
 * @returns {string} 形如 `刚刚`、`5分钟前`、`3小时前`、`7天前`、`2026-11-09` 的文本
 */
export function formatRelativeTime(secondsTs, nowMs = Date.now()) {
  const t = (Number(secondsTs) || 0) * 1000
  if (!t) return ''
  const diff = nowMs - t
  if (diff < 0) return '刚刚'
  if (diff < 60 * 1000) return '刚刚'
  if (diff < 60 * 60 * 1000) return Math.floor(diff / (60 * 1000)) + '分钟前'
  if (diff < 24 * 60 * 60 * 1000) return Math.floor(diff / (60 * 60 * 1000)) + '小时前'
  if (diff < 30 * 24 * 60 * 60 * 1000) return Math.floor(diff / (24 * 60 * 60 * 1000)) + '天前'
  const d = new Date(t)
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${mm}-${dd}`
}

/**
 * 收集当前时刻应该发射的弹幕（列表需按 offsetMs 升序）。
 *
 * @param {{offsetMs?: number}[]} list 弹幕列表
 * @param {number} pointer 已发射到的位置（下标）
 * @param {number} currentMs 当前播放时刻（毫秒）
 * @returns {{due: object[], nextPointer: number}} 本次应发射的弹幕与新的下标位置
 */
export function collectDueDanmaku(list, pointer, currentMs) {
  const safeList = Array.isArray(list) ? list : []
  let p = Math.min(Math.max(Number(pointer) || 0, 0), safeList.length)
  const due = []
  while (p < safeList.length && (Number(safeList[p].offsetMs) || 0) <= (Number(currentMs) || 0)) {
    due.push(safeList[p])
    p += 1
  }
  return { due, nextPointer: p }
}
