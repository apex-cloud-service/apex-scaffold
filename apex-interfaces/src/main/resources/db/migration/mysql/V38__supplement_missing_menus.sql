-- ============================================================
-- Flyway 迁移脚本 V38：补充缺失的菜单数据
-- ============================================================
-- 执行日期: 2026-09-17
-- 目标: 补充数据库中缺失的关键菜单(工作台、数据管理、Office管理等)
-- 背景: 由于历史迁移脚本执行失败,导致部分重要菜单数据未初始化,
--       前端页面出现404和功能缺失问题
-- ============================================================

-- 1. 补充工作台菜单(前端静态路由已有,但后端菜单树也需要以便权限控制)
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time)
VALUES (100, 0, '工作台', 1, '/dashboard', 'dashboard/index', NULL, 'Odometer', 0, 0, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    menu_name = '工作台', 
    parent_id = 0, 
    menu_type = 1, 
    path = '/dashboard', 
    component = 'dashboard/index', 
    status = 0, 
    visible = 0, 
    sort = 0,
    update_time = NOW();

-- 2. 补充数据管理目录
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time)
VALUES (2000, 0, '数据管理', 0, '/data', NULL, NULL, 'DataAnalysis', 20, 0, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    menu_name = '数据管理', 
    menu_type = 0, 
    component = NULL, 
    status = 0, 
    visible = 0,
    update_time = NOW();

-- 3. 补充数据文档菜单
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time)
VALUES (2001, 2000, '数据文档', 1, 'document', 'data/document/index', 'data:document:list', 'Document', 1, 0, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    menu_name = '数据文档', 
    parent_id = 2000, 
    path = 'document', 
    component = 'data/document/index', 
    perms = 'data:document:list',
    status = 0, 
    visible = 0,
    update_time = NOW();

-- 4. 补充数据图表菜单
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time)
VALUES (2002, 2000, '数据图表', 1, 'chart', 'data/chart/index', 'data:chart:list', 'DataLine', 2, 0, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    menu_name = '数据图表', 
    parent_id = 2000, 
    path = 'chart', 
    component = 'data/chart/index', 
    perms = 'data:chart:list',
    status = 0, 
    visible = 0,
    update_time = NOW();

-- 5. 补充Office管理目录
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time)
VALUES (3000, 0, 'Office管理', 0, '/office', NULL, NULL, 'OfficeBuilding', 30, 0, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    menu_name = 'Office管理', 
    menu_type = 0, 
    component = NULL, 
    status = 0, 
    visible = 0,
    update_time = NOW();

-- 6. 补充Office消息菜单
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time)
VALUES (3001, 3000, 'Office消息', 1, 'message', 'office/message/index', 'office:message:list', 'Message', 1, 0, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    menu_name = 'Office消息', 
    parent_id = 3000, 
    path = 'message', 
    component = 'office/message/index', 
    perms = 'office:message:list',
    status = 0, 
    visible = 0,
    update_time = NOW();

-- 7. 补充Office审批菜单
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time)
VALUES (3002, 3000, 'Office审批', 1, 'approval', 'office/approval/index', 'office:approval:list', 'Select', 2, 0, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    menu_name = 'Office审批', 
    parent_id = 3000, 
    path = 'approval', 
    component = 'office/approval/index', 
    perms = 'office:approval:list',
    status = 0, 
    visible = 0,
    update_time = NOW();

-- 8. 补充Office配置菜单
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time)
VALUES (3003, 3000, 'Office配置', 1, 'config', 'office/config/index', 'office:config:list', 'Setting', 3, 0, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    menu_name = 'Office配置', 
    parent_id = 3000, 
    path = 'config', 
    component = 'office/config/index', 
    perms = 'office:config:list',
    status = 0, 
    visible = 0,
    update_time = NOW();

-- 9. 补充Office绑定菜单
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time)
VALUES (3004, 3000, 'Office绑定', 1, 'bindings', 'office/bindings/index', 'office:bindings:list', 'Connection', 4, 0, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    menu_name = 'Office绑定', 
    parent_id = 3000, 
    path = 'bindings', 
    component = 'office/bindings/index', 
    perms = 'office:bindings:list',
    status = 0, 
    visible = 0,
    update_time = NOW();

-- 10. 补充Office控制台菜单
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time)
VALUES (3005, 3000, 'Office控制台', 1, 'console', 'office/console/index', 'office:console:list', 'Monitor', 5, 0, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    menu_name = 'Office控制台', 
    parent_id = 3000, 
    path = 'console', 
    component = 'office/console/index', 
    perms = 'office:console:list',
    status = 0, 
    visible = 0,
    update_time = NOW();

-- 11. 补充Office组织架构菜单
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time)
VALUES (3006, 3000, 'Office组织架构', 1, 'organization', 'office/organization/index', 'office:organization:list', 'Histogram', 6, 0, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    menu_name = 'Office组织架构', 
    parent_id = 3000, 
    path = 'organization', 
    component = 'office/organization/index', 
    perms = 'office:organization:list',
    status = 0, 
    visible = 0,
    update_time = NOW();

-- 12. 补充Office日历菜单
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time)
VALUES (3007, 3000, 'Office日历', 1, 'calendar', 'office/calendar/index', 'office:calendar:list', 'Calendar', 7, 0, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    menu_name = 'Office日历', 
    parent_id = 3000, 
    path = 'calendar', 
    component = 'office/calendar/index', 
    perms = 'office:calendar:list',
    status = 0, 
    visible = 0,
    update_time = NOW();

-- 13. 补充Office文档菜单
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time)
VALUES (3008, 3000, 'Office文档', 1, 'document', 'office/document/index', 'office:document:list', 'FolderOpened', 8, 0, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    menu_name = 'Office文档', 
    parent_id = 3000, 
    path = 'document', 
    component = 'office/document/index', 
    perms = 'office:document:list',
    status = 0, 
    visible = 0,
    update_time = NOW();

-- 14. 补充定时任务查询菜单(已有job管理,补充query功能)
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time)
VALUES (1009, 1000, '定时任务查询', 1, 'job/query', 'system/job/query', 'system:job:query', 'Search', 8, 0, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    menu_name = '定时任务查询', 
    parent_id = 1000, 
    path = 'job/query', 
    component = 'system/job/query', 
    perms = 'system:job:query',
    status = 0, 
    visible = 0,
    update_time = NOW();

-- 15. 补充运行时日志菜单
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time)
VALUES (1103, 1100, '运行时日志', 1, 'runtime-log', 'monitor/runtime-log/index', 'monitor:runtime-log:list', 'Document', 3, 0, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    menu_name = '运行时日志', 
    parent_id = 1100, 
    path = 'runtime-log', 
    component = 'monitor/runtime-log/index', 
    perms = 'monitor:runtime-log:list',
    status = 0, 
    visible = 0,
    update_time = NOW();

-- 16. 补充业务日志菜单
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time)
VALUES (1104, 1100, '业务日志', 1, 'business-log', 'monitor/business-log/index', 'monitor:business-log:list', 'Tickets', 4, 0, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    menu_name = '业务日志', 
    parent_id = 1100, 
    path = 'business-log', 
    component = 'monitor/business-log/index', 
    perms = 'monitor:business-log:list',
    status = 0, 
    visible = 0,
    update_time = NOW();

-- ============================================================
-- 迁移说明
-- ============================================================
-- 1. 本迁移脚本补充了缺失的关键菜单数据
-- 2. 使用 ON DUPLICATE KEY UPDATE 确保幂等性,可重复执行
-- 3. 补充的菜单包括:
--    - 工作台(id=100)
--    - 数据管理目录及子菜单(id=2000-2002)
--    - Office管理目录及子菜单(id=3000-3008)
--    - 系统管理和监控中心的补充菜单(id=1009,1103,1104)
-- 4. 所有菜单默认启用(status=0)且可见(visible=0)
-- ============================================================
