-- ============================================================
-- V64: 登记流程定义生命周期权限码（MySQL）
-- ============================================================
-- 脚本用途：将 WorkflowDefinitionHandler 上 @RequirePermission 实际使用的
--          workflow:definition:read/edit/publish/lifecycle 权限码登记到
--          sys_permission，并为超级管理员角色（ADMIN，role_id=1）授权，
--          避免 /api/v2/workflow-definitions 接口因权限码未登记而恒返回 403。
-- 业务背景：O03a 流程定义生命周期 API 提供草稿保存、结构校验、发布、版本
--          列表与启停，读、编辑、发布、启停按权限分权。
-- 前置依赖：V1-V63 已完成；sys_permission/sys_role_permission 表存在。
-- 影响范围：新增 4 个权限码与超管角色授权，不修改既有权限。
-- 幂等性：INSERT ... SELECT ... FROM DUAL WHERE NOT EXISTS（按 code 防重）+
--          授权 INSERT IGNORE，可重复执行。
-- MySQL 兼容性：使用 MySQL 8.0 语法，由 Flyway V64 顺序执行。
-- 数据风险：新增行不覆盖既有数据；权限 ID 使用 2605-2608 保留段
--          （2601-2604 已被 V63 使用）。
-- 运维注意：脚本执行后，非超管角色如需维护流程定义，须在角色管理中手动授权；
--          发布与启停应单独授予，保持职责分离。
-- 迁移元信息：版本 V64；执行阶段为流程定义生命周期阶段。
-- Checksum 提示：修改已执行迁移的注释会改变 Flyway checksum；存量环境需评估后 repair。
-- ============================================================

-- ============================================================
-- 1. 流程定义生命周期（WorkflowDefinitionHandler）— 4 个权限码
-- ============================================================
INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2605, '流程定义查询', 'workflow:definition:read', '查询流程定义列表、详情与版本历史', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'workflow:definition:read');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2606, '流程定义编辑', 'workflow:definition:edit', '创建与更新流程定义草稿、执行结构校验', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'workflow:definition:edit');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2607, '流程定义发布', 'workflow:definition:publish', '发布流程定义草稿为不可变版本', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'workflow:definition:publish');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2608, '流程定义启停', 'workflow:definition:lifecycle', '启用或停用流程定义，控制新发起', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'workflow:definition:lifecycle');

-- ============================================================
-- 2. 为超级管理员角色（ADMIN，role_id=1）授权
-- ============================================================
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT 1, p.id
FROM sys_permission p
WHERE p.code IN (
    'workflow:definition:read',
    'workflow:definition:edit',
    'workflow:definition:publish',
    'workflow:definition:lifecycle'
)
AND EXISTS (SELECT 1 FROM sys_role WHERE id = 1 AND code = 'ADMIN');

-- ============================================================
-- 执行结果验证建议：
-- 1. 查询新增权限码：
--    SELECT id, name, code FROM sys_permission WHERE id BETWEEN 2605 AND 2608 ORDER BY id;
-- 2. 验证超管授权：
--    SELECT p.code FROM sys_role_permission rp
--    JOIN sys_permission p ON p.id = rp.permission_id
--    WHERE rp.role_id = 1 AND p.id BETWEEN 2605 AND 2608;
-- ============================================================
