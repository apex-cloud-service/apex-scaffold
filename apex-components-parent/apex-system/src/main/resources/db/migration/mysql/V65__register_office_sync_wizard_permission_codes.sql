-- ============================================================
-- V65: 登记办公组织同步向导权限码（MySQL）
-- ============================================================
-- 脚本用途：将 OfficeSyncWizardHandler 上 @RequirePermission 实际使用的
--          office:sync:read/execute 权限码登记到 sys_permission，并为超级
--          管理员角色（ADMIN，role_id=1）授权，避免 /api/v2/office/sync
--          接口因权限码未登记而恒返回 403。
-- 业务背景：O06a 办公组织同步向导（v2）提供预检、预览、任务创建/列表/详情
--          与检查点重试；读与执行按权限分权。
-- 前置依赖：V1-V64 已完成；sys_permission/sys_role_permission 表存在。
-- 影响范围：新增 2 个权限码与超管角色授权，不修改既有权限。
-- 幂等性：INSERT ... SELECT ... FROM DUAL WHERE NOT EXISTS（按 code 防重）+
--          授权 INSERT IGNORE，可重复执行。
-- MySQL 兼容性：使用 MySQL 8.0 语法，由 Flyway V65 顺序执行。
-- 数据风险：新增行不覆盖既有数据；权限 ID 使用 2609-2610 保留段
--          （2601-2608 已被 V63/V64 使用）。
-- 运维注意：脚本执行后，非超管角色如需使用组织同步向导，须在角色管理中手动授权；
--          读与执行分别授予，保持最小权限。
-- 迁移元信息：版本 V65；执行阶段为办公组织同步向导阶段。
-- Checksum 提示：修改已执行迁移的注释会改变 Flyway checksum；存量环境需评估后 repair。
-- ============================================================

-- ============================================================
-- 1. 办公组织同步向导（OfficeSyncWizardHandler）— 2 个权限码
-- ============================================================
INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2609, '组织同步查询', 'office:sync:read', '查询办公平台组织同步预检、预览、任务列表与任务详情', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'office:sync:read');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2610, '组织同步执行', 'office:sync:execute', '创建办公平台组织同步任务与重试失败任务', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'office:sync:execute');

-- ============================================================
-- 2. 为超级管理员角色（ADMIN，role_id=1）授权
-- ============================================================
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT 1, p.id
FROM sys_permission p
WHERE p.code IN (
    'office:sync:read',
    'office:sync:execute'
)
AND EXISTS (SELECT 1 FROM sys_role WHERE id = 1 AND code = 'ADMIN');

-- ============================================================
-- 执行结果验证建议：
-- 1. 查询新增权限码：
--    SELECT id, name, code FROM sys_permission WHERE id BETWEEN 2609 AND 2610 ORDER BY id;
-- 2. 验证超管授权：
--    SELECT p.code FROM sys_role_permission rp
--    JOIN sys_permission p ON p.id = rp.permission_id
--    WHERE rp.role_id = 1 AND p.id BETWEEN 2609 AND 2610;
-- ============================================================
