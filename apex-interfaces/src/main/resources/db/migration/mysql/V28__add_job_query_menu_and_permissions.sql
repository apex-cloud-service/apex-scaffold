-- 脚本用途：为定时任务分页查询功能添加菜单和权限配置。
-- 业务背景：新增定时任务分页查询接口（GET /api/jobs/page），需在管理后台提供对应的查询页面和访问权限。
-- 前置依赖：V1-V27 已完成，sys_menu、sys_permission 表存在，定时任务管理菜单（id=1005）已配置。
-- 影响范围：在系统管理目录下新增"定时任务查询"菜单项，并添加对应的查询权限码。
-- 幂等性：使用 INSERT ... ON DUPLICATE KEY UPDATE，可重复执行。
-- MySQL/Flyway 兼容性：使用 MySQL 8.x 语法，由 Flyway V28 顺序执行。
-- 数据风险：新增菜单和权限，不影响现有数据。已有角色如需访问新菜单，需手动授权。
-- 运维注意：脚本执行后，需在角色管理中为相关角色授予 system:job:page 权限。
-- 迁移元信息：版本 V28；执行阶段为定时任务查询功能上线阶段。
-- Checksum 提示：修改已执行迁移的注释会改变 Flyway checksum；存量环境需评估后 repair。
-- ============================================================
-- V28: 添加定时任务分页查询菜单和权限
-- ============================================================
-- 背景：新增定时任务分页查询接口（GET /api/jobs/page），前端组件为 system/job/query，
--      需在管理后台菜单树中添加对应菜单项，并配置查询权限码。
-- 菜单层级：系统管理（id=1000） → 定时任务查询（id=1013）
-- 权限码：system:job:page（定时任务分页查询）
-- ============================================================

-- 1. 添加定时任务查询菜单（菜单类型 1=菜单，挂载到系统管理目录下）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
VALUES (1013, 1000, '定时任务查询', 1, 'job-query', 'system/job/query', 'system:job:page', 'Search', 8, 0, 0)
ON DUPLICATE KEY UPDATE 
    menu_name = '定时任务查询', 
    parent_id = 1000, 
    menu_type = 1, 
    path = 'job-query',
    component = 'system/job/query', 
    perms = 'system:job:page', 
    icon = 'Search',
    sort = 8,
    visible = 0,
    status = 0;

-- 2. 添加定时任务分页查询权限码
INSERT INTO sys_permission (id, name, code, description, type, status) 
VALUES (2042, '定时任务分页查询', 'system:job:page', '分页查询定时任务列表，支持按名称、分组、状态筛选', 1, 1)
ON DUPLICATE KEY UPDATE 
    name = '定时任务分页查询',
    code = 'system:job:page',
    description = '分页查询定时任务列表，支持按名称、分组、状态筛选',
    type = 1,
    status = 1;

-- 3. 为超级管理员角色（ADMIN，role_id=1）自动授予新增权限
-- 说明：超级管理员应默认拥有所有权限，此处自动授权以保持一致性
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT 1, 2042
WHERE EXISTS (SELECT 1 FROM sys_role WHERE id = 1 AND code = 'ADMIN');

-- ============================================================
-- 执行结果验证建议：
-- 1. 查询菜单是否正确插入：
--    SELECT * FROM sys_menu WHERE id = 1013;
-- 2. 查询权限是否正确插入：
--    SELECT * FROM sys_permission WHERE id = 2042;
-- 3. 查询超级管理员是否已授权：
--    SELECT * FROM sys_role_permission WHERE role_id = 1 AND permission_id = 2042;
-- 4. 前端验证：登录管理后台，检查"系统管理"目录下是否显示"定时任务查询"菜单
-- 5. 接口验证：访问 GET /api/jobs/page?pageNum=1&pageSize=20 测试接口可用性
-- ============================================================
