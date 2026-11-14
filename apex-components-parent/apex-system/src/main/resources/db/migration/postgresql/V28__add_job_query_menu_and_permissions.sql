-- 定时任务查询使用独立 ID，保留 V23 数据库配置菜单和权限。
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
VALUES (28013, 1000, '定时任务查询', 1, 'job-query', 'system/job/query', 'system:job:page', 'Search', 8, 1, 1);

INSERT INTO sys_permission (id, name, code, description, type, status)
VALUES (28042, '定时任务分页查询', 'system:job:page', '分页查询定时任务列表，支持按名称、分组、状态筛选', 1, 1);

INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT 2802042, 1, 28042
WHERE EXISTS (SELECT 1 FROM sys_role WHERE id = 1 AND code = 'ADMIN');
