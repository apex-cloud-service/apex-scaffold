-- 添加办公平台消息发送菜单和权限
-- 版本：V33
-- 功能：为消息发送模块添加菜单和权限配置

-- 1. 添加消息发送菜单
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES 
    ('消息发送', (SELECT id FROM (SELECT id FROM sys_menu WHERE menu_name = '办公平台' LIMIT 1) AS tmp), 2, 'message', 'office/message/index', 1, 0, 'C', '0', '0', 'office:message:list', 'message', 'admin', NOW(), 'admin', NOW(), '办公平台消息发送菜单');

-- 2. 获取刚插入的菜单ID
SET @message_menu_id = LAST_INSERT_ID();

-- 3. 添加消息发送相关按钮权限
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES 
    ('发送消息', @message_menu_id, 1, '', '', 1, 0, 'F', '0', '0', 'office:message:send', '#', 'admin', NOW(), 'admin', NOW(), '发送消息按钮'),
    ('查询消息状态', @message_menu_id, 2, '', '', 1, 0, 'F', '0', '0', 'office:message:query', '#', 'admin', NOW(), 'admin', NOW(), '查询消息状态按钮'),
    ('重新发送', @message_menu_id, 3, '', '', 1, 0, 'F', '0', '0', 'office:message:resend', '#', 'admin', NOW(), 'admin', NOW(), '重新发送消息按钮');

-- 4. 为管理员角色分配消息发送权限
-- 获取管理员角色ID
SET @admin_role_id = (SELECT role_id FROM sys_role WHERE role_key = 'admin' LIMIT 1);

-- 分配菜单权限
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT @admin_role_id, id FROM sys_menu WHERE perms IN (
    'office:message:list',
    'office:message:send',
    'office:message:query',
    'office:message:resend'
) AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu WHERE role_id = @admin_role_id AND menu_id = sys_menu.id
);

-- 5. 为超级管理员角色分配消息发送权限（如果存在）
SET @super_admin_role_id = (SELECT role_id FROM sys_role WHERE role_key = 'super_admin' LIMIT 1);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT @super_admin_role_id, id FROM sys_menu WHERE perms IN (
    'office:message:list',
    'office:message:send',
    'office:message:query',
    'office:message:resend'
) AND @super_admin_role_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu WHERE role_id = @super_admin_role_id AND menu_id = sys_menu.id
);
