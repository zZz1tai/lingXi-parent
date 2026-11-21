-- 已有库按需升级：先备份并核对 tb_model_history 已有 ui_json、content 列且仍使用 TEXT。
ALTER TABLE tb_model_history
    MODIFY COLUMN ui_json MEDIUMTEXT NULL DEFAULT NULL
        COMMENT '回答展示快照（JSON：{"renders":[...],"citations":[...]}，仅助手成功消息携带）',
    MODIFY COLUMN content MEDIUMTEXT NOT NULL COMMENT '消息内容';
