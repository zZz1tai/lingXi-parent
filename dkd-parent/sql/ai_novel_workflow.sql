-- AI 小说创作模块（MySQL 8.0 / utf8mb4）
-- 作品为顶层容器；长篇小说按章节存储正文，短篇小说正文直接放在作品表。
-- 逻辑关联使用索引，未使用数据库外键。

CREATE TABLE IF NOT EXISTS ai_novel_work (
  work_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '作品ID',
  work_name VARCHAR(128) NOT NULL COMMENT '作品名称',
  work_type VARCHAR(16) NOT NULL DEFAULT 'novel' COMMENT '作品类型:short-短篇, novel-长篇小说',
  genre VARCHAR(64) DEFAULT '' COMMENT '题材类型，如东方玄幻',
  synopsis TEXT DEFAULT NULL COMMENT '作品梗概',
  manuscript LONGTEXT DEFAULT NULL COMMENT '短篇正文（长篇按章节存储）',
  status VARCHAR(16) NOT NULL DEFAULT 'draft' COMMENT '状态:draft-草稿, writing-写作中, finished-已完成',
  owner_user_id BIGINT NOT NULL COMMENT '所属用户ID',
  create_by VARCHAR(64) DEFAULT '' COMMENT '创建者',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64) DEFAULT '' COMMENT '更新者',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志:0存在 2删除',
  remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (work_id),
  KEY idx_novel_work_owner (owner_user_id),
  KEY idx_novel_work_update (update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI小说作品';

CREATE TABLE IF NOT EXISTS ai_novel_chapter (
  chapter_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '章节ID',
  work_id BIGINT NOT NULL COMMENT '作品ID',
  chapter_no INT NOT NULL DEFAULT 1 COMMENT '章节序号',
  chapter_title VARCHAR(128) DEFAULT NULL COMMENT '章节标题',
  chapter_brief TEXT DEFAULT NULL COMMENT '本章梗概',
  content LONGTEXT DEFAULT NULL COMMENT '章节正文',
  word_count INT NOT NULL DEFAULT 0 COMMENT '正文字数',
  status VARCHAR(16) NOT NULL DEFAULT 'draft' COMMENT '状态:draft-草稿, published-已发布',
  create_by VARCHAR(64) DEFAULT '',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64) DEFAULT '',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志:0存在 2删除',
  remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (chapter_id),
  UNIQUE KEY uk_novel_chapter_work_no (work_id, chapter_no, del_flag),
  KEY idx_novel_chapter_work (work_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI小说章节';

CREATE TABLE IF NOT EXISTS ai_novel_setting (
  setting_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '设定ID',
  work_id BIGINT NOT NULL COMMENT '作品ID',
  setting_type VARCHAR(32) NOT NULL COMMENT '设定类型:character-人物, world-世界观, outline-大纲, item-物品, organization-组织, event-事件, style-文风, other-其他',
  title VARCHAR(128) NOT NULL COMMENT '设定标题',
  content TEXT DEFAULT NULL COMMENT '设定内容',
  create_by VARCHAR(64) DEFAULT '',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64) DEFAULT '',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志:0存在 2删除',
  remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (setting_id),
  KEY idx_novel_setting_work (work_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI小说设定卡';
