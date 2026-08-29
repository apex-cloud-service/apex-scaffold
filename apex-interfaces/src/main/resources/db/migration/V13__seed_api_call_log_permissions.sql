-- ================================================================
-- Apex Scaffold V13 迁移：API调用日志查询权限与菜单
-- ================================================================
-- 说明：为对外 HTTP 调用日志（sys_api_call_log 表）补充查询权限码、
--      ADMIN 角色授权与菜单项，支撑管理后台「监控中心-API调用日志」
--      页面（GET /admin-api/v1/api-call-logs，权限 system:api-call-log:list）。
-- 依赖：V11 已创建「监控中心」目录（id=1100）与「监控大盘」菜单（id=1101）。
-- 权限码：V4 用 2025-2026、V5 用 2027、V6 用 2028、V8 用 2033-2041、
--      V11 用 2050，本迁移从 2051 起；角色权限关联沿用 V11 的幂等写法。
-- 本脚本幂等可重复执行。
-- ================================================================

-- 1. 权限码
INSERT INTO sys_permission (id, name, code, description, type, status) VALUES
    (2051, 'API调用日志列表', 'system:api-call-log:list', '查询对外API调用日志（分页）', 1, 1),
    (2052, 'API调用日志详情', 'system:api-call-log:query', '查询API调用日志详情', 1, 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), status = 1;

-- 2. 授予 ADMIN 角色（表无唯一键且 id 非自增，按存在性防重保证幂等）
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT 10051, 1, 2051
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE role_id = 1 AND permission_id = 2051);

INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT 10052, 1, 2052
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE role_id = 1 AND permission_id = 2052);

-- 3. 菜单项（归入「监控中心」目录，component 对应前端视图）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
VALUES (1102, 1100, 'API调用日志', 1, 'api-call-log', 'monitor/api-call-log/index', 'system:api-call-log:list', 'Link', 4, 1, 1)
ON DUPLICATE KEY UPDATE menu_name = 'API调用日志', parent_id = 1100, menu_type = 1, path = 'api-call-log',
                        component = 'monitor/api-call-log/index', perms = 'system:api-call-log:list',
                        icon = 'Link', sort = 4, visible = 1, status = 1;
