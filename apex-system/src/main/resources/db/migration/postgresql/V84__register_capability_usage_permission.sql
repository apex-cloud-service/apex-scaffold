-- ============================================================
-- V84: 登记能力调用用量查询权限码（PostgreSQL）
-- ============================================================
-- 脚本用途：将 CapabilityUsageHandler 上 @RequirePermission 实际使用的
--          capability:usage:read 权限码登记到 sys_permission，
--          并为超级管理员角色（ADMIN，role_id=1）授权，避免能力用量查询
--          接口（/api/v2/capability-usage）因权限码未登记而恒返回 403。
-- 业务背景：D4 能力计量——基于既有操作审计日志（sys_operation_log，V14 起携带
--          租户列）按 租户 × 能力 输出接口调用次数统计；只读，不做计费。
-- 前置依赖：V1-V83 已完成；sys_permission/sys_role_permission 表存在。
-- 影响范围：新增 1 个权限码与超管角色授权，不修改既有权限。
-- 幂等性：INSERT ... SELECT ... WHERE NOT EXISTS（按 code 防重）+ 授权
--          ON CONFLICT DO NOTHING，可重复执行。
-- PostgreSQL/Flyway 兼容性：使用 PostgreSQL 9.5+ 语法，由 Flyway V84 顺序执行。
-- 数据风险：新增行不覆盖既有数据；权限 ID 使用 2840 保留位
--          （避开 V82 的 2830-2831、V50 的 2501-2519、V63 的 2601-2604 与 V57 的 3300-3302）。
-- 运维注意：脚本执行后，非超管角色如需查看能力用量，须在角色管理中手动授权。
-- 迁移元信息：版本 V84；执行阶段为统一认证租户平台整治阶段。
-- Checksum 提示：修改已执行迁移的注释会改变 Flyway checksum；存量环境需评估后 repair。
-- ============================================================

-- ============================================================
-- 1. 能力用量查询（CapabilityUsageHandler）— 1 个权限码
-- ============================================================
INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2840, '能力用量查询', 'capability:usage:read', '按租户与能力维度查询接口调用用量统计（只读）', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'capability:usage:read');

-- ============================================================
-- 2. 为超级管理员角色（ADMIN，role_id=1）授权
-- ============================================================
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT 500000 + p.id, 1, p.id
FROM sys_permission p
WHERE p.code IN (
    'capability:usage:read'
)
AND EXISTS (SELECT 1 FROM sys_role WHERE id = 1 AND code = 'ADMIN')
ON CONFLICT DO NOTHING;

-- ============================================================
-- 执行结果验证建议：
-- 1. 查询新增权限码：
--    SELECT id, name, code FROM sys_permission WHERE id = 2840;
-- 2. 验证超管授权：
--    SELECT p.code FROM sys_role_permission rp
--    JOIN sys_permission p ON p.id = rp.permission_id
--    WHERE rp.role_id = 1 AND p.id = 2840;
-- ============================================================
