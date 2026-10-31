-- B8: 租户管理员自助管理菜单种子（统一认证收口 / 方案批次 B8）
-- 语义与 MySQL 版本一致；菜单 ID 7200-7202。

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time, version, deleted)
VALUES (7200, 3000, '用户管理', 1, 'tenant-user', 'system/user/index', 'tenant:user:manage', 'User', 20, 1, 1, NOW(), NOW(), 0, 0)
ON CONFLICT (id) DO UPDATE SET menu_name = EXCLUDED.menu_name, parent_id = 3000, menu_type = 1,
    path = 'tenant-user', component = 'system/user/index', perms = 'tenant:user:manage',
    icon = 'User', sort = 20, visible = 1, status = 1, deleted = 0;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time, version, deleted)
VALUES (7201, 3000, '角色管理', 1, 'tenant-role', 'system/role/index', 'tenant:role:manage', 'Lock', 21, 1, 1, NOW(), NOW(), 0, 0)
ON CONFLICT (id) DO UPDATE SET menu_name = EXCLUDED.menu_name, parent_id = 3000, menu_type = 1,
    path = 'tenant-role', component = 'system/role/index', perms = 'tenant:role:manage',
    icon = 'Lock', sort = 21, visible = 1, status = 1, deleted = 0;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time, version, deleted)
VALUES (7202, 3000, '部门管理', 1, 'tenant-dept', 'system/dept/index', 'tenant:dept:manage', 'OfficeBuilding', 22, 1, 1, NOW(), NOW(), 0, 0)
ON CONFLICT (id) DO UPDATE SET menu_name = EXCLUDED.menu_name, parent_id = 3000, menu_type = 1,
    path = 'tenant-dept', component = 'system/dept/index', perms = 'tenant:dept:manage',
    icon = 'OfficeBuilding', sort = 22, visible = 1, status = 1, deleted = 0;

INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT 7920 + ROW_NUMBER() OVER (), r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.code = 'ADMIN' AND r.deleted = 0
  AND p.code IN ('tenant:user:manage','tenant:role:manage','tenant:dept:manage')
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- B3 兼容：V80 新增菜单后重算 platform-full 种子（保持"全量套餐"语义不漂移）
UPDATE sys_tenant_package
SET menu_ids = COALESCE((SELECT jsonb_agg(id) FROM sys_menu WHERE deleted = 0), '[]'::jsonb),
    permission_codes = COALESCE((SELECT jsonb_agg(code) FROM sys_permission WHERE deleted = 0), '[]'::jsonb),
    update_time = NOW()
WHERE package_code = 'platform-full' AND deleted = 0;
