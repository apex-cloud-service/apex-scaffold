-- ================================================================
-- Apex Scaffold V5 迁移：在线用户查询权限
-- ================================================================
-- 说明：为「在线用户/会话管理」补充查询权限码（C4），支撑管理后台
--      在线用户列表页面。在线判定基于 sys_user.last_heartbeat_time
--      （登录与活跃请求更新，超过 5 分钟视为离线）。
-- 依赖：V2 种子迁移（权限码 1000-2024、V4 已用 2025-2026）
-- ================================================================

INSERT INTO `sys_permission` (`id`, `name`, `code`, `description`, `type`, `status`, `create_time`, `update_time`, `version`, `deleted`) VALUES
(2027, '在线用户列表', 'system:online-user:list', '查询在线用户（会话管理）', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0);
