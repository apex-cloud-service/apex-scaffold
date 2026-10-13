-- ============================================================
-- Apex Scaffold - 工作流服务 basic Profile 初始化脚本（H2）
-- ============================================================
-- 【用途】
--   基础版（basic）单机环境使用 H2 内存库（MODE=MySQL）时，
--   通过 spring.sql.init 加载本脚本初始化表结构，替代 Flyway。
--   表结构与应用代码 PO 保持一致（与 db/migration 下 MySQL/PostgreSQL V1 脚本对齐）。
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
    node_definition CLOB,
    version         INT          NOT NULL DEFAULT 1,
    enabled         INT          NOT NULL DEFAULT 1,
    remark          VARCHAR(500),
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_wf_def_code_version UNIQUE (workflow_code, version, deleted)
);
CREATE INDEX IF NOT EXISTS idx_wf_def_tenant ON wf_workflow_definition (tenant_id);
CREATE INDEX IF NOT EXISTS idx_wf_def_enabled ON wf_workflow_definition (enabled);
CREATE INDEX IF NOT EXISTS idx_wf_def_update_time ON wf_workflow_definition (update_time);

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
    form_data              CLOB,
    start_time             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    end_time               TIMESTAMP,
    remark                 VARCHAR(500),
    suspended              TINYINT      NOT NULL DEFAULT 0,
    create_time            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted                TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_wf_instance_business_key UNIQUE (business_key, deleted)
);
CREATE INDEX IF NOT EXISTS idx_wf_instance_def ON wf_process_instance (workflow_definition_id);
CREATE INDEX IF NOT EXISTS idx_wf_instance_tenant_initiator ON wf_process_instance (tenant_id, initiator_id);
CREATE INDEX IF NOT EXISTS idx_wf_instance_status ON wf_process_instance (status);
CREATE INDEX IF NOT EXISTS idx_wf_instance_suspended ON wf_process_instance (suspended);
CREATE INDEX IF NOT EXISTS idx_wf_instance_start_time ON wf_process_instance (start_time);
CREATE INDEX IF NOT EXISTS idx_wf_instance_end_time ON wf_process_instance (end_time);

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
    timeout_handled     TINYINT      NOT NULL DEFAULT 0,
    transferred_from    BIGINT,
    create_time         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_wf_task_instance ON wf_task (process_instance_id);
CREATE INDEX IF NOT EXISTS idx_wf_task_tenant_assignee_status ON wf_task (tenant_id, assignee_id, status);
CREATE INDEX IF NOT EXISTS idx_wf_task_status_assignee ON wf_task (status, assignee_id);
CREATE INDEX IF NOT EXISTS idx_wf_task_due_time ON wf_task (due_time);
CREATE INDEX IF NOT EXISTS idx_wf_task_timeout ON wf_task (timeout_at, timeout_handled);
CREATE INDEX IF NOT EXISTS idx_wf_task_transferred_from ON wf_task (transferred_from);
CREATE INDEX IF NOT EXISTS idx_wf_task_create_time ON wf_task (create_time);

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
    deleted             TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_wf_approval_instance ON wf_approval_record (process_instance_id);
CREATE INDEX IF NOT EXISTS idx_wf_approval_task ON wf_approval_record (task_id);
CREATE INDEX IF NOT EXISTS idx_wf_approval_tenant_approver ON wf_approval_record (tenant_id, approver_id);
CREATE INDEX IF NOT EXISTS idx_wf_approval_create_time ON wf_approval_record (create_time);