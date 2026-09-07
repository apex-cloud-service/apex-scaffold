-- ============================================================
-- 前端按钮权限配置SQL
-- ============================================================
-- 项目: Apex Scaffold
-- 数据库: apex_data
-- 执行时机: 前端代码添加v-permission指令后执行
-- 创建日期: 2026-09-07
-- ============================================================

-- 1. 菜单管理权限
-- 查询菜单管理的菜单ID
SET @menu_mgmt_id = (SELECT id FROM sys_menu WHERE component = 'system/menu/index' LIMIT 1);

-- 新增按钮权限
INSERT INTO sys_menu (menu_name, parent_id, menu_type, perms, sort, visible, status, create_time)
SELECT '新增菜单', @menu_mgmt_id, 2, 'system:menu:add', 1, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:menu:add');

INSERT INTO sys_menu (menu_name, parent_id, menu_type, perms, sort, visible, status, create_time)
SELECT '编辑菜单', @menu_mgmt_id, 2, 'system:menu:edit', 2, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:menu:edit');

INSERT INTO sys_menu (menu_name, parent_id, menu_type, perms, sort, visible, status, create_time)
SELECT '删除菜单', @menu_mgmt_id, 2, 'system:menu:delete', 3, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:menu:delete');

-- 2. 用户管理权限(staff:manage已存在,无需新增)

-- 3. 部门管理权限
SET @dept_mgmt_id = (SELECT id FROM sys_menu WHERE component = 'system/dept/index' LIMIT 1);

INSERT INTO sys_menu (menu_name, parent_id, menu_type, perms, sort, visible, status, create_time)
SELECT '新增部门', @dept_mgmt_id, 2, 'system:dept:add', 1, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:dept:add');

INSERT INTO sys_menu (menu_name, parent_id, menu_type, perms, sort, visible, status, create_time)
SELECT '编辑部门', @dept_mgmt_id, 2, 'system:dept:edit', 2, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:dept:edit');

INSERT INTO sys_menu (menu_name, parent_id, menu_type, perms, sort, visible, status, create_time)
SELECT '删除部门', @dept_mgmt_id, 2, 'system:dept:delete', 3, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:dept:delete');

-- 4. 字典管理权限
SET @dict_mgmt_id = (SELECT id FROM sys_menu WHERE component = 'system/dict/index' LIMIT 1);

INSERT INTO sys_menu (menu_name, parent_id, menu_type, perms, sort, visible, status, create_time)
SELECT '新增字典', @dict_mgmt_id, 2, 'system:dict:add', 1, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:dict:add');

INSERT INTO sys_menu (menu_name, parent_id, menu_type, perms, sort, visible, status, create_time)
SELECT '编辑字典', @dict_mgmt_id, 2, 'system:dict:edit', 2, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:dict:edit');

INSERT INTO sys_menu (menu_name, parent_id, menu_type, perms, sort, visible, status, create_time)
SELECT '删除字典', @dict_mgmt_id, 2, 'system:dict:delete', 3, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:dict:delete');

-- 5. 参数配置权限
SET @config_mgmt_id = (SELECT id FROM sys_menu WHERE component = 'system/config/index' LIMIT 1);

INSERT INTO sys_menu (menu_name, parent_id, menu_type, perms, sort, visible, status, create_time)
SELECT '新增参数', @config_mgmt_id, 2, 'system:config:add', 1, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:config:add');

INSERT INTO sys_menu (menu_name, parent_id, menu_type, perms, sort, visible, status, create_time)
SELECT '编辑参数', @config_mgmt_id, 2, 'system:config:edit', 2, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:config:edit');

INSERT INTO sys_menu (menu_name, parent_id, menu_type, perms, sort, visible, status, create_time)
SELECT '删除参数', @config_mgmt_id, 2, 'system:config:delete', 3, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:config:delete');

INSERT INTO sys_menu (menu_name, parent_id, menu_type, perms, sort, visible, status, create_time)
SELECT '刷新缓存', @config_mgmt_id, 2, 'system:config:refresh', 4, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:config:refresh');

-- 6. 数据库配置权限
SET @db_mgmt_id = (SELECT id FROM sys_menu WHERE component = 'system/database/index' LIMIT 1);

INSERT INTO sys_menu (menu_name, parent_id, menu_type, perms, sort, visible, status, create_time)
SELECT '新增数据源', @db_mgmt_id, 2, 'system:database:add', 1, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:database:add');

INSERT INTO sys_menu (menu_name, parent_id, menu_type, perms, sort, visible, status, create_time)
SELECT '编辑数据源', @db_mgmt_id, 2, 'system:database:edit', 2, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:database:edit');

INSERT INTO sys_menu (menu_name, parent_id, menu_type, perms, sort, visible, status, create_time)
SELECT '删除数据源', @db_mgmt_id, 2, 'system:database:delete', 3, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:database:delete');

INSERT INTO sys_menu (menu_name, parent_id, menu_type, perms, sort, visible, status, create_time)
SELECT '测试连接', @db_mgmt_id, 2, 'system:database:test', 4, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:database:test');

-- 7. 定时任务权限
SET @job_mgmt_id = (SELECT id FROM sys_menu WHERE component = 'system/job/index' LIMIT 1);

INSERT INTO sys_menu (menu_name, parent_id, menu_type, perms, sort, visible, status, create_time)
SELECT '新增任务', @job_mgmt_id, 2, 'system:job:add', 1, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:job:add');

INSERT INTO sys_menu (menu_name, parent_id, menu_type, perms, sort, visible, status, create_time)
SELECT '编辑任务', @job_mgmt_id, 2, 'system:job:edit', 2, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:job:edit');

INSERT INTO sys_menu (menu_name, parent_id, menu_type, perms, sort, visible, status, create_time)
SELECT '删除任务', @job_mgmt_id, 2, 'system:job:delete', 3, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:job:delete');

INSERT INTO sys_menu (menu_name, parent_id, menu_type, perms, sort, visible, status, create_time)
SELECT '执行任务', @job_mgmt_id, 2, 'system:job:execute', 4, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:job:execute');

INSERT INTO sys_menu (menu_name, parent_id, menu_type, perms, sort, visible, status, create_time)
SELECT '查看日志', @job_mgmt_id, 2, 'system:job:log:view', 5, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:job:log:view');

-- 8. 为管理员角色分配所有权限
-- 假设管理员角色ID为1,如不是请修改
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu 
WHERE menu_type = 2 
  AND (
    perms LIKE 'system:menu:%'
    OR perms LIKE 'system:dept:%'
    OR perms LIKE 'system:dict:%'
    OR perms LIKE 'system:config:%'
    OR perms LIKE 'system:database:%'
    OR perms LIKE 'system:job:%'
  )
  AND id NOT IN (SELECT menu_id FROM sys_role_menu WHERE role_id = 1);

-- ============================================================
-- 验证SQL - 执行后检查结果
-- ============================================================
SELECT 
  m1.menu_name AS '父菜单',
  m2.menu_name AS '按钮权限',
  m2.perms AS '权限码',
  CASE WHEN rm.role_id IS NOT NULL THEN '✅ 已授权' ELSE '❌ 未授权' END AS '管理员授权状态'
FROM sys_menu m2
LEFT JOIN sys_menu m1 ON m2.parent_id = m1.id
LEFT JOIN sys_role_menu rm ON m2.id = rm.menu_id AND rm.role_id = 1
WHERE m2.menu_type = 2 
  AND m2.perms LIKE 'system:%'
ORDER BY m1.sort, m2.sort;

-- 预期结果: 所有权限码的"管理员授权状态"都应该是"✅ 已授权"
