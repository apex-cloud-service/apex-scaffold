-- ============================================================
-- 数据库配置菜单和权限 - 手动执行版本
-- ============================================================
-- 说明：本脚本适用于手动执行，支持 MySQL 和 PostgreSQL。
--       如果使用 Flyway，请使用对应数据库的 V22 迁移脚本。
-- ============================================================

-- ============================================================
-- MySQL 版本
-- ============================================================
-- 使用条件：当前数据库是 MySQL
-- 执行方式：mysql -u root -p apex_db < database_config_menu.sql
-- ============================================================

-- 1. 添加数据库配置菜单（系统管理目录下）
-- 菜单 ID: 1013
-- 父菜单 ID: 1000（系统管理）
-- 排序: 8（在参数配置和定时任务之间）
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
-- 权限 ID: 2042-2045
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

-- 3. 为超级管理员角色（ADMIN）授予数据库配置权限
-- 角色 ID: 1（超级管理员）
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT 1, id
FROM sys_permission
WHERE code IN ('system:database:view', 'system:database:test', 'system:database:generate', 'system:database:info')
  AND status = 1
  AND deleted = 0;

-- ============================================================
-- PostgreSQL 版本（注释掉，需要时取消注释）
-- ============================================================
-- 使用条件：当前数据库是 PostgreSQL
-- 执行方式：psql -U postgres -d apex_db -f database_config_menu.sql
-- ============================================================

/*
-- 1. 添加数据库配置菜单（系统管理目录下）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time, deleted)
VALUES (1013, 1000, '数据库配置', 1, 'database', 'system/database/index', 'system:database:view', 'Database', 8, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON CONFLICT (id) DO UPDATE SET
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
    update_time = CURRENT_TIMESTAMP;

-- 2. 添加数据库配置相关权限码
INSERT INTO sys_permission (id, name, code, description, type, status, create_time, update_time, deleted) VALUES
    (2042, '数据库配置页面', 'system:database:view',     '查看数据库配置页面', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (2043, '测试数据库连接', 'system:database:test',     '测试数据库连接',     1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (2044, '生成部署配置',   'system:database:generate', '生成部署配置',       1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (2045, '查询数据库信息', 'system:database:info',     '查询当前数据库信息', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    status = 1,
    update_time = CURRENT_TIMESTAMP;

-- 3. 为超级管理员角色授予数据库配置权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, id
FROM sys_permission
WHERE code IN ('system:database:view', 'system:database:test', 'system:database:generate', 'system:database:info')
  AND status = 1
  AND deleted = 0
ON CONFLICT (role_id, permission_id) DO NOTHING;
*/

-- ============================================================
-- 验证语句
-- ============================================================
-- 执行完成后，可以运行以下查询验证：

-- 1. 检查菜单是否添加成功
SELECT id, menu_name, path, component, perms, parent_id, sort
FROM sys_menu
WHERE id = 1013;

-- 2. 检查权限是否添加成功
SELECT id, name, code, description
FROM sys_permission
WHERE code LIKE 'system:database:%'
ORDER BY id;

-- 3. 检查超级管理员是否拥有这些权限
SELECT rp.role_id, r.name AS role_name, p.code AS permission_code, p.name AS permission_name
FROM sys_role_permission rp
JOIN sys_role r ON rp.role_id = r.id
JOIN sys_permission p ON rp.permission_id = p.id
WHERE r.code = 'ADMIN'
  AND p.code LIKE 'system:database:%'
ORDER BY p.code;

-- ============================================================
-- 回滚语句（需要时执行）
-- ============================================================
/*
-- 删除菜单
DELETE FROM sys_menu WHERE id = 1013;

-- 删除权限
DELETE FROM sys_permission WHERE id IN (2042, 2043, 2044, 2045);

-- 删除角色-权限关联
DELETE FROM sys_role_permission WHERE permission_id IN (2042, 2043, 2044, 2045);
*/
