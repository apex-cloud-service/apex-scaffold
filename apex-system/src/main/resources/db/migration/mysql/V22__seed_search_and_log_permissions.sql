-- 脚本用途：初始化全文搜索与日志查询系统的权限、管理员授权及菜单入口。
-- 业务背景：为全文搜索、运行时日志查询、业务日志统一查询提供完整授权导航。
-- 前置依赖：V11 已创建监控中心，V13 已创建API调用日志权限，目标表与 ADMIN 角色均可用。
-- 影响范围：写入权限、角色权限关联和菜单配置，不变更表结构及数据。
-- 幂等性：使用 ON DUPLICATE KEY UPDATE 与 NOT EXISTS 条件插入，设计为可重复执行。
-- MySQL/Flyway 兼容性：使用 MySQL 8.x DML、VALUES() 与 FROM DUAL，由 Flyway V22 执行。
-- 数据风险：固定 ID 冲突或错误授权会造成迁移失败、越权访问或菜单异常。
-- 运维注意：执行前核对权限 ID 号段，执行后验证 ADMIN 授权与菜单访问。
-- 迁移元信息：版本 V22；执行阶段为全文搜索与日志查询系统的权限与菜单上线阶段。
-- Checksum 提示：修改已执行迁移的注释也会改变 Flyway checksum；存量环境需评估后 repair，禁止盲目重跑。
-- ================================================================
-- Apex Scaffold V22 迁移：全文搜索与日志查询权限与菜单
-- ================================================================
-- 说明：为全文搜索（Meilisearch）、运行时日志查询（Loki）、
--      业务日志统一查询补充权限码、ADMIN 角色授权与菜单项。
-- 依赖：V11 已创建「监控中心」目录（id=1100）。
-- 权限码：V4 用 2025-2026、V5 用 2027、V6 用 2028、V8 用 2033-2041、
--      V11 用 2050、V13 用 2051-2052，本迁移从 2053 起。
-- 菜单ID：V11 用 1101、V13 用 1102，本迁移从 1103 起。
-- 角色权限ID：V13 用 10051-10052，本迁移从 10053 起。
-- 本脚本幂等可重复执行。
-- ================================================================

-- ================================================================
-- 一、全文搜索权限与菜单
-- ================================================================

-- 1.1 权限码
INSERT INTO sys_permission (id, name, code, description, type, status) VALUES
    (2053, '全文搜索', 'system:search:query', '全文搜索功能（用户/角色/菜单/部门/字典/配置）', 1, 1),
    (2054, '索引重建', 'system:search:rebuild', '重建搜索索引', 1, 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), status = 1;

-- 1.2 授予 ADMIN 角色
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT 10053, 1, 2053
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE role_id = 1 AND permission_id = 2053);

INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT 10054, 1, 2054
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE role_id = 1 AND permission_id = 2054);

-- 1.3 菜单项（全局搜索在导航栏，不需要单独菜单项，此处仅记录权限）

-- ================================================================
-- 二、运行时日志查询权限与菜单
-- ================================================================

-- 2.1 权限码
INSERT INTO sys_permission (id, name, code, description, type, status) VALUES
    (2055, '运行时日志查询', 'system:log:runtime:query', '查询应用运行时日志（Loki）', 1, 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), status = 1;

-- 2.2 授予 ADMIN 角色
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT 10055, 1, 2055
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE role_id = 1 AND permission_id = 2055);

-- 2.3 菜单项（归入「监控中心」目录）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
VALUES (1103, 1100, '运行时日志', 1, 'runtime-log', 'monitor/runtime-log/index', 'system:log:runtime:query', 'DocumentCopy', 5, 1, 1)
ON DUPLICATE KEY UPDATE menu_name = '运行时日志', parent_id = 1100, menu_type = 1, path = 'runtime-log',
                        component = 'monitor/runtime-log/index', perms = 'system:log:runtime:query',
                        icon = 'DocumentCopy', sort = 5, visible = 1, status = 1;

-- ================================================================
-- 三、业务日志统一查询权限与菜单
-- ================================================================

-- 3.1 权限码
INSERT INTO sys_permission (id, name, code, description, type, status) VALUES
    (2056, '业务日志查询', 'system:log:business:query', '统一查询业务日志（登录/操作/API）', 1, 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), status = 1;

-- 3.2 授予 ADMIN 角色
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT 10056, 1, 2056
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE role_id = 1 AND permission_id = 2056);

-- 3.3 菜单项（归入「监控中心」目录）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
VALUES (1104, 1100, '业务日志', 1, 'business-log', 'monitor/business-log/index', 'system:log:business:query', 'List', 6, 1, 1)
ON DUPLICATE KEY UPDATE menu_name = '业务日志', parent_id = 1100, menu_type = 1, path = 'business-log',
                        component = 'monitor/business-log/index', perms = 'system:log:business:query',
                        icon = 'List', sort = 6, visible = 1, status = 1;

-- ================================================================
-- 迁移完成说明
-- ================================================================
-- 本迁移添加了：
-- 1. 4个权限码（2053-2056）
-- 2. 4个角色权限关联（10053-10056，授予ADMIN角色）
-- 3. 2个菜单项（1103-1104，归入监控中心）
--
-- API端点映射：
-- - GET /admin-api/v1/search                  → system:search:query
-- - POST /admin-api/v1/search/rebuild         → system:search:rebuild
-- - GET /admin-api/v1/logs/runtime            → system:log:runtime:query
-- - GET /admin-api/v1/logs/business           → system:log:business:query
--
-- 前端页面映射：
-- - 全局搜索：导航栏组件（无独立菜单）
-- - 运行时日志：/monitor/runtime-log
-- - 业务日志：/monitor/business-log
--
-- 验证方法：
-- 1. 查询权限是否插入：SELECT * FROM sys_permission WHERE id BETWEEN 2053 AND 2056;
-- 2. 查询ADMIN角色授权：SELECT * FROM sys_role_permission WHERE id BETWEEN 10053 AND 10056;
-- 3. 查询菜单是否插入：SELECT * FROM sys_menu WHERE id IN (1103, 1104);
-- 4. 登录管理后台，验证菜单可见且可访问
-- ================================================================
