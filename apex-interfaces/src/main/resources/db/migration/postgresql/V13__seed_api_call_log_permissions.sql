-- 脚本用途：初始化 API 调用日志查询权限、管理员授权及监控菜单入口。
-- 业务背景：为外部 HTTP 调用日志的后台分页查询和详情查看提供完整授权导航。
-- 前置依赖：V11 已创建监控中心，V1-V12 目标表与 ADMIN 角色均可用。
-- 影响范围：写入或更新权限、角色权限关联和菜单配置，不变更日志表结构及数据。
-- 幂等性：使用 ON CONFLICT (id) DO UPDATE SET 与 NOT EXISTS 条件插入，设计为可重复执行。
-- PostgreSQL/Flyway 兼容性：使用 PostgreSQL DML，由 Flyway V13 执行。
-- 数据风险：固定 ID 冲突或错误授权会造成迁移失败、越权访问或菜单异常。
-- 运维注意：执行前核对权限 ID 号段，执行后验证 ADMIN 授权与监控菜单访问。
-- 迁移元信息：版本 V13；执行阶段为 API 调用日志管理能力的权限与菜单上线阶段。
-- Checksum 提示：修改已执行迁移的注释也会改变 Flyway checksum；存量环境需评估后 repair，禁止盲目重跑。
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
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, status = 1;

-- 2. 授予 ADMIN 角色（表无唯一键且 id 非自增，按存在性防重保证幂等）
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT 10051, 1, 2051
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE role_id = 1 AND permission_id = 2051);

INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT 10052, 1, 2052
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE role_id = 1 AND permission_id = 2052);

-- 3. 菜单项（归入「监控中心」目录，component 对应前端视图）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
VALUES (1102, 1100, 'API调用日志', 1, 'api-call-log', 'monitor/api-call-log/index', 'system:api-call-log:list', 'Link', 4, 1, 1)
ON CONFLICT (id) DO UPDATE SET menu_name = 'API调用日志', parent_id = 1100, menu_type = 1, path = 'api-call-log',
                        component = 'monitor/api-call-log/index', perms = 'system:api-call-log:list',
                        icon = 'Link', sort = 4, visible = 1, status = 1;
