/**
 * 刷新章节列表后优先恢复指定章节；新建章节可按章节序号定位其数据库记录。
 */
export function pickNovelChapter(chapters, { chapterId = null, chapterNo = null } = {}) {
  if (!Array.isArray(chapters) || !chapters.length) return null
  if (chapterId != null) {
    const byId = chapters.find(chapter => String(chapter.chapterId) === String(chapterId))
    if (byId) return byId
  }
  if (chapterNo != null) {
    const byNo = chapters.find(chapter => Number(chapter.chapterNo) === Number(chapterNo))
    if (byNo) return byNo
  }
  return chapters[0]
}

/** 返回不会与现有章节序号冲突的下一个章节号。 */
export function nextNovelChapterNo(chapters) {
  return (Array.isArray(chapters) ? chapters : []).reduce(
    (max, chapter) => Math.max(max, Number(chapter?.chapterNo) || 0),
    0
  ) + 1
}

/**
 * 规划“续写下一章”：末章已有正文时新建一章；末章为空时复用它，便于故障后重试。
 */
export function planNovelContinuation(chapters) {
  if (!Array.isArray(chapters) || !chapters.length) return null
  const lastChapter = chapters[chapters.length - 1]
  if (String(lastChapter?.content || '').trim()) {
    return {
      createNew: true,
      chapterNo: nextNovelChapterNo(chapters),
      sourceChapter: lastChapter,
      targetChapter: null
    }
  }
  const sourceChapter = chapters
    .slice(0, -1)
    .reverse()
    .find(chapter => String(chapter?.content || '').trim())
  if (!sourceChapter) return null
  return {
    createNew: false,
    chapterNo: Number(lastChapter.chapterNo) || chapters.length,
    sourceChapter,
    targetChapter: lastChapter
  }
}
