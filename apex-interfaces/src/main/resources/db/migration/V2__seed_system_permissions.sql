-- ================================================================
-- V2: 系统管理权限码与菜单种子数据
-- ================================================================
-- 用途: 预置系统管理权限码（system:*）与后台菜单树，使管理后台
--       部署后即可在"角色-权限"配置中直接引用，无需手工录入。
-- 背景: P0 阶段新增菜单/字典/参数/定时任务/登录日志能力后，
--       权限点与菜单导航的统一初始化。
-- 说明: 使用显式 ID（1000+ / 2000+），避免与雪花算法主键冲突；
--       由 Flyway 记录版本，仅执行一次。
-- ================================================================

-- ------------------------------------------------------------------
-- 1. 系统管理权限码（sys_permission）
-- ------------------------------------------------------------------
INSERT INTO sys_permission (id, name, code, description, type, status, create_time, update_time, version, deleted) VALUES
(2001, '员工管理', 'staff:manage', '后台员工创建/编辑/启停/角色配置', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2002, '会话撤销', 'staff:session:revoke', '强制撤销指定用户全部会话', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2003, '菜单列表', 'system:menu:list', '查询系统菜单树', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2004, '菜单查询', 'system:menu:query', '查询菜单详情', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2005, '菜单新增', 'system:menu:add', '创建系统菜单', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2006, '菜单编辑', 'system:menu:edit', '更新系统菜单', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2007, '菜单删除', 'system:menu:remove', '删除系统菜单', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2008, '字典列表', 'system:dict:list', '查询字典类型与数据', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2009, '字典查询', 'system:dict:query', '查询字典详情', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2010, '字典新增', 'system:dict:add', '创建字典类型与数据', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2011, '字典编辑', 'system:dict:edit', '更新字典类型与数据', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2012, '字典删除', 'system:dict:remove', '删除字典类型与数据', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2013, '参数列表', 'system:config:list', '查询系统参数配置', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2014, '参数查询', 'system:config:query', '查询参数配置详情', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2015, '参数新增', 'system:config:add', '创建系统参数', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2016, '参数编辑', 'system:config:edit', '更新系统参数', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2017, '参数删除', 'system:config:remove', '删除系统参数', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2018, '任务列表', 'system:job:list', '查询定时任务', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2019, '任务查询', 'system:job:query', '查询任务详情与执行日志', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2020, '任务新增', 'system:job:add', '创建定时任务', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2021, '任务编辑', 'system:job:edit', '更新定时任务', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2022, '任务删除', 'system:job:remove', '删除定时任务', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2023, '任务执行', 'system:job:run', '手动触发定时任务', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2024, '登录日志', 'system:login-log:list', '查询登录日志', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0);

-- ------------------------------------------------------------------
-- 2. 系统管理菜单树（sys_menu）
-- ------------------------------------------------------------------
-- 2.1 系统管理目录
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time, version, deleted) VALUES
(1000, 0, '系统管理', 0, 'system', 'system/index', NULL, 'Setting', 1, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0);

-- 2.2 系统管理子菜单
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time, version, deleted) VALUES
(1001, 1000, '用户管理', 1, 'system/user', 'system/user/index', 'staff:manage', 'User', 1, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(1002, 1000, '菜单管理', 1, 'system/menu', 'system/menu/index', 'system:menu:list', 'Menu', 2, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(1003, 1000, '字典管理', 1, 'system/dict', 'system/dict/index', 'system:dict:list', 'Collection', 3, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(1004, 1000, '参数配置', 1, 'system/config', 'system/config/index', 'system:config:list', 'Tools', 4, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(1005, 1000, '定时任务', 1, 'system/job', 'system/job/index', 'system:job:list', 'Timer', 5, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(1006, 1000, '登录日志', 1, 'system/login-log', 'system/login-log/index', 'system:login-log:list', 'Document', 6, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0);
