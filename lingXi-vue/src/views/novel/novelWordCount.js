/**
 * 小说正文统一按非空白 UTF-16 字符数统计，与后端 NovelWordCounter 保持一致。
 */
export function countNovelCharacters(content) {
  return String(content || '').replace(/\s/gu, '').length
}
