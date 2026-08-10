-- ============================================================
-- 迁移：ai_video_generation_task 增加 recover_count 列
-- 用途：故事圣经任务恢复投递次数上限保护，防止任务在
--       QUEUED <-> RUNNING 之间无限循环消耗 LLM token。
-- 执行：在现有数据库执行一次即可（ALTER TABLE 幂等性由人工保证）。
-- ============================================================
ALTER TABLE ai_video_generation_task
    ADD COLUMN recover_count INT NOT NULL DEFAULT 0
    COMMENT '恢复重投递累计次数，达到上限后终止任务';

-- 存量异常数据兜底：执行中但租约缺失的任务直接失败，终止循环
UPDATE ai_video_generation_task
SET status = 'FAILED', progress = 100,
    error_code = 'STORY_BIBLE_LEASE_MISSING',
    error_message = '执行中任务缺少租约（数据异常），已终止防止重复投递',
    completed_time = NOW(), worker_id = NULL, lease_expire = NULL,
    update_time = NOW()
WHERE task_type = 'STORY_BIBLE' AND status = 'RUNNING' AND lease_expire IS NULL;
