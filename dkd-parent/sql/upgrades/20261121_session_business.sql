-- 既有库单次升级。先备份并停写，核对 tb_chat_session 原唯一索引名为 session_id。
ALTER TABLE tb_chat_session
    ADD business_type VARCHAR(8) NOT NULL DEFAULT 'chat',
    ADD scope_owner VARCHAR(64) GENERATED ALWAYS AS (CASE WHEN business_type='novel' THEN user_id ELSE '' END) STORED,
    DROP INDEX session_id,
    ADD UNIQUE KEY uk_session_business (session_id, business_type, scope_owner);
ALTER TABLE tb_model_history ADD business_type VARCHAR(8) NOT NULL DEFAULT 'chat';

START TRANSACTION;
-- 旧创作入口对用户和助手消息均写入 novel-agent。混合会话按消息拆分。
UPDATE tb_model_history SET business_type = 'novel' WHERE model_name = 'novel-agent';
-- 只有小说消息或为空的旧工作台会话沿用原主键。保留普通聊天生成的会话行。
UPDATE tb_chat_session s SET s.business_type = 'novel'
WHERE (s.session_id REGEXP '^novel_[0-9]+_[0-9]+_[a-z0-9]*$'
       OR (s.session_id NOT REGEXP '^session_[0-9]+_[0-9]+$'
           AND EXISTS (SELECT 1 FROM tb_model_history h WHERE h.user_id=s.user_id
                       AND h.session_id=s.session_id AND h.business_type='novel')))
  AND NOT EXISTS (SELECT 1 FROM tb_model_history h WHERE h.session_id=s.session_id
                  AND h.user_id=s.user_id AND h.business_type='chat');
-- 混合会话保留普通聊天行，并为小说历史补建会话。历史消息 ID 和时间戳不变。
INSERT INTO tb_chat_session (session_id, business_type, user_id, session_name, created_at)
SELECT h.session_id, 'novel', h.user_id, COALESCE(MAX(s.session_name), '小说创作'), MIN(h.created_at)
FROM tb_model_history h
LEFT JOIN tb_chat_session s ON s.session_id=h.session_id AND s.user_id=h.user_id AND s.business_type='chat'
LEFT JOIN tb_chat_session n ON n.session_id=h.session_id AND n.user_id=h.user_id AND n.business_type='novel'
WHERE h.business_type='novel' AND n.id IS NULL
GROUP BY h.session_id, h.user_id;
COMMIT;
