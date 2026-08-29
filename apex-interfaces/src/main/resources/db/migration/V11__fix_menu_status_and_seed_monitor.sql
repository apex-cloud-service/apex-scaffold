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
