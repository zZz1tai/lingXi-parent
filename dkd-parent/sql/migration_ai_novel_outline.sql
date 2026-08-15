-- ============================================================
-- 迁移：AI 小说三层大纲表（全书 → 卷 → 章）
-- 用途：替代自由文本 outline 设定卡，支持层级存储、排序、
--       章节关联与断链修复。
-- 执行：在现有数据库执行一次即可。
-- ============================================================
CREATE TABLE IF NOT EXISTS ai_novel_outline (
  outline_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '大纲ID',
  work_id BIGINT NOT NULL COMMENT '作品ID',
  outline_level VARCHAR(16) NOT NULL COMMENT '层级:BOOK-全书, VOLUME-卷, CHAPTER-章',
  parent_id BIGINT NOT NULL DEFAULT 0 COMMENT '父级大纲ID，BOOK 层为 0',
  seq_no INT NOT NULL DEFAULT 0 COMMENT '同级排序序号',
  outline_title VARCHAR(128) NOT NULL COMMENT '大纲标题',
  outline_content TEXT DEFAULT NULL COMMENT '概述/梗概内容',
  chapter_id BIGINT DEFAULT NULL COMMENT '关联章节ID（章级大纲）',
  chapter_no INT DEFAULT NULL COMMENT '计划章节号（含尚未创建正文的未来章节）',
  create_by VARCHAR(64) DEFAULT '',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64) DEFAULT '',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志:0存在 2删除',
  remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (outline_id),
  KEY idx_novel_outline_work (work_id, outline_level),
  KEY idx_novel_outline_parent (parent_id),
  KEY idx_novel_outline_chapter (chapter_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI小说三层大纲（全书-卷-章）';
