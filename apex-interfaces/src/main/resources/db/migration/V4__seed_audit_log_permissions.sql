-- ================================================================
-- Apex Scaffold V4 迁移：审计日志查询权限与菜单
-- ================================================================
-- 说明：为操作审计日志（audit_log 表）补充查询权限码与菜单项，
--      支撑管理后台「系统监控-审计日志」页面（C2 审计落库闭环）。
-- 依赖：V2 种子迁移（权限码 1000-2024、菜单 1000-1006 已存在）
-- ================================================================

INSERT INTO `sys_permission` (`id`, `name`, `code`, `description`, `type`, `status`, `create_time`, `update_time`, `version`, `deleted`) VALUES
(2025, '审计日志列表', 'system:audit-log:list', '查询操作审计日志', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2026, '审计日志详情', 'system:audit-log:query', '查询操作审计日志详情', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0);

INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `sort`, `path`, `component`, `permission`, `icon`, `menu_type`, `status`, `visible`, `create_time`, `update_time`, `version`, `deleted`) VALUES
(1007, 1000, '审计日志', 7, 'system/audit-log', 'system/audit-log/index', 'system:audit-log:list', 'Audit', 2, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0);
