-- 添加办公平台消息发送菜单和权限
-- 版本：V33
-- 功能：为消息发送模块添加菜单和权限配置

-- 1. 添加消息发送菜单
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES 
    ('消息发送', (SELECT id FROM sys_menu WHERE menu_name = '办公平台' LIMIT 1), 2, 'message', 'office/message/index', 1, 0, 'C', '0', '0', 'office:message:list', 'message', 'admin', NOW(), 'admin', NOW(), '办公平台消息发送菜单')
RETURNING id INTO message_menu_id;

-- 2. 添加消息发送相关按钮权限
DO $$
DECLARE
    message_menu_id BIGINT;
    admin_role_id BIGINT;
    super_admin_role_id BIGINT;
BEGIN
    -- 获取消息菜单ID
    SELECT id INTO message_menu_id FROM sys_menu WHERE perms = 'office:message:list' ORDER BY id DESC LIMIT 1;
    
    -- 添加按钮权限
    INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
    VALUES 
        ('发送消息', message_menu_id, 1, '', '', 1, 0, 'F', '0', '0', 'office:message:send', '#', 'admin', NOW(), 'admin', NOW(), '发送消息按钮'),
        ('查询消息状态', message_menu_id, 2, '', '', 1, 0, 'F', '0', '0', 'office:message:query', '#', 'admin', NOW(), 'admin', NOW(), '查询消息状态按钮'),
        ('重新发送', message_menu_id, 3, '', '', 1, 0, 'F', '0', '0', 'office:message:resend', '#', 'admin', NOW(), 'admin', NOW(), '重新发送消息按钮');
    
    -- 获取管理员角色ID
    SELECT role_id INTO admin_role_id FROM sys_role WHERE role_key = 'admin' LIMIT 1;
    
    -- 为管理员角色分配权限
    INSERT INTO sys_role_menu (role_id, menu_id)
    SELECT admin_role_id, id FROM sys_menu WHERE perms IN (
        'office:message:list',
        'office:message:send',
        'office:message:query',
        'office:message:resend'
    ) AND NOT EXISTS (
        SELECT 1 FROM sys_role_menu WHERE role_id = admin_role_id AND menu_id = sys_menu.id
    );
    
    -- 获取超级管理员角色ID（如果存在）
    SELECT role_id INTO super_admin_role_id FROM sys_role WHERE role_key = 'super_admin' LIMIT 1;
    
    -- 为超级管理员角色分配权限
    IF super_admin_role_id IS NOT NULL THEN
        INSERT INTO sys_role_menu (role_id, menu_id)
        SELECT super_admin_role_id, id FROM sys_menu WHERE perms IN (
            'office:message:list',
            'office:message:send',
            'office:message:query',
            'office:message:resend'
        ) AND NOT EXISTS (
            SELECT 1 FROM sys_role_menu WHERE role_id = super_admin_role_id AND menu_id = sys_menu.id
        );
    END IF;
END $$;
