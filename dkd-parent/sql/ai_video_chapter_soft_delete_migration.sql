-- 修复 ai_video_chapter 软删除与唯一索引冲突。
-- 唯一键为 (project_id, chapter_no, del_flag)：正常记录使用 '0'，
-- 被删除记录使用 NULL。MySQL 允许唯一键中存在多个 NULL，因而可保留多次删除历史。

ALTER TABLE ai_video_chapter
    MODIFY COLUMN del_flag CHAR(1) NULL DEFAULT '0' COMMENT '删除标志：0为正常，NULL为已删除';

UPDATE ai_video_chapter
SET del_flag = NULL
WHERE del_flag = '2';
