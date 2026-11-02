-- ============================================================
-- V82: 登记开放平台应用管理权限码（MySQL）
-- ============================================================
-- 脚本用途：将 OpenAppAdminHandler 上 @RequirePermission 实际使用的
--          open:app:read/open:app:manage 权限码登记到 sys_permission，
--          并为超级管理员角色（ADMIN，role_id=1）授权，避免开放应用管理
--          接口（/api/v1/open-apps）因权限码未登记而恒返回 403。
-- 业务背景：D2 开放平台——开放应用管理基于既有 OAUTH2_CLIENT 注册表，
--          管理第三方应用的回调、scope 清单、限流档、联系人与状态；
--          网关 /open-api/** 依应用 scope 清单做令牌断言（D2）与租户能力门禁（D4）。
-- 前置依赖：V1-V81 已完成；sys_permission/sys_role_permission 表存在。
-- 影响范围：新增 2 个权限码与超管角色授权，不修改既有权限。
-- 幂等性：INSERT ... SELECT ... FROM DUAL WHERE NOT EXISTS（按 code 防重）+
--          授权 INSERT IGNORE，可重复执行。
-- MySQL 兼容性：使用 MySQL 8.0 语法，由 Flyway V82 顺序执行。
-- 数据风险：新增行不覆盖既有数据；权限 ID 使用 2830-2831 保留段
--          （避开 V50 的 2501-2519、V63 的 2601-2604 与 V57 的 3300-3302）。
-- 运维注意：脚本执行后，非超管角色如需管理开放应用，须在角色管理中手动授权。
-- 迁移元信息：版本 V82；执行阶段为开放平台阶段。
-- Checksum 提示：修改已执行迁移的注释会改变 Flyway checksum；存量环境需评估后 repair。
-- ============================================================

-- ============================================================
-- 1. 开放应用管理（OpenAppAdminHandler）— 2 个权限码
-- ============================================================
INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2830, '开放应用查看', 'open:app:read', '查询开放平台应用注册信息与 scope 清单', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'open:app:read');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2831, '开放应用管理', 'open:app:manage', '创建、变更、停用开放平台应用并重置密钥', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'open:app:manage');

-- ============================================================
-- 2. 为超级管理员角色（ADMIN，role_id=1）授权
-- ============================================================
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT 1, p.id
FROM sys_permission p
WHERE p.code IN (
    'open:app:read',
    'open:app:manage'
)
AND EXISTS (SELECT 1 FROM sys_role WHERE id = 1 AND code = 'ADMIN');

-- ============================================================
-- 执行结果验证建议：
-- 1. 查询新增权限码：
--    SELECT id, name, code FROM sys_permission WHERE id BETWEEN 2830 AND 2831 ORDER BY id;
-- 2. 验证超管授权：
--    SELECT p.code FROM sys_role_permission rp
--    JOIN sys_permission p ON p.id = rp.permission_id
--    WHERE rp.role_id = 1 AND p.id BETWEEN 2830 AND 2831;
-- ============================================================
