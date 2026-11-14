-- B8: 租户管理员自助管理菜单种子（统一认证收口 / 方案批次 B8）
-- 权限码已在 V79 交付（tenant:user/role/dept:manage，ID 2705-2707）。
-- 本迁移仅补租户管理员菜单项，并授予内置 tenant_admin 角色（租户开通时由 B4 建立，
-- 编码 tenant_admin，租户内唯一）。
-- 菜单 ID 使用 7200-7202 空闲段；幂等：ON DUPLICATE KEY UPDATE + INSERT IGNORE。

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time, version, deleted)
VALUES (7200, 3000, '用户管理', 1, 'tenant-user', 'system/user/index', 'tenant:user:manage', 'User', 20, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0)
ON DUPLICATE KEY UPDATE menu_name = '用户管理', parent_id = 3000, menu_type = 1, path = 'tenant-user',
                        component = 'system/user/index', perms = 'tenant:user:manage', icon = 'User',
                        sort = 20, visible = 1, status = 1, deleted = 0;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time, version, deleted)
VALUES (7201, 3000, '角色管理', 1, 'tenant-role', 'system/role/index', 'tenant:role:manage', 'Lock', 21, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0)
ON DUPLICATE KEY UPDATE menu_name = '角色管理', parent_id = 3000, menu_type = 1, path = 'tenant-role',
                        component = 'system/role/index', perms = 'tenant:role:manage', icon = 'Lock',
                        sort = 21, visible = 1, status = 1, deleted = 0;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time, version, deleted)
VALUES (7202, 3000, '部门管理', 1, 'tenant-dept', 'system/dept/index', 'tenant:dept:manage', 'OfficeBuilding', 22, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0)
ON DUPLICATE KEY UPDATE menu_name = '部门管理', parent_id = 3000, menu_type = 1, path = 'tenant-dept',
                        component = 'system/dept/index', perms = 'tenant:dept:manage', icon = 'OfficeBuilding',
                        sort = 22, visible = 1, status = 1, deleted = 0;

-- 授予平台 ADMIN 角色（供平台超管演示；租户内 tenant_admin 角色由 B4 开通时按套餐授权）
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.code = 'ADMIN' AND r.deleted = 0
  AND p.code IN ('tenant:user:manage','tenant:role:manage','tenant:dept:manage');

-- B3 兼容：V80 新增菜单后重算 platform-full 种子（保持"全量套餐"语义不漂移）
UPDATE `sys_tenant_package`
SET `menu_ids` = (SELECT COALESCE(CAST(JSON_ARRAYAGG(`id`) AS JSON), JSON_ARRAY()) FROM `sys_menu` WHERE `deleted` = 0),
    `permission_codes` = (SELECT COALESCE(CAST(JSON_ARRAYAGG(`code`) AS JSON), JSON_ARRAY()) FROM `sys_permission` WHERE `deleted` = 0),
    `update_time` = NOW()
WHERE `package_code` = 'platform-full' AND `deleted` = 0;
