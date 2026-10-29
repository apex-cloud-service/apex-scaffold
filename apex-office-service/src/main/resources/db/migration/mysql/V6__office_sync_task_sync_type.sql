-- ============================================================
-- Apex Scaffold - 办公服务独立库 V6：同步任务按类型隔离（MySQL）
-- ============================================================
-- 【用途】
--   O06 真实组织同步与引导向导（T19）：office_sync_task 原唯一键
--   (tenant_id, platform) 导致部门与成员同步任务互相覆盖游标。
--   增加 sync_type 维度（DEPARTMENT/MEMBER），任务与检查点按
--   租户/平台/同步类型三维隔离，重试与断点续传互不干扰。
--
-- 【变更】
--   office_sync_task  新增 sync_type 列（存量行默认 DEPARTMENT）；
--                     唯一键 uk_office_sync 调整为
--                     (tenant_id, platform, sync_type)。
--
-- 【约束】
--   sync_type 取值 DEPARTMENT/MEMBER（应用层白名单校验）。
--
-- 【方言约定】
--   MySQL 统一使用 DATETIME，禁止 TIMESTAMP；字符集 utf8mb4。
--
-- @author Apex Team
-- @version 1.0.0
-- @since 1.0.0
-- ============================================================

ALTER TABLE `office_sync_task`
    ADD COLUMN `sync_type` VARCHAR(32) NOT NULL DEFAULT 'DEPARTMENT' COMMENT '同步类型（DEPARTMENT/MEMBER）' AFTER `platform`;

ALTER TABLE `office_sync_task` DROP INDEX `uk_office_sync`;
ALTER TABLE `office_sync_task` ADD UNIQUE KEY `uk_office_sync` (`tenant_id`, `platform`, `sync_type`);
