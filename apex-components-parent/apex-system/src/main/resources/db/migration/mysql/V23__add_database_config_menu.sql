-- ============================================================
-- V23: 添加数据库配置菜单和权限
-- ============================================================
-- 背景：支持多数据库部署配置采集功能，需要在系统管理菜单下新增数据库配置页面。
-- 功能：数据库连接测试、部署配置生成（YAML/env/docker-compose）、当前数据库信息查询。
-- 定位：部署配置生成工具，不是运行时数据库管理控制台。
-- 依赖：前端 system/database/index 视图已在 VIEW_REGISTRY 注册。
-- 幂等性：使用 INSERT ... ON DUPLICATE KEY UPDATE 确保可重复执行。
-- ============================================================

-- 1. 添加数据库配置菜单（系统管理目录下）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time, deleted)
VALUES (1013, 1000, '数据库配置', 1, 'database', 'system/database/index', 'system:database:view', 'Database', 8, 0, 0, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    menu_name = '数据库配置',
    parent_id = 1000,
    menu_type = 1,
    path = 'database',
    component = 'system/database/index',
    perms = 'system:database:view',
    icon = 'Database',
    sort = 8,
    status = 0,
    visible = 0,
    update_time = NOW();

-- 2. 添加数据库配置相关权限码
INSERT INTO sys_permission (id, name, code, description, type, status, create_time, update_time, deleted) VALUES
    (2042, '数据库配置页面', 'system:database:view',     '查看数据库配置页面', 1, 1, NOW(), NOW(), 0),
    (2043, '测试数据库连接', 'system:database:test',     '测试数据库连接',     1, 1, NOW(), NOW(), 0),
    (2044, '生成部署配置',   'system:database:generate', '生成部署配置',       1, 1, NOW(), NOW(), 0),
    (2045, '查询数据库信息', 'system:database:info',     '查询当前数据库信息', 1, 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    description = VALUES(description),
    status = 1,
    update_time = NOW();

-- 3. 为管理员角色授予数据库配置权限，关联主键必须显式提供。
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT 230000 + p.id, 1, p.id
FROM sys_permission p
WHERE p.code IN ('system:database:view', 'system:database:test', 'system:database:generate', 'system:database:info')
  AND p.status = 1
  AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = 1 AND rp.permission_id = p.id);
