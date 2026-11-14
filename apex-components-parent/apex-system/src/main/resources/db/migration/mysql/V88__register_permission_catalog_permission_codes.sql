-- ============================================================
-- V88: 登记权限目录管理权限码（MySQL）
-- ============================================================
-- 脚本用途：为权限目录管理端点登记权限码：
--          - GET    /api/v1/auth/permissions        -> system:permission:list
--          - POST   /api/v1/auth/permissions        -> system:permission:create
--          - POST   /api/v1/auth/permissions/update -> system:permission:update
--          - DELETE /api/v1/auth/permissions/{id}   -> system:permission:remove
--          并授予超级管理员角色（ADMIN，role_id=1）。
-- 业务背景：AuthorizationHandler 的角色管理端点已绑定 system:role:*（V8 种子登记），
--          但同类的权限目录 CRUD 端点长期无权限码可绑：system:permission:list 曾被
--          AuthConfigHandler 直接引用却从未登记，导致该注解对非超管恒为 403；
--          create/update/remove 三个写端点则完全无权限校验。
-- 设计说明：权限码与既有 system:role:*（ID 2038-2041）保持同一命名与 type 口径，
--          使"角色管理 + 权限管理"在角色授权界面成对出现。
-- 前置依赖：V1-V87 已完成；sys_permission / sys_role_permission 表存在。
-- 影响范围：新增 4 个权限码与超管角色授权，不修改既有权限。
-- 幂等性：INSERT ... SELECT ... FROM DUAL WHERE NOT EXISTS（按 code 防重）+
--          INSERT IGNORE 授权，可重复执行。
-- MySQL 兼容性：使用 MySQL 8.0 语法，由 Flyway V88 顺序执行。
-- 数据风险：新增行不覆盖既有数据；权限 ID 使用 2844-2847（2842-2843 已被 V85 使用）。
-- 运维注意：非超管角色如需维护权限目录，须在角色管理中手动授权。
-- 迁移元信息：版本 V88；执行阶段为功能性注解补全。
-- Checksum 提示：修改已执行迁移的注释会改变 Flyway checksum；存量环境需评估后 repair。
-- ============================================================

-- ============================================================
-- 1. 权限目录管理 — 4 个权限码
-- ============================================================
INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2844, '权限列表', 'system:permission:list', '查询系统权限目录树与全部权限定义', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'system:permission:list');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2845, '权限新增', 'system:permission:create', '创建系统权限定义', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'system:permission:create');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2846, '权限编辑', 'system:permission:update', '更新系统权限定义', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'system:permission:update');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2847, '权限删除', 'system:permission:remove', '删除系统权限定义', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'system:permission:remove');

-- ============================================================
-- 2. 为超级管理员角色（ADMIN，role_id=1）授权
-- ============================================================
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT 1, p.id
FROM sys_permission p
WHERE p.code IN (
        'system:permission:list',
        'system:permission:create',
        'system:permission:update',
        'system:permission:remove')
  AND EXISTS (SELECT 1 FROM sys_role WHERE id = 1 AND code = 'ADMIN');

-- ============================================================
-- 执行结果验证建议：
-- 1. 查询新增权限码：
--    SELECT id, name, code FROM sys_permission WHERE id BETWEEN 2844 AND 2847;
-- 2. 验证超管授权：
--    SELECT p.code FROM sys_role_permission rp
--    JOIN sys_permission p ON p.id = rp.permission_id
--    WHERE rp.role_id = 1 AND p.code LIKE 'system:permission:%';
-- ============================================================
