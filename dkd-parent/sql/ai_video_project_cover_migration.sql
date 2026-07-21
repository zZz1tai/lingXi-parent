-- 为已有 AI 视频项目表增加封面图片地址。
-- 新安装环境无需执行本文件，ai_video_workflow.sql 已包含该字段。

ALTER TABLE ai_video_project
    ADD COLUMN cover_url VARCHAR(1024) NULL COMMENT '项目封面图片URL' AFTER owner_user_id;
