-- ============================================================
-- Apex Scaffold - 工作流服务 V5：流程定义生命周期字段（PostgreSQL）
-- ============================================================
-- 【背景】
--   O03 需要区分草稿、已发布与停用状态，并记录发布审计信息。
--   V1 基线只有 enabled 启停标志，无法表达“未发布不可发起”。
--
-- 【变更】
--   1. wf_workflow_definition 增加 status VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED'。
--      历史数据继续视为已发布，保持既有发起行为不变。
--   2. 增加 published_at / published_by 记录发布时刻与发布人。
--   3. 增加 status 索引，供按状态筛选与“同 code 取最新已发布版本”使用。
--
-- 【说明】
--   历史迁移 V1–V4 不修改；本脚本仅做增量 ALTER。
--
-- @author Apex Team
-- @version 1.0.0
-- @since 1.0.0
-- ============================================================

ALTER TABLE wf_workflow_definition
    ADD COLUMN IF NOT EXISTS status VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED',
    ADD COLUMN IF NOT EXISTS published_at TIMESTAMP DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS published_by BIGINT DEFAULT NULL;

COMMENT ON COLUMN wf_workflow_definition.status IS '生命周期状态（DRAFT/PUBLISHED/DISABLED）';
COMMENT ON COLUMN wf_workflow_definition.published_at IS '发布时刻';
COMMENT ON COLUMN wf_workflow_definition.published_by IS '发布人用户ID';

CREATE INDEX IF NOT EXISTS idx_wf_def_status ON wf_workflow_definition (status);
