-- ============================================================
-- V67: 登记运维体检报告权限码（MySQL）
-- ============================================================
-- 脚本用途：将 OperationsHealthHandler 上 @RequirePermission 实际使用的
--          system:operations:health:read 权限码登记到 sys_permission，
--          并为超级管理员角色（ADMIN，role_id=1）授权，避免
--          GET /api/v2/operations/health-report 因权限码未登记而恒返回 403。
-- 业务背景：O07 管理员体检报告提供即时、可解释的健康结论，按应用存活、
--          依赖就绪、业务风险、容量分组；平台体检与租户业务摘要分权，
--          聚合结果不绕过子项授权。
-- 前置依赖：V1-V66 已完成；sys_permission/sys_role_permission 表存在。
-- 影响范围：新增 1 个权限码与超管角色授权，不修改既有权限。
-- 幂等性：INSERT ... SELECT ... FROM DUAL WHERE NOT EXISTS（按 code 防重）+
--          授权 INSERT IGNORE，可重复执行。
-- MySQL 兼容性：使用 MySQL 8.0 语法，由 Flyway V67 顺序执行。
-- 数据风险：新增行不覆盖既有数据；权限 ID 使用 2614 保留段
--          （2601-2613 已被 V63/V64/V65/V66 使用）。
-- 运维注意：脚本执行后，非超管角色如需查看体检报告，须在角色管理中手动授权；
--          该权限暴露平台级实例证据，不建议授予租户侧角色。
-- 迁移元信息：版本 V67；执行阶段为管理员体检报告阶段。
-- Checksum 提示：修改已执行迁移的注释会改变 Flyway checksum；存量环境需评估后 repair。
-- ============================================================

-- ============================================================
-- 1. 运维体检报告（OperationsHealthHandler）— 1 个权限码
-- ============================================================
INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2614, '体检报告查询', 'system:operations:health:read', '生成管理员体检报告（应用存活/依赖就绪/业务风险/容量）', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'system:operations:health:read');

-- ============================================================
-- 2. 为超级管理员角色（ADMIN，role_id=1）授权
-- ============================================================
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT 1, p.id
FROM sys_permission p
WHERE p.code IN ('system:operations:health:read')
AND EXISTS (SELECT 1 FROM sys_role WHERE id = 1 AND code = 'ADMIN');

-- ============================================================
-- 执行结果验证建议：
-- 1. 查询新增权限码：
--    SELECT id, name, code FROM sys_permission WHERE id = 2614;
-- 2. 验证超管授权：
--    SELECT p.code FROM sys_role_permission rp
--    JOIN sys_permission p ON p.id = rp.permission_id
--    WHERE rp.role_id = 1 AND p.id = 2614;
-- ============================================================
