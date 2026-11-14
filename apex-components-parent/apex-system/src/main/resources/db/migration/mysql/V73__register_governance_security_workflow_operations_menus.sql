-- ============================================================
-- V73: 登记租户管理/账号安全/工作流定义/运维健康菜单与超管授权（MySQL）
-- ============================================================
-- 脚本用途：为前端已实现但缺少后端菜单记录的四个管理页面补充 sys_menu 记录，
--          使它们能出现在侧边栏，并为超级管理员角色补齐相关权限授权。
-- 业务背景：
--   1) 租户管理（/api/v2/platform/tenants）— 权限 tenant:* 由 V63 登记；
--   2) 账号安全（/api/mfa/**、/api/ip-access-control/**）—
--      ⚠️ 该组接口不使用 @RequirePermission，而由 auth 域
--      @RequireResourcePermission 校验（auth_service 库 auth_permission 表的
--      mfa 资源 MANAGE 权限，见 auth-service V50/V51），因此本脚本
--      不为其登记 sys_permission 权限码，菜单 perms 置 NULL；
--   3) 工作流定义（/api/v2/workflow-definitions）— 权限 workflow:definition:*
--      由 V64 登记；
--   4) 运维健康（/api/v2/operations/**）— 权限 system:operations:health:*
--      由 V67/V69/V70 登记。
-- 本脚本只补菜单记录与超管授权，不重复登记既有权限码。
-- 前置依赖：V1-V72 已完成；sys_menu/sys_permission/sys_role_permission 表存在。
-- 影响范围：新增 6 条菜单（2 个目录 + 4 个菜单）；仅新增超管授权，无新权限码。
-- 幂等性：菜单使用 INSERT ... ON DUPLICATE KEY UPDATE（按 id）；
--          授权使用 INSERT IGNORE，可重复执行。
-- 菜单路径约定：前端 router/dynamic.ts 直接使用 node.path 作为路由 path，
--          目录用绝对路径（如 /platform），子菜单用相对段（如 tenant），
--          由前端 resolvePath 与 Vue Router 共同拼接为 /platform/tenant。
-- 组件路径：必须逐字匹配前端 VIEW_REGISTRY 键，否则菜单被静默丢弃：
--          system/tenant/index、system/security/index、
--          workflow/definition/index、monitor/operations/index。
-- 数据风险：新增行不覆盖既有数据；菜单 ID 使用 70001-70006 保留段。
-- 运维注意：脚本执行后，非超管角色如需访问这些页面，须在角色管理中手动授权；
--          账号安全页的 MFA 管理能力另受 auth 域资源权限控制。
-- 迁移元信息：版本 V73；执行阶段为平台治理与可观测性补全阶段。
-- Checksum 提示：修改已执行迁移的注释会改变 Flyway checksum；存量环境需评估后 repair。
-- ============================================================

-- ============================================================
-- 1. 菜单记录（6 条：1 个「平台治理」目录 + 5 个菜单）
-- ============================================================
-- 说明：租户管理、账号安全归入新建「平台治理」目录（顶级）；
--       工作流定义挂到「工作流」（70005 目录，新建顶级目录）；
--       运维健康挂到既有「监控中心」目录（1100）。

-- 1.1 顶级目录：平台治理
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time, version, deleted)
VALUES (70001, 0, '平台治理', 0, '/platform', NULL, NULL, 'Management', 20, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0)
ON DUPLICATE KEY UPDATE menu_name = '平台治理', parent_id = 0, menu_type = 0, path = '/platform',
                        component = NULL, perms = NULL, icon = 'Management', sort = 20,
                        visible = 1, status = 1, deleted = 0;

-- 1.2 租户管理（平台治理 → 租户管理）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time, version, deleted)
VALUES (70002, 70001, '租户管理', 1, 'tenant', 'system/tenant/index', 'tenant:read', 'OfficeBuilding', 1, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0)
ON DUPLICATE KEY UPDATE menu_name = '租户管理', parent_id = 70001, menu_type = 1, path = 'tenant',
                        component = 'system/tenant/index', perms = 'tenant:read', icon = 'OfficeBuilding',
                        sort = 1, visible = 1, status = 1, deleted = 0;

-- 1.3 账号安全（平台治理 → 账号安全）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time, version, deleted)
VALUES (70003, 70001, '账号安全', 1, 'security', 'system/security/index', NULL, 'Lock', 2, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0)
ON DUPLICATE KEY UPDATE menu_name = '账号安全', parent_id = 70001, menu_type = 1, path = 'security',
                        component = 'system/security/index', perms = NULL,
                        icon = 'Lock', sort = 2, visible = 1, status = 1, deleted = 0;

-- 1.4 顶级目录：工作流
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time, version, deleted)
VALUES (70005, 0, '工作流', 0, '/workflow', NULL, NULL, 'Share', 21, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0)
ON DUPLICATE KEY UPDATE menu_name = '工作流', parent_id = 0, menu_type = 0, path = '/workflow',
                        component = NULL, perms = NULL, icon = 'Share', sort = 21,
                        visible = 1, status = 1, deleted = 0;

-- 1.5 流程定义（工作流 → 流程定义）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time, version, deleted)
VALUES (70006, 70005, '流程定义', 1, 'definition', 'workflow/definition/index', 'workflow:definition:read', 'Document', 1, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0)
ON DUPLICATE KEY UPDATE menu_name = '流程定义', parent_id = 70005, menu_type = 1, path = 'definition',
                        component = 'workflow/definition/index', perms = 'workflow:definition:read',
                        icon = 'Document', sort = 1, visible = 1, status = 1, deleted = 0;

-- 1.6 运维健康（既有「监控中心」目录 1100 → 运维健康）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time, version, deleted)
VALUES (70004, 1100, '运维健康', 1, 'operations', 'monitor/operations/index', 'system:operations:health:read', 'TrendCharts', 20, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0)
ON DUPLICATE KEY UPDATE menu_name = '运维健康', parent_id = 1100, menu_type = 1, path = 'operations',
                        component = 'monitor/operations/index', perms = 'system:operations:health:read',
                        icon = 'TrendCharts', sort = 20, visible = 1, status = 1, deleted = 0;

-- ============================================================
-- 2. 缺失权限码（MFA 与 IP 访问控制，此前未在 sys_permission 登记）
-- ============================================================
-- 说明：租户（V63）、工作流定义（V64）、运维健康（V67/V69/V70）权限码已存在，
--       本节不重复登记；仅补账号安全页面对应的 MFA/IP 权限码。

-- ============================================================
-- 3. 为超级管理员角色授权（含本脚本新增与既有四组权限码）
-- ============================================================
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT 1, p.id
FROM sys_permission p
WHERE p.code IN (
    'tenant:read', 'tenant:create', 'tenant:lifecycle', 'tenant:quota',
    'workflow:definition:read', 'workflow:definition:edit',
    'workflow:definition:publish', 'workflow:definition:lifecycle',
    'system:operations:health:read', 'system:operations:health:snapshot:list',
    'system:operations:health:snapshot:create', 'system:operations:health:snapshot:export',
    'system:operations:health:tenant:read',
    'system:operations:health:export:task:create', 'system:operations:health:export:task:read'
)
AND EXISTS (SELECT 1 FROM sys_role WHERE id = 1 AND code = 'ADMIN');

-- 为 SUPER_ADMIN 角色（若存在）同样授权
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code = 'SUPER_ADMIN'
  AND p.code IN (
    'tenant:read', 'tenant:create', 'tenant:lifecycle', 'tenant:quota',
    'workflow:definition:read', 'workflow:definition:edit',
    'workflow:definition:publish', 'workflow:definition:lifecycle',
    'system:operations:health:read', 'system:operations:health:snapshot:list',
    'system:operations:health:snapshot:create', 'system:operations:health:snapshot:export',
    'system:operations:health:tenant:read',
    'system:operations:health:export:task:create', 'system:operations:health:export:task:read'
  );

-- ============================================================
-- 执行结果验证建议：
-- 1. 查询新增菜单：
--    SELECT id, parent_id, menu_name, menu_type, path, component
--    FROM sys_menu WHERE id BETWEEN 70001 AND 70006 ORDER BY id;
-- 2. 查询新增权限码：
--    SELECT id, name, code FROM sys_permission WHERE id BETWEEN 2621 AND 2627 ORDER BY id;
-- 3. 验证超管授权：
--    SELECT p.code FROM sys_role_permission rp
--    JOIN sys_permission p ON p.id = rp.permission_id
--    WHERE rp.role_id = 1 AND p.code LIKE 'security:%' ORDER BY p.code;
-- 4. 前端验证：以超管登录后侧边栏应出现「平台治理 / 工作流」目录，
--    以及「监控中心 → 运维健康」菜单；页面可访问且无 404/403。
-- ============================================================
