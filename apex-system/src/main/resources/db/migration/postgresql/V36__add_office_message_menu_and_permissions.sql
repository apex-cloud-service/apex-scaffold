-- 复用 V33 消息菜单及查询权限，补充通用发送和重发权限。
UPDATE sys_menu SET perms = 'office:message:list' WHERE id = 2002;

INSERT INTO sys_menu (id, parent_id, menu_name, sort, path, component, menu_type, visible, status, perms, icon) VALUES
(36001, 2002, '发送消息', 7, '', '', 2, 0, 1, 'office:message:send', '#'),
(36002, 2002, '重新发送', 8, '', '', 2, 0, 1, 'office:message:resend', '#');

INSERT INTO sys_permission (id, name, code, type, status)
SELECT 360000 + id, menu_name, perms, 1, 1 FROM sys_menu WHERE id IN (2002, 36001, 36002);

INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT 3600000 + r.id * 100000 + p.id, r.id, p.id
FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code IN ('ADMIN', 'SUPER_ADMIN')
  AND p.code IN ('office:message:list', 'office:message:send', 'office:message:query', 'office:message:resend')
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
