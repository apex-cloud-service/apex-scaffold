-- ============================================================
-- Apex Scaffold - 工作流服务独立库初始化脚本（PostgreSQL）
-- ============================================================
-- 【用途】
--   工作流服务（apex-workflow-service）独立数据库 apex_workflow 的 Flyway 基线脚本，
--   与旧单体的迁移版本链解耦，新库从 V1 重新开始（迁移方案 §9.3 规则）。
--
-- 【表清单】（迁移方案 Phase 3.3）
--   wf_workflow_definition  工作流定义表
--   wf_process_instance     流程实例表
--   wf_task                 流程任务表
--   wf_approval_record      审批记录表
--
-- 【字段来源原则】
--   以代码实现（PO 类）为唯一事实来源（对齐 apex-interfaces V40 迁移原则）。
--   列名与以下类字段严格一一对应（MyBatis-Plus map-underscore-to-camel-case）：
--     com.apex.scaffold.workflow.infrastructure.persistence.po.WorkflowDefinitionPO
--     com.apex.scaffold.workflow.infrastructure.persistence.po.ProcessInstancePO
--     com.apex.scaffold.workflow.infrastructure.persistence.po.TaskPO
--     com.apex.scaffold.workflow.infrastructure.persistence.po.ApprovalRecordPO
--   并包含 Mapper 原生 SQL 依赖的列（如 wf_task.timeout_handled）。
--
-- 【方言约定】
--   PostgreSQL 使用 TIMESTAMP 与 SMALLINT 逻辑删除/布尔字段。
--
-- @author Apex Team
-- @version 1.0.0
-- @since 1.0.0
-- ============================================================

-- ------------------------------------------------------------
-- 工作流定义表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wf_workflow_definition (
    id              BIGINT       NOT NULL,
    tenant_id       VARCHAR(64)  NOT NULL,
    workflow_code   VARCHAR(100) NOT NULL,
    workflow_name   VARCHAR(200) NOT NULL,
    description     VARCHAR(500),
    node_definition TEXT,
    version         INT          NOT NULL DEFAULT 1,
    enabled         INT          NOT NULL DEFAULT 1,
    remark          VARCHAR(500),
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_wf_def_code_version UNIQUE (workflow_code, version, deleted)
);
CREATE INDEX IF NOT EXISTS idx_wf_def_tenant ON wf_workflow_definition (tenant_id);
CREATE INDEX IF NOT EXISTS idx_wf_def_enabled ON wf_workflow_definition (enabled);
CREATE INDEX IF NOT EXISTS idx_wf_def_update_time ON wf_workflow_definition (update_time);

COMMENT ON TABLE  wf_workflow_definition IS '工作流定义表';
COMMENT ON COLUMN wf_workflow_definition.node_definition IS '节点定义JSON（DAG）';
COMMENT ON COLUMN wf_workflow_definition.enabled IS '启用标志（1-启用 0-停用）';

-- ------------------------------------------------------------
-- 流程实例表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wf_process_instance (
    id                     BIGINT       NOT NULL,
    tenant_id              VARCHAR(64)  NOT NULL,
    workflow_definition_id BIGINT       NOT NULL,
    business_key           VARCHAR(200) NOT NULL,
    current_node_key       VARCHAR(100),
    status                 VARCHAR(20)  NOT NULL DEFAULT 'RUNNING',
    initiator_id           BIGINT       NOT NULL,
    form_data              TEXT,
    start_time             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    end_time               TIMESTAMP,
    remark                 VARCHAR(500),
    suspended              SMALLINT     NOT NULL DEFAULT 0,
    create_time            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted                SMALLINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_wf_instance_business_key UNIQUE (business_key, deleted)
);
CREATE INDEX IF NOT EXISTS idx_wf_instance_def ON wf_process_instance (workflow_definition_id);
CREATE INDEX IF NOT EXISTS idx_wf_instance_tenant_initiator ON wf_process_instance (tenant_id, initiator_id);
CREATE INDEX IF NOT EXISTS idx_wf_instance_status ON wf_process_instance (status);
CREATE INDEX IF NOT EXISTS idx_wf_instance_suspended ON wf_process_instance (suspended);
CREATE INDEX IF NOT EXISTS idx_wf_instance_start_time ON wf_process_instance (start_time);
CREATE INDEX IF NOT EXISTS idx_wf_instance_end_time ON wf_process_instance (end_time);

COMMENT ON TABLE  wf_process_instance IS '流程实例表';
COMMENT ON COLUMN wf_process_instance.form_data IS '表单数据JSON（流程变量）';
COMMENT ON COLUMN wf_process_instance.suspended IS '是否挂起（1-是 0-否）';

-- ------------------------------------------------------------
-- 流程任务表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wf_task (
    id                  BIGINT       NOT NULL,
    tenant_id           VARCHAR(64)  NOT NULL,
    process_instance_id BIGINT       NOT NULL,
    node_code           VARCHAR(100) NOT NULL,
    task_name           VARCHAR(200) NOT NULL,
    assignee_id         BIGINT       NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    due_time            TIMESTAMP,
    complete_time       TIMESTAMP,
    remark              VARCHAR(500),
    timeout_at          TIMESTAMP,
    timeout_strategy    VARCHAR(50),
    timeout_handled     SMALLINT     NOT NULL DEFAULT 0,
    transferred_from    BIGINT,
    create_time         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_wf_task_instance ON wf_task (process_instance_id);
CREATE INDEX IF NOT EXISTS idx_wf_task_tenant_assignee_status ON wf_task (tenant_id, assignee_id, status);
CREATE INDEX IF NOT EXISTS idx_wf_task_status_assignee ON wf_task (status, assignee_id);
CREATE INDEX IF NOT EXISTS idx_wf_task_due_time ON wf_task (due_time);
CREATE INDEX IF NOT EXISTS idx_wf_task_timeout ON wf_task (timeout_at, timeout_handled);
CREATE INDEX IF NOT EXISTS idx_wf_task_transferred_from ON wf_task (transferred_from);
CREATE INDEX IF NOT EXISTS idx_wf_task_create_time ON wf_task (create_time);

COMMENT ON TABLE  wf_task IS '流程任务表';
COMMENT ON COLUMN wf_task.node_code IS '节点编码';
COMMENT ON COLUMN wf_task.timeout_handled IS '是否已处理超时（1-是 0-否）';

-- ------------------------------------------------------------
-- 审批记录表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wf_approval_record (
    id                  BIGINT       NOT NULL,
    tenant_id           VARCHAR(64)  NOT NULL,
    process_instance_id BIGINT       NOT NULL,
    task_id             BIGINT       NOT NULL,
    node_code           VARCHAR(100),
    approver_id         BIGINT,
    action              VARCHAR(20),
    opinion             VARCHAR(1000),
    remark              VARCHAR(500),
    create_time         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_wf_approval_instance ON wf_approval_record (process_instance_id);
CREATE INDEX IF NOT EXISTS idx_wf_approval_task ON wf_approval_record (task_id);
CREATE INDEX IF NOT EXISTS idx_wf_approval_tenant_approver ON wf_approval_record (tenant_id, approver_id);
CREATE INDEX IF NOT EXISTS idx_wf_approval_create_time ON wf_approval_record (create_time);

COMMENT ON TABLE  wf_approval_record IS '审批记录表';
COMMENT ON COLUMN wf_approval_record.opinion IS '处理意见';
