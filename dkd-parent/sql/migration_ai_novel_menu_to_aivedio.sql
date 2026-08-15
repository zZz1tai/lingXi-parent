-- ============================================================
-- 迁移：将 AI 小说创作菜单树挂到 AI视频创作目录下
-- 用途：小说写作台入口并入「AI视频创作」目录，访问路径由
--       /novel/writing 变更为 /aiVedio/novel/writing。
-- 执行：在既有数据库执行一次即可；脚本幂等，可重复执行。
-- ============================================================

-- AI视频创作顶级目录（parent_id = 0 且 path = 'aiVedio'）
SET @ai_vedio_parent_id := (
    SELECT menu_id FROM sys_menu WHERE parent_id = 0 AND path = 'aiVedio' LIMIT 1
);

-- 1. 历史遗留的顶级 novel 目录挂到 AI视频目录下，排在 project/task 之后
SET @novel_parent_id := (
    SELECT menu_id FROM sys_menu WHERE parent_id = 0 AND path = 'novel' LIMIT 1
);
UPDATE sys_menu
SET parent_id = @ai_vedio_parent_id, order_num = 3
WHERE menu_id = @novel_parent_id
  AND @ai_vedio_parent_id IS NOT NULL
  AND @novel_parent_id IS NOT NULL;

-- 2. 重新定位小说目录（可能已被移动到 aiVedio 下）
SET @novel_parent_id := (
    SELECT menu_id FROM sys_menu WHERE parent_id = @ai_vedio_parent_id AND path = 'novel' LIMIT 1
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 'AI小说创作', @ai_vedio_parent_id, 3, 'novel', NULL, '', 1, 0, 'M', '0', '0', '', 'edit', 'admin', NOW(), 'AI小说创作目录（挂载于AI视频创作下）'
WHERE @ai_vedio_parent_id IS NOT NULL
  AND @novel_parent_id IS NULL;

SET @novel_parent_id := (
    SELECT menu_id FROM sys_menu WHERE parent_id = @ai_vedio_parent_id AND path = 'novel' LIMIT 1
);

-- 3. 找回或补建写作台叶子菜单
SET @novel_writing_menu_id := (
    SELECT menu_id FROM sys_menu
    WHERE parent_id = @novel_parent_id
      AND (path = 'writing' OR component = 'novel/index' OR perms = 'novel:writing:list')
    LIMIT 1
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '小说写作台', @novel_parent_id, 1, 'writing', 'novel/index', '', 1, 0, 'C', '0', '0', 'novel:writing:list', 'edit', 'admin', NOW(), 'AI小说创作工作台（短篇/长篇）'
WHERE @novel_parent_id IS NOT NULL
  AND @novel_writing_menu_id IS NULL;

SET @novel_writing_menu_id := (
    SELECT menu_id FROM sys_menu
    WHERE parent_id = @novel_parent_id
      AND (path = 'writing' OR component = 'novel/index' OR perms = 'novel:writing:list')
    LIMIT 1
);

-- 4. 保留非空叶子路径，确保后端生成稳定的路由名称和前端菜单链接
UPDATE sys_menu
SET path = 'writing',
    component = 'novel/index',
    perms = 'novel:writing:list',
    menu_type = 'C',
    visible = '0',
    status = '0'
WHERE menu_id = @novel_writing_menu_id;

-- 5. 迁移角色授权。非管理员若缺少新的 aiVedio 祖先目录，菜单树会被后端裁掉。
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, @ai_vedio_parent_id
FROM sys_role_menu rm
JOIN sys_menu m ON m.menu_id = rm.menu_id
WHERE @ai_vedio_parent_id IS NOT NULL
  AND (m.menu_id IN (@novel_parent_id, @novel_writing_menu_id) OR m.perms LIKE 'novel:%')
ON DUPLICATE KEY UPDATE menu_id = VALUES(menu_id);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, @novel_parent_id
FROM sys_role_menu rm
JOIN sys_menu m ON m.menu_id = rm.menu_id
WHERE @novel_parent_id IS NOT NULL
  AND (m.menu_id = @novel_writing_menu_id OR m.perms LIKE 'novel:%')
ON DUPLICATE KEY UPDATE menu_id = VALUES(menu_id);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, @novel_writing_menu_id
FROM sys_role_menu rm
JOIN sys_menu m ON m.menu_id = rm.menu_id
WHERE @novel_writing_menu_id IS NOT NULL
  AND m.perms LIKE 'novel:%'
ON DUPLICATE KEY UPDATE menu_id = VALUES(menu_id);

-- 管理员角色始终保留完整入口。
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu_id
FROM sys_menu
WHERE menu_id IN (@ai_vedio_parent_id, @novel_parent_id, @novel_writing_menu_id)
ON DUPLICATE KEY UPDATE menu_id = VALUES(menu_id);
