-- AI 小说创作模块菜单与权限。
-- 执行后请重新登录后台，使动态菜单和权限缓存刷新。
-- 该脚本仅向管理员角色（role_id = 1）授权；其他角色请在“角色管理”中按需分配。
-- 注意：后端 Java 接口（/novel/**）尚未实现，菜单可先行注册，页面会提示“后端未就绪”。

SET @novel_parent_id := (
    SELECT menu_id FROM sys_menu WHERE parent_id = 0 AND path = 'novel' LIMIT 1
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 'AI小说创作', 0, 16, 'novel', NULL, '', 1, 0, 'M', '0', '0', '', 'edit', 'admin', NOW(), 'AI小说创作目录'
WHERE @novel_parent_id IS NULL;

SET @novel_parent_id := (
    SELECT menu_id FROM sys_menu WHERE parent_id = 0 AND path = 'novel' LIMIT 1
);

SET @novel_writing_menu_id := (
    SELECT menu_id FROM sys_menu WHERE parent_id = @novel_parent_id AND path = 'writing' LIMIT 1
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '小说写作台', @novel_parent_id, 1, 'writing', 'novel/index', '', 1, 0, 'C', '0', '0', 'novel:writing:list', 'edit', 'admin', NOW(), 'AI小说创作工作台（短篇/长篇）'
WHERE @novel_writing_menu_id IS NULL;

SET @novel_writing_menu_id := (
    SELECT menu_id FROM sys_menu WHERE parent_id = @novel_parent_id AND path = 'writing' LIMIT 1
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT x.menu_name, @novel_writing_menu_id, x.order_num, '', '', '', 1, 0, 'F', '0', '0', x.perms, '#', 'admin', NOW(), x.remark
FROM (
    SELECT '作品查询' AS menu_name, 1 AS order_num, 'novel:work:list' AS perms, '查看作品列表' AS remark
    UNION ALL SELECT '作品新增', 2, 'novel:work:add', '新建作品'
    UNION ALL SELECT '作品修改', 3, 'novel:work:edit', '编辑作品信息与正文'
    UNION ALL SELECT '作品删除', 4, 'novel:work:remove', '删除作品'
) x
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu existing_menu WHERE existing_menu.perms = x.perms
);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu_id
FROM sys_menu
WHERE menu_id = @novel_parent_id
   OR parent_id = @novel_parent_id
   OR parent_id = @novel_writing_menu_id
ON DUPLICATE KEY UPDATE menu_id = VALUES(menu_id);
