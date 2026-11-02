-- ============================================================
-- V83: 登记集成服务与物联网权限码（MySQL）
-- ============================================================
-- 脚本用途：将 IntegrationConnectorHandler / CredentialHandler 与 IoT 设备域
--          Handler 上 @RequirePermission 声明的 integration:connector:*、
--          integration:credential:*、iot:device:*、iot:telemetry:read 与
--          iot:rule:manage 权限码登记到 sys_permission，并为超级管理员角色
--          （ADMIN，role_id=1）授权，避免集成服务接口因权限码未登记而恒 403。
-- 业务背景：方案 M5 集成域（批次 E1-E3）——外部系统连接器配置、租户级凭据
--          管理与物联网设备/遥测/规则能力；入站 webhook 免用户认证（HMAC 验签），
--          不设权限码。
-- 菜单说明：参照 V81 的登记模式，前端集成管理页面尚未实现，本版本只登记
--          权限码不建菜单；待前端页面落地后补充 sys_menu 登记及菜单-权限关联。
-- 前置依赖：V1-V82 已完成；sys_permission/sys_role_permission 表存在。
-- 影响范围：新增 10 个权限码与超管角色授权，不修改既有权限。
-- 幂等性：INSERT ... SELECT ... FROM DUAL WHERE NOT EXISTS（按 code 防重）+
--          授权 INSERT IGNORE，可重复执行。
-- MySQL 兼容性：使用 MySQL 8.0 语法，由 Flyway V83 顺序执行。
-- 数据风险：新增行不覆盖既有数据；权限 ID 使用 2810-2815 与 2820-2823 保留段
--          （2700-2707 已被 V79 使用，2750-2752 已被 V81 使用，2830-2831 已被 V82 使用）。
-- 运维注意：脚本执行后，非超管角色如需使用集成管理功能，须在角色管理中
--          手动授权；连接器/凭据/设备/遥测/规则分别授予，保持最小权限。
-- 迁移元信息：版本 V83；执行阶段为集成服务接入阶段（批次 E4）。
-- Checksum 提示：修改已执行迁移的注释会改变 Flyway checksum；存量环境需评估后 repair。
-- ============================================================

-- ============================================================
-- 1. 集成连接器与凭据（IntegrationConnectorHandler / CredentialHandler）— 6 个权限码
-- ============================================================
INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2810, '连接器查询', 'integration:connector:read', '查询集成连接器配置详情与分页列表', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'integration:connector:read');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2811, '连接器创建', 'integration:connector:create', '创建集成连接器配置', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'integration:connector:create');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2812, '连接器更新', 'integration:connector:update', '更新集成连接器配置与启停状态', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'integration:connector:update');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2813, '连接器删除', 'integration:connector:delete', '删除集成连接器配置', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'integration:connector:delete');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2814, '凭据查询', 'integration:credential:read', '查询租户级外部系统凭据登记信息（不含明文）', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'integration:credential:read');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2815, '凭据管理', 'integration:credential:manage', '注册、轮换与删除租户级外部系统凭据', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'integration:credential:manage');

-- ============================================================
-- 2. 物联网设备域（IoT 设备 / 遥测 / 规则 Handler）— 4 个权限码
-- ============================================================
INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2820, '设备查询', 'iot:device:read', '查询物联网设备、设备影子与下行命令记录', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'iot:device:read');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2821, '设备管理', 'iot:device:manage', '注册、变更与注销物联网设备及凭据', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'iot:device:manage');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2822, '遥测查询', 'iot:telemetry:read', '查询设备遥测记录与影子状态', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'iot:telemetry:read');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2823, '规则管理', 'iot:rule:manage', '创建、变更、启停与删除物联网轻量规则', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'iot:rule:manage');

-- ============================================================
-- 3. 为超级管理员角色（ADMIN，role_id=1）授权
-- ============================================================
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT 1, p.id
FROM sys_permission p
WHERE p.code IN (
    'integration:connector:read',
    'integration:connector:create',
    'integration:connector:update',
    'integration:connector:delete',
    'integration:credential:read',
    'integration:credential:manage',
    'iot:device:read',
    'iot:device:manage',
    'iot:telemetry:read',
    'iot:rule:manage'
)
AND EXISTS (SELECT 1 FROM sys_role WHERE id = 1 AND code = 'ADMIN');

-- ============================================================
-- 执行结果验证建议：
-- 1. 查询新增权限码：
--    SELECT id, name, code FROM sys_permission WHERE id BETWEEN 2810 AND 2823 ORDER BY id;
-- 2. 验证超管授权：
--    SELECT p.code FROM sys_role_permission rp
--    JOIN sys_permission p ON p.id = rp.permission_id
--    WHERE rp.role_id = 1 AND p.id BETWEEN 2810 AND 2823;
-- ============================================================
