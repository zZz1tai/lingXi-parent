-- ============================================================
-- 迁移：修复 AI 小说章节重复软删除与唯一索引冲突
-- 原因：唯一键为 (work_id, chapter_no, del_flag)，固定写入 '2' 会让
--       同一作品同一章号的第二条删除记录发生 Duplicate entry。
-- 执行：现有数据库执行一次；MySQL 唯一索引允许存在多条 NULL。
-- ============================================================
ALTER TABLE ai_novel_chapter
  MODIFY COLUMN del_flag CHAR(1) NULL DEFAULT '0'
  COMMENT '删除标志：0为正常，NULL为已删除';

UPDATE ai_novel_chapter
SET del_flag = NULL
WHERE del_flag = '2';
