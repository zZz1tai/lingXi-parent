-- ---------------------------------------------------------------------------
-- 对话消息生命周期：为 tb_model_history 增加处理状态、稳定错误码、请求标识和完成时间
-- 执行方式：在 lingXi-admin 连接的 MySQL 上执行本脚本（幂等，可重复执行）
-- 对应架构文档：阶段 1 对话生命周期闭环（7.2 消息状态）
-- ---------------------------------------------------------------------------

ALTER TABLE `tb_model_history`
  ADD COLUMN `status` VARCHAR(20) NOT NULL DEFAULT 'SUCCEEDED'
    COMMENT '消息处理状态：ACCEPTED/STREAMING/SUCCEEDED/FAILED/CANCELLED/REJECTED'
    AFTER `message_type`,
  ADD COLUMN `error_code` VARCHAR(64) DEFAULT NULL
    COMMENT '稳定错误码（与 Java/Agent 契约一致）'
    AFTER `status`,
  ADD COLUMN `request_id` VARCHAR(64) DEFAULT NULL
    COMMENT '请求标识（request_id，跨 Java/Agent 链路）'
    AFTER `error_code`,
  ADD COLUMN `completed_at` DATETIME DEFAULT NULL
    COMMENT '消息完成时间（SUCCEEDED/FAILED/CANCELLED 写入）'
    AFTER `updated_at`,
  ADD INDEX `idx_status` (`status`),
  ADD INDEX `idx_request_id` (`request_id`);

-- 存量数据兼容：历史消息默认视为已完成。
UPDATE `tb_model_history`
SET `status` = 'SUCCEEDED',
    `completed_at` = `updated_at`
WHERE `status` = 'SUCCEEDED' AND `completed_at` IS NULL;
