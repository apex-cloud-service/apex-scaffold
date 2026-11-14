-- 脚本用途：补充操作审计日志的查询权限与管理后台菜单入口。
-- 业务背景：打通审计日志落库、授权控制和后台查询页面的使用闭环。
-- 前置依赖：V1 已创建目标表，V2 权限及菜单种子已完成，相关固定 ID 未被占用。
-- 影响范围：向 sys_permission 与 sys_menu 写入固定种子记录，不变更表结构。
-- 幂等性：普通 INSERT 非幂等，重复执行可能因主键或唯一键冲突失败。
-- MySQL/Flyway 兼容性：使用 MySQL 8.x 常规 DML，由 Flyway V4 按版本顺序执行。
-- 数据风险：权限码或菜单配置错误会造成越权、拒绝访问或导航异常。
-- 运维注意：执行前核对 ID 与权限码，执行后验证审计页面授权及菜单可见性。
-- 迁移元信息：版本 V4；执行阶段为审计能力上线后的权限与菜单补种阶段。
-- Checksum 提示：修改已执行迁移的注释也会改变 Flyway checksum；存量环境需评估后 repair，禁止盲目重跑。
-- ================================================================
-- Apex Scaffold V4 迁移：审计日志查询权限与菜单
-- ================================================================
-- 说明：为操作审计日志（sys_operation_log 表）补充查询权限码与菜单项，
--      支撑管理后台「系统监控-审计日志」页面（C2 审计落库闭环）。
-- 依赖：V2 种子迁移（权限码 2001-2024、菜单 1000-1006 已存在）
-- 修复（2026-08-27）：sys_menu 列名对齐 V1 表结构（menu_name/perms），
--      列序对齐 V2 写法，menu_type=1（菜单页），sort=7（接登录日志 6）。
-- ================================================================

INSERT INTO sys_permission (id, name, code, description, type, status, create_time, update_time, version, deleted) VALUES
(2025, '审计日志列表', 'system:audit-log:list', '查询操作审计日志', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2026, '审计日志详情', 'system:audit-log:query', '查询操作审计日志详情', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time, version, deleted) VALUES
(1007, 1000, '审计日志', 1, 'system/audit-log', 'system/audit-log/index', 'system:audit-log:list', 'Audit', 7, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0);
