-- 脚本用途：修复后台菜单路由配置并初始化管理员账号、角色及关联数据。
-- 业务背景：解决基础种子菜单不可路由、状态语义错误及系统无可登录管理员的问题。
-- 前置依赖：V1-V7 已完成，目标系统表存在，固定用户、角色、菜单及关联 ID 可用。
-- 影响范围：更新既有菜单并写入管理员相关种子数据，直接影响登录和后台授权。
-- 幂等性：主要使用 UPDATE、ON DUPLICATE KEY UPDATE 与 INSERT IGNORE，设计为可重复执行。
-- MySQL/Flyway 兼容性：使用 MySQL 8.x 特有幂等 DML，由 Flyway V8 顺序执行。
-- 数据风险：会覆盖目标菜单配置；内置账号及密码哈希属于敏感初始化数据，部署后须治理。
-- 运维注意：执行后立即修改或禁用默认凭据，并核对菜单路由、角色权限及关联记录。
-- 迁移元信息：版本 V8；执行阶段为管理后台可用性修复与管理员引导阶段。
-- Checksum 提示：修改已执行迁移的注释也会改变 Flyway checksum；存量环境需评估后 repair，禁止盲目重跑。
-- ============================================================
-- V8: 修复管理后台菜单数据 + 种子管理员账号
-- ============================================================
-- 背景：V1-V7 仅初始化了菜单/权限的基础数据，存在以下问题：
--   1. 系统管理目录 menu_type=1（应为 0 目录），component='system/index'
--      与前端动态路由注册表不匹配，导致整个菜单树无法生成路由；
--   2. 全部菜单 status=1（停用），会被前端动态路由过滤；
--   3. 子菜单 path 为绝对形式（system/user），与目录 path 拼接后路由错乱；
--   4. 登录日志/审计日志 component 与前端 VIEW_REGISTRY（monitor/*）不一致；
--   5. 无任何 sys_user / sys_role / 关联数据，无法登录且无管理员角色。
-- 本脚本修复菜单数据并补充管理员账号（admin / admin123），幂等可重复执行。
-- ============================================================

-- 1. 修复系统管理目录（menu_type 1→0 目录、清除无效 component、启用）
UPDATE sys_menu
SET menu_type = 0,
    component = NULL,
    perms     = NULL,
    status    = 0,
    visible   = 0,
    sort      = 1,
    icon      = COALESCE(NULLIF(icon, ''), 'Setting')
WHERE id = 1000;

-- 2. 修复子菜单：启用 + 相对路径（与目录 path 拼接）+ 组件与前端注册表对齐
UPDATE sys_menu SET status = 0, visible = 0, path = 'user',      perms = 'system:user:list', sort = 1, icon = COALESCE(NULLIF(icon, ''), 'User')           WHERE id = 1001;
UPDATE sys_menu SET status = 0, visible = 0, path = 'menu',      sort = 3, icon = COALESCE(NULLIF(icon, ''), 'Menu')            WHERE id = 1002;
UPDATE sys_menu SET status = 0, visible = 0, path = 'dict',      sort = 5, icon = COALESCE(NULLIF(icon, ''), 'Collection')      WHERE id = 1003;
UPDATE sys_menu SET status = 0, visible = 0, path = 'config',    sort = 6, icon = COALESCE(NULLIF(icon, ''), 'Tools')           WHERE id = 1004;
UPDATE sys_menu SET status = 0, visible = 0, path = 'job',       sort = 7, icon = COALESCE(NULLIF(icon, ''), 'Timer')           WHERE id = 1005;
UPDATE sys_menu SET status = 0, visible = 0, path = 'login-log', component = 'monitor/loginlog/index', sort = 8, icon = COALESCE(NULLIF(icon, ''), 'Document') WHERE id = 1006;
UPDATE sys_menu SET status = 0, visible = 0, path = 'audit-log', component = 'monitor/audit/index',    sort = 9, icon = COALESCE(NULLIF(icon, ''), 'View')     WHERE id = 1007;
UPDATE sys_menu SET status = 0, visible = 0, path = 'dept',      sort = 4, icon = COALESCE(NULLIF(icon, ''), 'OfficeBuilding')  WHERE id = 1008;

-- 3. 补充角色管理 / 在线用户菜单（前端视图已注册，后端此前缺菜单项）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
VALUES (1011, 1000, '角色管理', 1, 'role', 'system/role/index', 'system:role:list', 'UserFilled', 2, 0, 0)
ON DUPLICATE KEY UPDATE menu_name = '角色管理', parent_id = 1000, menu_type = 1, path = 'role',
                        component = 'system/role/index', perms = 'system:role:list', status = 0, visible = 0, sort = 2;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
VALUES (1012, 1000, '在线用户', 1, 'online', 'monitor/online/index', 'system:online-user:list', 'Monitor', 10, 0, 0)
ON DUPLICATE KEY UPDATE menu_name = '在线用户', parent_id = 1000, menu_type = 1, path = 'online',
                        component = 'monitor/online/index', perms = 'system:online-user:list', status = 0, visible = 0, sort = 10;

-- 4. 补充用户管理 / 角色管理权限码（与前端 v-permission 按钮权限码对齐）
INSERT INTO sys_permission (id, name, code, description, type, status) VALUES
    (2033, '用户列表', 'system:user:list',   '查询后台用户列表', 1, 1),
    (2034, '用户查询', 'system:user:query',  '查询后台用户详情', 1, 1),
    (2035, '用户新增', 'system:user:create', '创建后台用户',     1, 1),
    (2036, '用户编辑', 'system:user:update', '更新后台用户',     1, 1),
    (2037, '用户删除', 'system:user:remove', '删除后台用户',     1, 1),
    (2038, '角色列表', 'system:role:list',   '查询后台角色列表', 1, 1),
    (2039, '角色新增', 'system:role:create', '创建后台角色',     1, 1),
    (2040, '角色编辑', 'system:role:update', '更新后台角色',     1, 1),
    (2041, '角色删除', 'system:role:remove', '删除后台角色',     1, 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), status = 1;

-- 5. 种子角色：超级管理员（ADMIN，对应 SecurityProperties.adminRoles 默认值）
INSERT INTO sys_role (id, name, code, description, status)
VALUES (1, '超级管理员', 'ADMIN', '系统内置超级管理员角色，拥有全部管理权限', 1)
ON DUPLICATE KEY UPDATE name = '超级管理员', status = 1;

-- 6. 种子用户：admin / admin123（BCrypt，open_id 作为 JWT subject，鉴权链路必需）
INSERT INTO sys_user (id, open_id, username, dept_id, password, nick_name, gender, status, version)
VALUES (1, 'admin-openid', 'admin', 1, '$2a$10$3WRG9DszA2YX58nDBTKpFON71ZbLSkJUQrAWnyfaavl39OqBUPITW', '系统管理员', 0, 1, 0)
ON DUPLICATE KEY UPDATE password = VALUES(password), status = 1, nick_name = '系统管理员';

-- 7. 用户-角色关联（表无唯一键且 id 非自增，先清后插保证幂等）
DELETE FROM sys_user_role WHERE user_id = 1;
INSERT INTO sys_user_role (id, user_id, role_id) VALUES (1, 1, 1);

-- 8. ADMIN 角色授予全部可用权限（表无唯一键且 id 非自增，先清后插保证幂等）
DELETE FROM sys_role_permission WHERE role_id = 1;
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT ROW_NUMBER() OVER (ORDER BY id), 1, id FROM sys_permission WHERE status = 1 AND deleted = 0;
