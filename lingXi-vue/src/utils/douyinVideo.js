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
