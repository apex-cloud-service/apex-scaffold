-- PostgreSQL tenant isolation columns and indexes.

DO $$
BEGIN
    IF to_regclass('biz_credit_tx') IS NOT NULL THEN
        ALTER TABLE biz_credit_tx ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) DEFAULT NULL;
        CREATE INDEX IF NOT EXISTS idx_biz_credit_tx_tenant_id ON biz_credit_tx (tenant_id);
    END IF;
END $$;

DO $$
BEGIN
    IF to_regclass('sys_operation_log') IS NOT NULL THEN
        ALTER TABLE sys_operation_log ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) DEFAULT NULL;
        CREATE INDEX IF NOT EXISTS idx_v15_sys_operation_log_tenant_id ON sys_operation_log (tenant_id);
    END IF;
END $$;

DO $$
BEGIN
    IF to_regclass('biz_sample') IS NOT NULL THEN
        ALTER TABLE biz_sample ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) DEFAULT NULL;
        CREATE INDEX IF NOT EXISTS idx_biz_sample_tenant_id ON biz_sample (tenant_id);
    END IF;
END $$;

DO $$
BEGIN
    IF to_regclass('biz_system_message') IS NOT NULL THEN
        ALTER TABLE biz_system_message ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) DEFAULT NULL;
        CREATE INDEX IF NOT EXISTS idx_v15_biz_system_message_tenant_id ON biz_system_message (tenant_id);
    END IF;
END $$;

DO $$
BEGIN
    IF to_regclass('sys_user_role') IS NOT NULL THEN
        ALTER TABLE sys_user_role ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) DEFAULT NULL;
        CREATE INDEX IF NOT EXISTS idx_v15_user_role_tenant_id ON sys_user_role (tenant_id);
    END IF;
END $$;

DO $$
BEGIN
    IF to_regclass('sys_account_deletion_task') IS NOT NULL THEN
        ALTER TABLE sys_account_deletion_task ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) DEFAULT NULL;
        CREATE INDEX IF NOT EXISTS idx_sys_account_deletion_task_tenant_id ON sys_account_deletion_task (tenant_id);
    END IF;
END $$;

DO $$
BEGIN
    IF to_regclass('biz_credit_account') IS NOT NULL THEN
        ALTER TABLE biz_credit_account ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) DEFAULT NULL;
        CREATE INDEX IF NOT EXISTS idx_biz_credit_account_tenant_id ON biz_credit_account (tenant_id);
    END IF;
END $$;

DO $$
BEGIN
    IF to_regclass('biz_user_consent') IS NOT NULL THEN
        ALTER TABLE biz_user_consent ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) DEFAULT NULL;
        CREATE INDEX IF NOT EXISTS idx_biz_user_consent_tenant_id ON biz_user_consent (tenant_id);
    END IF;
END $$;

DO $$
BEGIN
    IF to_regclass('sys_api_call_log') IS NOT NULL THEN
        ALTER TABLE sys_api_call_log ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) DEFAULT NULL;
        CREATE INDEX IF NOT EXISTS idx_sys_api_call_log_tenant_id ON sys_api_call_log (tenant_id);
    END IF;
END $$;

DO $$
BEGIN
    IF to_regclass('biz_content_moderation_review') IS NOT NULL THEN
        ALTER TABLE biz_content_moderation_review ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) DEFAULT NULL;
        CREATE INDEX IF NOT EXISTS idx_biz_content_moderation_review_tenant_id ON biz_content_moderation_review (tenant_id);
    END IF;
END $$;

DO $$
BEGIN
    IF to_regclass('biz_quiz_question') IS NOT NULL THEN
        ALTER TABLE biz_quiz_question ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) DEFAULT NULL;
        CREATE INDEX IF NOT EXISTS idx_biz_quiz_question_tenant_id ON biz_quiz_question (tenant_id);
    END IF;
END $$;

