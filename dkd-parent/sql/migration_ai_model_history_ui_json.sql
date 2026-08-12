-- ============================================================
-- 迁移：tb_model_history 增加 ui_json 列（OpenUI 渲染历史）
-- 用途：会话历史回放时恢复已完成渲染；仅助手成功消息携带，
--       形如 {"renders":[{render_id,schema_version,spec,fallback_markdown}]}
-- 执行：对存量数据库执行一次即可（ALTER TABLE 幂等，人工校验）。
-- ============================================================
ALTER TABLE tb_model_history
    ADD COLUMN ui_json TEXT NULL COMMENT 'OpenUI渲染历史（JSON：{"renders":[...]}，仅助手成功消息可能携带）'
        AFTER content;