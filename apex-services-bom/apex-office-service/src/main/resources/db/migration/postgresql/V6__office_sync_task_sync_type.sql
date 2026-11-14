-- ============================================================
-- Apex Scaffold - 办公服务独立库 V6：同步任务按类型隔离（PostgreSQL）
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
-- @author Apex Team
-- @version 1.0.0
-- @since 1.0.0
-- ============================================================

ALTER TABLE office_sync_task ADD COLUMN IF NOT EXISTS sync_type VARCHAR(32) NOT NULL DEFAULT 'DEPARTMENT';
COMMENT ON COLUMN office_sync_task.sync_type IS '同步类型（DEPARTMENT/MEMBER）';

DROP INDEX IF EXISTS uk_office_sync;
CREATE UNIQUE INDEX IF NOT EXISTS uk_office_sync ON office_sync_task (tenant_id, platform, sync_type);
