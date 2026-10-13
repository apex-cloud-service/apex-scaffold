-- 办公平台管理权限种子数据
-- 为系统管理员角色新增办公平台配置和绑定管理权限

-- 插入办公平台管理菜单
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, menu_type, visible, status, perms, icon, remark, create_time, update_time)
VALUES ('办公平台管理', 1, 8, 'office', NULL, 1, 'M', '0', '0', NULL, 'office-building', '办公平台配置和绑定管理', NOW(), NOW());

SET @office_menu_id = LAST_INSERT_ID();

-- 插入应用配置子菜单
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, menu_type, visible, status, perms, icon, remark, create_time, update_time)
VALUES ('应用配置', @office_menu_id, 1, 'config', 'office/config/index', 1, 'C', '0', '0', 'office:app:list', 'setting', '办公平台应用配置管理', NOW(), NOW());

SET @office_app_menu_id = LAST_INSERT_ID();

-- 插入身份绑定子菜单
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, menu_type, visible, status, perms, icon, remark, create_time, update_time)
VALUES ('身份绑定', @office_menu_id, 2, 'bindings', 'office/bindings/index', 1, 'C', '0', '0', 'office:binding:list', 'link', '办公平台身份绑定审批', NOW(), NOW());

SET @office_binding_menu_id = LAST_INSERT_ID();

-- 应用配置相关权限
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, menu_type, visible, status, perms, icon, remark, create_time, update_time)
VALUES ('查询应用配置', @office_app_menu_id, 1, '', NULL, 1, 'F', '0', '0', 'office:app:query', '#', '查询办公平台应用配置详情', NOW(), NOW());

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, menu_type, visible, status, perms, icon, remark, create_time, update_time)
VALUES ('创建应用配置', @office_app_menu_id, 2, '', NULL, 1, 'F', '0', '0', 'office:app:create', '#', '创建办公平台应用配置', NOW(), NOW());

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, menu_type, visible, status, perms, icon, remark, create_time, update_time)
VALUES ('更新应用配置', @office_app_menu_id, 3, '', NULL, 1, 'F', '0', '0', 'office:app:update', '#', '更新办公平台应用配置', NOW(), NOW());

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, menu_type, visible, status, perms, icon, remark, create_time, update_time)
VALUES ('删除应用配置', @office_app_menu_id, 4, '', NULL, 1, 'F', '0', '0', 'office:app:delete', '#', '删除办公平台应用配置', NOW(), NOW());

-- 身份绑定相关权限
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, menu_type, visible, status, perms, icon, remark, create_time, update_time)
VALUES ('审批身份绑定', @office_binding_menu_id, 1, '', NULL, 1, 'F', '0', '0', 'office:binding:review', '#', '审批办公平台身份绑定申请', NOW(), NOW());

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, menu_type, visible, status, perms, icon, remark, create_time, update_time)
VALUES ('解绑身份', @office_binding_menu_id, 2, '', NULL, 1, 'F', '0', '0', 'office:binding:unbind', '#', '解绑办公平台身份', NOW(), NOW());

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
