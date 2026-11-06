-- 迁移：无损扩大聊天正文与回答展示快照容量，容纳合法长文本、知识证据与 OpenUI 渲染。
-- 适用于已有 ui_json 列的环境；缺列时先执行 migration_ai_model_history_ui_json.sql。
-- 对现有数据库仅执行本独立迁移，不要重新导入 lingxi_all.sql。
-- MEDIUMTEXT 在 utf8mb4 下仍按字节限制，应用侧同步限制序列化快照为 16,777,215 字节。
-- 正文保留 NOT NULL；容量覆盖当前用户输入 32,000 字符与助手输出 200,000 字符上限。
ALTER TABLE tb_model_history
    MODIFY COLUMN ui_json MEDIUMTEXT NULL DEFAULT NULL
        COMMENT '回答展示快照（JSON：{"renders":[...],"citations":[...]}，仅助手成功消息携带）',
    MODIFY COLUMN content MEDIUMTEXT NOT NULL COMMENT '消息内容';
