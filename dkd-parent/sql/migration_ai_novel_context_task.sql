-- ============================================================
-- AI 小说资料同步持久化异步任务
-- 执行范围：既有数据库执行一次；新环境已包含在 lingxi_all.sql。
-- ============================================================

CREATE TABLE IF NOT EXISTS ai_novel_context_task (
  task_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  work_id BIGINT NOT NULL COMMENT '作品ID',
  chapter_id BIGINT NOT NULL COMMENT '章节ID',
  owner_user_id BIGINT NOT NULL COMMENT '任务发起用户ID',
  content_hash CHAR(64) NOT NULL COMMENT '提交时正文SHA-256',
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCEEDED/FAILED/OBSOLETE',
  attempt_count INT NOT NULL DEFAULT 0 COMMENT '领取执行次数',
  next_run_time DATETIME DEFAULT NULL COMMENT '下次可执行时间',
  worker_id VARCHAR(128) DEFAULT NULL COMMENT '当前执行者',
  lease_until DATETIME DEFAULT NULL COMMENT '执行租约截止时间',
  result_json LONGTEXT DEFAULT NULL COMMENT '摘要及待确认资料建议JSON',
  error_message VARCHAR(500) DEFAULT NULL COMMENT '最近错误',
  started_time DATETIME DEFAULT NULL COMMENT '首次开始时间',
  finished_time DATETIME DEFAULT NULL COMMENT '终态时间',
  create_by VARCHAR(64) DEFAULT '',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (task_id),
  UNIQUE KEY uk_novel_context_chapter_hash (chapter_id, content_hash),
  KEY idx_novel_context_runnable (status, next_run_time, lease_until),
  KEY idx_novel_context_work (work_id, task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI小说资料同步异步任务';
