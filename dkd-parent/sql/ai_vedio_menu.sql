-- AI 视频创作模块菜单与权限。
-- 执行后请重新登录后台，使动态菜单和权限缓存刷新。
-- 该脚本仅向管理员角色（role_id = 1）授权；其他角色请在“角色管理”中按需分配。

SET @ai_vedio_parent_id := (
    SELECT menu_id FROM sys_menu WHERE parent_id = 0 AND path = 'aiVedio' LIMIT 1
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 'AI视频创作', 0, 15, 'aiVedio', NULL, '', 1, 0, 'M', '0', '0', '', 'video-play', 'admin', NOW(), 'AI视频创作目录'
WHERE @ai_vedio_parent_id IS NULL;

SET @ai_vedio_parent_id := (
    SELECT menu_id FROM sys_menu WHERE parent_id = 0 AND path = 'aiVedio' LIMIT 1
);

SET @ai_vedio_project_menu_id := (
    SELECT menu_id FROM sys_menu WHERE parent_id = @ai_vedio_parent_id AND path = 'project' LIMIT 1
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '小说视频项目', @ai_vedio_parent_id, 1, 'project', 'aiVedio/project/index', '', 1, 0, 'C', '0', '0', 'aivideo:project:list', 'video-play', 'admin', NOW(), 'AI视频项目工作台'
WHERE @ai_vedio_project_menu_id IS NULL;

SET @ai_vedio_project_menu_id := (
    SELECT menu_id FROM sys_menu WHERE parent_id = @ai_vedio_parent_id AND path = 'project' LIMIT 1
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT x.menu_name, @ai_vedio_project_menu_id, x.order_num, '', '', '', 1, 0, 'F', '0', '0', x.perms, '#', 'admin', NOW(), x.remark
FROM (
    SELECT '项目查询' AS menu_name, 1 AS order_num, 'aivideo:project:query' AS perms, '查看AI视频项目' AS remark
    UNION ALL SELECT '项目新增', 2, 'aivideo:project:add', '新建AI视频项目'
    UNION ALL SELECT '项目修改', 3, 'aivideo:project:edit', '编辑AI视频项目和章节'
    UNION ALL SELECT '项目删除', 4, 'aivideo:project:remove', '删除AI视频项目'
    UNION ALL SELECT '资产查询', 5, 'aivideo:asset:list', '查看AI视频资产'
    UNION ALL SELECT '资产详情', 6, 'aivideo:asset:query', '查看AI视频资产详情'
) x
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu existing_menu WHERE existing_menu.perms = x.perms
);

SET @ai_vedio_task_menu_id := (
    SELECT menu_id FROM sys_menu WHERE parent_id = @ai_vedio_parent_id AND path = 'task' LIMIT 1
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '生成队列', @ai_vedio_parent_id, 2, 'task', 'aiVedio/task/index', '', 1, 0, 'C', '0', '0', 'aivideo:task:list', 'list', 'admin', NOW(), 'AI视频生成任务队列'
WHERE @ai_vedio_task_menu_id IS NULL;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu_id
FROM sys_menu
WHERE menu_id = @ai_vedio_parent_id
   OR parent_id = @ai_vedio_parent_id
   OR parent_id = @ai_vedio_project_menu_id
   OR parent_id = @ai_vedio_task_menu_id
ON DUPLICATE KEY UPDATE menu_id = VALUES(menu_id);