DO $$
BEGIN
    IF to_regclass('biz_report_ticket') IS NOT NULL THEN
        ALTER TABLE biz_report_ticket ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) DEFAULT NULL;
        CREATE INDEX IF NOT EXISTS idx_biz_report_ticket_tenant_id ON biz_report_ticket (tenant_id);
    END IF;
END $$;

DO $$
BEGIN
    IF to_regclass('sys_user') IS NOT NULL THEN
        ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) DEFAULT NULL;
        CREATE INDEX IF NOT EXISTS idx_v15_sys_user_tenant_id ON sys_user (tenant_id);
    END IF;
END $$;

DO $$
BEGIN
    IF to_regclass('biz_user_compliance') IS NOT NULL THEN
        ALTER TABLE biz_user_compliance ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) DEFAULT NULL;
        CREATE INDEX IF NOT EXISTS idx_biz_user_compliance_tenant_id ON biz_user_compliance (tenant_id);
    END IF;
END $$;

DO $$
BEGIN
    IF to_regclass('biz_policy_version') IS NOT NULL THEN
        ALTER TABLE biz_policy_version ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) DEFAULT NULL;
        CREATE INDEX IF NOT EXISTS idx_biz_policy_version_tenant_id ON biz_policy_version (tenant_id);
    END IF;
END $$;

DO $$
BEGIN
    IF to_regclass('biz_subscription') IS NOT NULL THEN
        ALTER TABLE biz_subscription ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) DEFAULT NULL;
        CREATE INDEX IF NOT EXISTS idx_biz_subscription_tenant_id ON biz_subscription (tenant_id);
    END IF;
END $$;

DO $$
BEGIN
    IF to_regclass('biz_blacklist') IS NOT NULL THEN
        ALTER TABLE biz_blacklist ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) DEFAULT NULL;
        CREATE INDEX IF NOT EXISTS idx_biz_blacklist_tenant_id ON biz_blacklist (tenant_id);
    END IF;
END $$;

DO $$
BEGIN
    IF to_regclass('biz_user_profile') IS NOT NULL THEN
        ALTER TABLE biz_user_profile ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) DEFAULT NULL;
        CREATE INDEX IF NOT EXISTS idx_biz_user_profile_tenant_id ON biz_user_profile (tenant_id);
    END IF;
END $$;

DO $$
BEGIN
    IF to_regclass('biz_admin_review_reason') IS NOT NULL THEN
        ALTER TABLE biz_admin_review_reason ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) DEFAULT NULL;
        CREATE INDEX IF NOT EXISTS idx_biz_admin_review_reason_tenant_id ON biz_admin_review_reason (tenant_id);
    END IF;
END $$;

DO $$
BEGIN
    IF to_regclass('biz_report') IS NOT NULL THEN
        ALTER TABLE biz_report ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) DEFAULT NULL;
        CREATE INDEX IF NOT EXISTS idx_biz_report_tenant_id ON biz_report (tenant_id);
    END IF;
END $$;

DO $$
BEGIN
    IF to_regclass('sys_file') IS NOT NULL THEN
        ALTER TABLE sys_file ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) DEFAULT NULL;
        CREATE INDEX IF NOT EXISTS idx_sys_file_tenant_id ON sys_file (tenant_id);
    END IF;
END $$;

DO $$
BEGIN
    IF to_regclass('sys_login_log') IS NOT NULL THEN
        ALTER TABLE sys_login_log ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) DEFAULT NULL;
        CREATE INDEX IF NOT EXISTS idx_sys_login_log_tenant_id ON sys_login_log (tenant_id);
    END IF;
END $$;

DO $$
BEGIN
    IF to_regclass('biz_admin_review_audit') IS NOT NULL THEN
        ALTER TABLE biz_admin_review_audit ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) DEFAULT NULL;
        CREATE INDEX IF NOT EXISTS idx_biz_admin_review_audit_tenant_id ON biz_admin_review_audit (tenant_id);
    END IF;
END $$;
