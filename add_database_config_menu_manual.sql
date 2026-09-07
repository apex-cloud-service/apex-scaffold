-- 数据库配置菜单 - 手动执行版本（简化）
-- 执行: mysql -u root -p apex_db < add_database_config_menu_manual.sql

-- 1. 添加数据库配置菜单
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time, deleted)
VALUES (1013, 1000, '数据库配置', 1, 'database', 'system/database/index', 'system:database:view', 'Database', 8, 0, 0, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    menu_name = '数据库配置',
    update_time = NOW();

-- 2. 添加数据库配置相关权限码
INSERT INTO sys_permission (id, name, code, description, type, status, create_time, update_time, deleted) VALUES
    (2042, '数据库配置页面', 'system:database:view',     '查看数据库配置页面', 1, 1, NOW(), NOW(), 0),
    (2043, '测试数据库连接', 'system:database:test',     '测试数据库连接',     1, 1, NOW(), NOW(), 0),
    (2044, '生成部署配置',   'system:database:generate', '生成部署配置',       1, 1, NOW(), NOW(), 0),
    (2045, '查询数据库信息', 'system:database:info',     '查询当前数据库信息', 1, 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    update_time = NOW();

-- 3. 为超级管理员角色授予数据库配置权限
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT 1, id
FROM sys_permission
WHERE code IN ('system:database:view', 'system:database:test', 'system:database:generate', 'system:database:info')
  AND status = 1
  AND deleted = 0;

-- 验证查询
SELECT '=== 菜单验证 ===' AS '';
SELECT id, menu_name, path, component FROM sys_menu WHERE id = 1013;

SELECT '=== 权限验证 ===' AS '';
SELECT id, code, name FROM sys_permission WHERE code LIKE 'system:database:%' ORDER BY id;

SELECT '=== 角色权限验证 ===' AS '';
SELECT COUNT(*) AS granted_count FROM sys_role_permission rp
JOIN sys_permission p ON rp.permission_id = p.id
WHERE rp.role_id = 1 AND p.code LIKE 'system:database:%';
