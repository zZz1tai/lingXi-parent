-- ============================================================
-- 迁移：持久化 AI 小说章纲的计划章节号
-- 用途：让尚未创建正文的未来章纲也能与普通续写上下文稳定对齐。
-- 执行：已存在 ai_novel_outline 表的数据库执行一次即可。
-- ============================================================
ALTER TABLE ai_novel_outline
  ADD COLUMN chapter_no INT DEFAULT NULL
  COMMENT '计划章节号（含尚未创建正文的未来章节）'
  AFTER chapter_id;

-- 为已经关联正文的旧章纲回填章节号；未关联正文的旧数据无法可靠推断，保留 NULL。
UPDATE ai_novel_outline o
JOIN ai_novel_chapter c
  ON c.chapter_id = o.chapter_id AND c.del_flag = '0'
SET o.chapter_no = c.chapter_no
WHERE o.outline_level = 'CHAPTER' AND o.chapter_no IS NULL;
