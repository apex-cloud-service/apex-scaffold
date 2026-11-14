-- ============================================================
-- V85: 登记 IoT 规则查询与命令下发权限码（MySQL）
-- ============================================================
-- 脚本用途：为 E3 管理面补强的两个端点登记权限码：
--          - GET  /integration/v1/iot/rules              -> iot:rule:read
--          - POST /integration/v1/iot/devices/{id}/commands -> iot:command:send
--          并授予超级管理员角色（ADMIN，role_id=1）。
-- 业务背景：方案 E3「设备管理（列表/详情/影子/遥测）、规则管理、命令下发与历史」
--          要求管理端可列规则、可下发命令；V83 仅登记了 iot:rule:manage，
--          规则列表与命令下发缺少独立权限码。
-- 菜单说明：前端页面由后端菜单种子驱动，菜单项随 V86 登记；本版本只补权限码。
-- 前置依赖：V1-V84 已完成；sys_permission / sys_role_permission 表存在。
-- 影响范围：新增 2 个权限码与超管角色授权，不修改既有权限。
-- 幂等性：INSERT ... SELECT ... FROM DUAL WHERE NOT EXISTS（按 code 防重）+
--          INSERT IGNORE 授权，可重复执行。
-- MySQL 兼容性：使用 MySQL 8.0 语法，由 Flyway V85 顺序执行。
-- 数据风险：新增行不覆盖既有数据；权限 ID 使用 2842-2843（2840-2841 已被 V84 使用）。
-- 运维注意：非超管角色如需使用规则列表或命令下发，须在角色管理中手动授权。
-- 迁移元信息：版本 V85；执行阶段为 E3 管理面补强。
-- Checksum 提示：修改已执行迁移的注释会改变 Flyway checksum；存量环境需评估后 repair。
-- ============================================================

-- ============================================================
-- 1. IoT 规则查询与命令下发 — 2 个权限码
-- ============================================================
INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2842, '规则查询', 'iot:rule:read', '分页查询物联网规则列表与详情', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'iot:rule:read');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2843, '命令下发', 'iot:command:send', '向指定设备下发下行命令并查询命令历史', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'iot:command:send');

-- ============================================================
-- 2. 为超级管理员角色（ADMIN，role_id=1）授权
-- ============================================================
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT 1, p.id
FROM sys_permission p
WHERE p.code IN ('iot:rule:read', 'iot:command:send')
  AND EXISTS (SELECT 1 FROM sys_role WHERE id = 1 AND code = 'ADMIN');

-- ============================================================
-- 执行结果验证建议：
-- 1. 查询新增权限码：
--    SELECT id, name, code FROM sys_permission WHERE id IN (2842, 2843);
-- 2. 验证超管授权：
--    SELECT p.code FROM sys_role_permission rp
--    JOIN sys_permission p ON p.id = rp.permission_id
--    WHERE rp.role_id = 1 AND p.code IN ('iot:rule:read','iot:command:send');
-- ============================================================
