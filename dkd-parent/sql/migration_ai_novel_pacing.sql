-- ============================================================
-- 迁移：AI 小说作品表增加节奏档位列（节奏控制系统）
-- 用途：作品级 pacing_level 档位（relaxed-舒缓 / steady-平稳 /
--       balanced-均衡 / intense-紧凑 / rapid-激烈），随作品上下文
--       注入创作智能体，作为续写与精修的节奏约束；节奏分析链
--       按该档位评估章节并与精修模板联动。
-- 执行：在现有数据库执行一次即可。
-- ============================================================
ALTER TABLE ai_novel_work
  ADD COLUMN pacing_level VARCHAR(16) NOT NULL DEFAULT 'balanced'
  COMMENT '节奏档位:relaxed-舒缓, steady-平稳, balanced-均衡, intense-紧凑, rapid-激烈'
  AFTER status;
