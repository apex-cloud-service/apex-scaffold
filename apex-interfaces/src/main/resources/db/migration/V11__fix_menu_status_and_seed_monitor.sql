-- 脚本用途：统一菜单状态语义并初始化监控中心目录、页面与权限关联。
-- 业务背景：修复历史种子状态与枚举定义相反的问题，并接入监控后台能力。
-- 前置依赖：V1-V10 已完成，相关菜单、权限、角色及关联表存在。
-- 影响范围：批量更新现有菜单状态，新增或更新监控菜单和权限，并调整菜单层级。
-- 幂等性：采用 UPDATE、ON DUPLICATE KEY UPDATE 和条件插入，设计为可重复执行。
-- MySQL/Flyway 兼容性：使用 MySQL 8.x DML，由 Flyway V11 按版本顺序执行。
-- 数据风险：批量更新会改变后台菜单可见性和层级；错误配置可能导致授权或路由异常。
-- 运维注意：执行前留存菜单配置，执行后验证菜单树、监控权限和前端组件映射。
-- 迁移元信息：版本 V11；执行阶段为菜单语义修复与监控中心上线阶段。
-- Checksum 提示：修改已执行迁移的注释也会改变 Flyway checksum；存量环境需评估后 repair，禁止盲目重跑。
-- ============================================================
-- V11: 修复菜单状态语义 + 种子监控中心菜单
-- ============================================================
-- 背景：
--   1. 后端 MenuState 枚举定义为 status: 0=停用 1=正常；MenuVisible 枚举为
--      visible: 0=隐藏 1=显示。但 V2/V8 种子数据将全部菜单写入 status=0、
--      visible=0，与枚举语义相反，导致「启停菜单」接口与前端状态展示不一致。
--   2. 新增监控大盘聚合接口（/admin-api/v1/monitor/*，权限 system:monitor:view），
--      需在菜单树中补充「监控中心」目录与「监控大盘」页面，
--      并将登录日志/审计日志/在线用户归入监控中心。
-- 本脚本幂等可重复执行。
-- ============================================================

-- 1. 统一既有目录/菜单状态：status=1 正常、visible=1 显示（与枚举语义对齐）
UPDATE sys_menu
SET status  = 1,
    visible = 1
WHERE menu_type IN (0, 1)
  AND deleted = 0;

-- 2. 新增「监控中心」目录（parent=0，目录 menu_type=0）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
VALUES (1100, 0, '监控中心', 0, 'monitor', NULL, NULL, 'Monitor', 11, 1, 1)
ON DUPLICATE KEY UPDATE menu_name = '监控中心', menu_type = 0, path = 'monitor', icon = 'Monitor',
                        sort = 11, visible = 1, status = 1;

-- 3. 新增「监控大盘」菜单（component 对应前端 VIEW_REGISTRY monitor/dashboard/index）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
VALUES (1101, 1100, '监控大盘', 1, 'dashboard', 'monitor/dashboard/index', 'system:monitor:view', 'DataLine', 1, 1, 1)
ON DUPLICATE KEY UPDATE menu_name = '监控大盘', parent_id = 1100, menu_type = 1, path = 'dashboard',
                        component = 'monitor/dashboard/index', perms = 'system:monitor:view',
                        icon = 'DataLine', sort = 1, visible = 1, status = 1;

-- 4. 将登录日志/审计日志/在线用户归入「监控中心」（同时保持启用与显示）
UPDATE sys_menu SET parent_id = 1100, status = 1, visible = 1 WHERE id IN (1006, 1007, 1012);

-- 5. 监控权限码 + 授予 ADMIN 角色（ROLE_ADMIN 已直接放行，此处补齐权限数据）
INSERT INTO sys_permission (id, name, code, description, type, status) VALUES
    (2050, '监控大盘', 'system:monitor:view', '查看系统监控大盘（性能/治理/业务指标）', 1, 1)
ON DUPLICATE KEY UPDATE name = '监控大盘', status = 1;

INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT 10050, 1, 2050
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE role_id = 1 AND permission_id = 2050);
