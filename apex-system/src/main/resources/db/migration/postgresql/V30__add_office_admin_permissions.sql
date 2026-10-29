-- 办公平台管理菜单及权限，沿用 system 的数值菜单类型和权限关联表。
INSERT INTO sys_menu (id, parent_id, menu_name, sort, path, component, menu_type, visible, status, perms, icon) VALUES
(30000, 1000, '办公平台管理', 8, 'office', NULL, 0, 1, 1, NULL, 'office-building'),
(30001, 30000, '应用配置', 1, 'config', 'office/config/index', 1, 1, 1, 'office:app:list', 'setting'),
(30002, 30000, '身份绑定', 2, 'bindings', 'office/bindings/index', 1, 1, 1, 'office:binding:list', 'link'),
(30003, 30001, '查询应用配置', 1, '', NULL, 2, 0, 1, 'office:app:query', '#'),
(30004, 30001, '创建应用配置', 2, '', NULL, 2, 0, 1, 'office:app:create', '#'),
(30005, 30001, '更新应用配置', 3, '', NULL, 2, 0, 1, 'office:app:update', '#'),
(30006, 30001, '删除应用配置', 4, '', NULL, 2, 0, 1, 'office:app:delete', '#'),
(30007, 30002, '审批身份绑定', 1, '', NULL, 2, 0, 1, 'office:binding:review', '#'),
(30008, 30002, '解绑身份', 2, '', NULL, 2, 0, 1, 'office:binding:unbind', '#');

INSERT INTO sys_permission (id, name, code, type, status)
SELECT 300000 + id, menu_name, perms, 1, 1 FROM sys_menu WHERE id BETWEEN 30001 AND 30008;

INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT 3000000 + m.id, r.id, 300000 + m.id
FROM sys_role r CROSS JOIN sys_menu m
WHERE r.code = 'SUPER_ADMIN' AND m.id BETWEEN 30001 AND 30008;
