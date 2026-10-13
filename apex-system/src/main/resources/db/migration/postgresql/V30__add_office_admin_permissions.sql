-- 办公平台管理权限种子数据（PostgreSQL 版本）
-- 为系统管理员角色新增办公平台配置和绑定管理权限

-- 插入办公平台管理菜单
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, menu_type, visible, status, perms, icon, remark, create_time, update_time)
VALUES ('办公平台管理', 1, 8, 'office', NULL, 1, 'M', '0', '0', NULL, 'office-building', '办公平台配置和绑定管理', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
RETURNING id INTO @office_menu_id;

-- PostgreSQL 需要使用临时变量或 WITH 子句
WITH office_menu AS (
    INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, menu_type, visible, status, perms, icon, remark, create_time, update_time)
    VALUES ('办公平台管理', 1, 8, 'office', NULL, 1, 'M', '0', '0', NULL, 'office-building', '办公平台配置和绑定管理', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    RETURNING id
),
app_menu AS (
    INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, menu_type, visible, status, perms, icon, remark, create_time, update_time)
    SELECT '应用配置', id, 1, 'config', 'office/config/index', 1, 'C', '0', '0', 'office:app:list', 'setting', '办公平台应用配置管理', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    FROM office_menu
    RETURNING id
),
binding_menu AS (
    INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, menu_type, visible, status, perms, icon, remark, create_time, update_time)
    SELECT '身份绑定', id, 2, 'bindings', 'office/bindings/index', 1, 'C', '0', '0', 'office:binding:list', 'link', '办公平台身份绑定审批', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    FROM office_menu
    RETURNING id
)
-- 应用配置相关权限
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, menu_type, visible, status, perms, icon, remark, create_time, update_time)
SELECT '查询应用配置', id, 1, '', NULL, 1, 'F', '0', '0', 'office:app:query', '#', '查询办公平台应用配置详情', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM app_menu
UNION ALL
SELECT '创建应用配置', id, 2, '', NULL, 1, 'F', '0', '0', 'office:app:create', '#', '创建办公平台应用配置', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM app_menu
UNION ALL
SELECT '更新应用配置', id, 3, '', NULL, 1, 'F', '0', '0', 'office:app:update', '#', '更新办公平台应用配置', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM app_menu
UNION ALL
SELECT '删除应用配置', id, 4, '', NULL, 1, 'F', '0', '0', 'office:app:delete', '#', '删除办公平台应用配置', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM app_menu
UNION ALL
SELECT '审批身份绑定', id, 1, '', NULL, 1, 'F', '0', '0', 'office:binding:review', '#', '审批办公平台身份绑定申请', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM binding_menu
UNION ALL
SELECT '解绑身份', id, 2, '', NULL, 1, 'F', '0', '0', 'office:binding:unbind', '#', '解绑办公平台身份', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM binding_menu;

-- 为超级管理员角色授予办公平台所有权限（假设超级管理员角色 ID 为 1）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE perms IN (
    'office:app:list',
    'office:app:query',
    'office:app:create',
    'office:app:update',
    'office:app:delete',
    'office:binding:list',
    'office:binding:review',
    'office:binding:unbind'
);
