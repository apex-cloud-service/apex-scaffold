-- ============================================================
-- Apex Scaffold - 工作流服务 V3：租户唯一键与乐观锁版本列（PostgreSQL）
-- ============================================================
-- 【背景】
--   V1 基线脚本按旧单体语义创建唯一约束，未包含 tenant_id；
--   同时 wf_task / wf_process_instance 缺少 BasePO 的 version 列。
--   多租户改造后同一业务键可跨租户重复，而写入路径（TaskRepositoryImpl、
--   ProcessInstanceRepositoryImpl）会显式写入 version，缺失列将直接导致 SQL 失败。
--
-- 【变更】
--   1. wf_task、wf_process_instance 增加 version INT NOT NULL DEFAULT 0（乐观锁版本）。
--   2. wf_workflow_definition 唯一约束改为 (tenant_id, workflow_code, version, deleted)。
--   3. wf_process_instance 唯一约束改为 (tenant_id, business_key, deleted)。
--   wf_approval_record 只追加记录、无并发更新语义，不增加 version 列
--   （ApprovalRecordPO 使用 @TableName(excludeProperty = "version")）。
--
-- 【说明】
--   历史迁移 V1/V2 不修改；本脚本仅做增量 ALTER，可对已有数据执行
--   （新增 version 列默认 0，唯一约束收紧在既有数据上需保证无跨租户重复键）。
--
-- @author Apex Team
-- @version 1.0.0
-- @since 1.0.0
-- ============================================================

-- 1. 乐观锁版本列（BasePO.version 参与 INSERT/UPDATE，必须真实存在）
ALTER TABLE wf_process_instance
    ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 0;

ALTER TABLE wf_task
    ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 0;

COMMENT ON COLUMN wf_process_instance.version IS '乐观锁版本号';
COMMENT ON COLUMN wf_task.version IS '乐观锁版本号';

-- 2. 定义唯一约束纳入租户：不同租户可使用相同 workflow_code/version
ALTER TABLE wf_workflow_definition DROP CONSTRAINT uk_wf_def_code_version;
ALTER TABLE wf_workflow_definition
    ADD CONSTRAINT uk_wf_def_tenant_code_version
    UNIQUE (tenant_id, workflow_code, version, deleted);

-- 3. 实例唯一约束纳入租户：不同租户可使用相同业务主键
ALTER TABLE wf_process_instance DROP CONSTRAINT uk_wf_instance_business_key;
ALTER TABLE wf_process_instance
    ADD CONSTRAINT uk_wf_instance_tenant_business_key
    UNIQUE (tenant_id, business_key, deleted);
