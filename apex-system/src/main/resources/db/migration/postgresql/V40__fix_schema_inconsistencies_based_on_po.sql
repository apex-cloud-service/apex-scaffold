-- ============================================================
-- V40 - Fix database schema inconsistencies (based on PO code)
-- ============================================================
-- Purpose: Align database schema with PO entity classes
-- Based on: Database field consistency verification report 2026-09-17
-- Principle: Code implementation (PO classes) is the source of truth
-- PostgreSQL Version
-- ============================================================

-- ============================================================
-- 1. Fix oauth2_authorization_consent table structure
-- ============================================================
-- Problem: PO uses single 'id' primary key, but DB uses composite key
-- Solution: Add id column and adjust primary key strategy

ALTER TABLE oauth2_authorization_consent
    DROP CONSTRAINT oauth2_authorization_consent_pkey;

ALTER TABLE oauth2_authorization_consent
    ADD COLUMN id VARCHAR(100) NOT NULL,
    ADD COLUMN created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- Add primary key on id
ALTER TABLE oauth2_authorization_consent
    ADD PRIMARY KEY (id);

-- Keep original composite key as unique index
CREATE UNIQUE INDEX uk_client_principal ON oauth2_authorization_consent (registered_client_id, principal_name);

-- Add comments
COMMENT ON COLUMN oauth2_authorization_consent.id IS '主键ID';
COMMENT ON COLUMN oauth2_authorization_consent.created_at IS '创建时间';
COMMENT ON COLUMN oauth2_authorization_consent.updated_at IS '更新时间';

-- ============================================================
-- 2. Fix wf_workflow_definition table structure
-- ============================================================
-- Problem: Multiple field name and type mismatches
-- Solution: Rename columns and adjust types to match PO

-- Rename node_config_json to node_definition
ALTER TABLE wf_workflow_definition
    RENAME COLUMN node_config_json TO node_definition;

COMMENT ON COLUMN wf_workflow_definition.node_definition IS '节点定义JSON';

-- Rename status to enabled
ALTER TABLE wf_workflow_definition
    RENAME COLUMN status TO enabled;

COMMENT ON COLUMN wf_workflow_definition.enabled IS '是否启用';

-- ============================================================
-- 3. Fix wf_process_instance table structure
-- ============================================================
-- Problem: context_json should be form_data
-- Solution: Rename column to match PO

-- Rename context_json to form_data
ALTER TABLE wf_process_instance
    RENAME COLUMN context_json TO form_data;

COMMENT ON COLUMN wf_process_instance.form_data IS '表单数据JSON';

-- ============================================================
-- 4. Fix office_message table structure
-- ============================================================
-- Problem: Multiple field name mismatches
-- Solution: Rename columns to match PO

-- Add title column (missing in V31)
ALTER TABLE office_message
    ADD COLUMN title VARCHAR(500);

COMMENT ON COLUMN office_message.title IS '消息标题';

-- Rename recipient_type to target_type
ALTER TABLE office_message
    RENAME COLUMN recipient_type TO target_type;

COMMENT ON COLUMN office_message.target_type IS '目标类型';

-- Rename recipient_ids to target_user_ids
ALTER TABLE office_message
    RENAME COLUMN recipient_ids TO target_user_ids;

COMMENT ON COLUMN office_message.target_user_ids IS '目标用户ID列表';

-- Rename status to send_status
ALTER TABLE office_message
    RENAME COLUMN status TO send_status;

COMMENT ON COLUMN office_message.send_status IS '发送状态';

-- Rename result_message to failure_reason
ALTER TABLE office_message
    RENAME COLUMN result_message TO failure_reason;

COMMENT ON COLUMN office_message.failure_reason IS '失败原因';

-- ============================================================
-- End of V40 migration
-- ============================================================
