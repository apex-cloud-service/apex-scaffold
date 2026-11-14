-- V40 CHANGE COLUMN 遗失了原有 NOT NULL。存量 NULL 必须由业务确认后修复，禁止猜测回填。
-- 在任何持久 DDL 前检查，避免 MySQL 隐式提交造成部分升级。
CREATE TEMPORARY TABLE v62_null_preflight (
    null_count BIGINT NOT NULL,
    CONSTRAINT v62_requires_non_null_message_and_workflow CHECK (null_count = 0)
);
INSERT INTO v62_null_preflight
SELECT COUNT(*) FROM office_message WHERE send_status IS NULL OR target_user_ids IS NULL
UNION ALL
SELECT COUNT(*) FROM wf_workflow_definition WHERE enabled IS NULL OR node_definition IS NULL;
DROP TEMPORARY TABLE v62_null_preflight;

ALTER TABLE office_message
    MODIFY COLUMN send_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    MODIFY COLUMN target_user_ids TEXT NOT NULL;
-- V20 的状态默认 DRAFT；定义需要显式发布，不能将新定义自动置为 ACTIVE。
-- 仅修改默认值，保留所有存量 ACTIVE/DRAFT 等状态。
ALTER TABLE wf_workflow_definition
    MODIFY COLUMN enabled VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    MODIFY COLUMN node_definition TEXT NOT NULL;
-- 与 PG V42 一致；存量行的默认时间是本次结构升级时间，不宣称历史最后更新时间。
ALTER TABLE off_sync_history ADD COLUMN update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- V43 在 MySQL V57 建表之前跳过的索引；已有相同键的索引不再补建。
CREATE INDEX idx_v62_department_parent_status_sort ON off_department(parent_id, status, sort_order);
CREATE INDEX idx_v62_employee_dept_status ON off_employee(dept_id, status);
-- uk_off_employee_code_tenant(employee_code,tenant_id,deleted) 已覆盖编码/租户查找。
CREATE INDEX idx_v62_template_code_enabled_tenant ON off_message_template(template_code, enabled, tenant_id);
CREATE INDEX idx_v62_sync_source_start ON off_sync_history(sync_source, start_time);
-- PG 的 outbox(status,next_attempt_at) 是部分索引；MySQL 已有同前缀三列调度索引，无需副本。
