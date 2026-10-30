-- ============================================================
-- V75: 登记 Keycloak 管理细粒度权限码并授予超管（MySQL）
-- ============================================================
-- 背景：KeycloakAdminHandler 三个管理端点此前仅依赖 /admin-api/v1/** 的
--       admin 角色门槛，没有操作级权限。本脚本登记并授权对应权限码。
-- 前置依赖：V1-V74 完成；sys_permission/sys_role_permission/sys_role 存在。
-- 幂等性：权限按 code 防重；授权 INSERT IGNORE。
-- 数据风险：新增权限 ID 使用 2628-2630 保留段（2627 已被 V73 使用）。
-- ============================================================

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2628, 'Keycloak 状态查询', 'system:keycloak:status', '探测 Keycloak realm 连通状态', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'system:keycloak:status');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2629, 'Keycloak 全量同步', 'system:keycloak:sync', '从 Keycloak 全量同步用户到本地', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'system:keycloak:sync');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2630, 'Keycloak 单用户同步', 'system:keycloak:user:sync', '按 sub 同步单个 Keycloak 用户', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'system:keycloak:user:sync');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT 1, p.id
FROM sys_permission p
WHERE p.code IN ('system:keycloak:status', 'system:keycloak:sync', 'system:keycloak:user:sync')
AND EXISTS (SELECT 1 FROM sys_role WHERE id = 1 AND code = 'ADMIN');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code = 'SUPER_ADMIN'
  AND p.code IN ('system:keycloak:status', 'system:keycloak:sync', 'system:keycloak:user:sync');

-- 验证：
-- SELECT id, name, code FROM sys_permission WHERE id BETWEEN 2628 AND 2630 ORDER BY id;
