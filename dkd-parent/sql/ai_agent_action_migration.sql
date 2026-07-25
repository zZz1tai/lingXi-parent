-- 阶段 5：AI 受控写操作持久化与工单幂等迁移
-- 执行前请先备份数据库；本迁移默认不会启用写操作，仍需显式设置
-- AGENT_WRITE_ACTIONS_ENABLED=true 才会开放提案与执行链路。

CREATE TABLE IF NOT EXISTS `ai_agent_action` (
  `action_id` varchar(64) NOT NULL COMMENT '受控动作ID',
  `idempotency_key` varchar(128) NOT NULL COMMENT 'Agent 单次工具调用幂等键',
  `action_type` varchar(64) NOT NULL COMMENT '动作类型',
  `user_id` varchar(64) NOT NULL COMMENT '提案所属登录用户',
  `thread_id` varchar(128) NOT NULL COMMENT '提案所属聊天会话',
  `region_id` bigint DEFAULT NULL COMMENT '动作目标区域',
  `inner_code` varchar(64) NOT NULL COMMENT '目标设备编号',
  `action_desc` varchar(500) NOT NULL COMMENT '拟创建工单描述',
  `status` varchar(20) NOT NULL COMMENT 'PENDING/APPROVED/REJECTED/SUCCEEDED/FAILED/EXPIRED',
  `created_at` datetime NOT NULL,
  `expires_at` datetime NOT NULL,
  `decided_at` datetime DEFAULT NULL,
  `decided_by` bigint DEFAULT NULL COMMENT '批准或拒绝的登录用户ID',
  `executed_at` datetime DEFAULT NULL,
  `task_id` bigint DEFAULT NULL,
  `task_code` varchar(64) DEFAULT NULL,
  `last_error_code` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`action_id`),
  UNIQUE KEY `uk_ai_agent_action_idempotency` (`user_id`, `thread_id`, `idempotency_key`),
  KEY `idx_ai_agent_action_status_expiry` (`status`, `expires_at`),
  KEY `idx_ai_agent_action_task` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 人工确认受控动作';

ALTER TABLE `tb_task`
  ADD COLUMN `agent_action_id` varchar(64) DEFAULT NULL COMMENT 'AI 受控动作ID' AFTER `addr`,
  ADD UNIQUE KEY `uk_tb_task_agent_action_id` (`agent_action_id`);

