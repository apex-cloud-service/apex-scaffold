-- PostgreSQL tenant isolation columns and indexes.
DO $$
BEGIN
    IF to_regclass('sys_user') IS NOT NULL THEN
        ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) DEFAULT NULL;
        CREATE INDEX IF NOT EXISTS idx_sys_user_tenant_id ON sys_user (tenant_id);
    END IF;
END $$;
DO $$
BEGIN
    IF to_regclass('sys_user_role') IS NOT NULL THEN
        ALTER TABLE sys_user_role ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) DEFAULT NULL;
        CREATE INDEX IF NOT EXISTS idx_user_role_tenant_id ON sys_user_role (tenant_id);
    END IF;
END $$;
DO $$
BEGIN
    IF to_regclass('sys_operation_log') IS NOT NULL THEN
        ALTER TABLE sys_operation_log ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) DEFAULT NULL;
        CREATE INDEX IF NOT EXISTS idx_sys_operation_log_tenant_id ON sys_operation_log (tenant_id);
    END IF;
END $$;
DO $$
BEGIN
    IF to_regclass('biz_system_message') IS NOT NULL THEN
        ALTER TABLE biz_system_message ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) DEFAULT NULL;
        CREATE INDEX IF NOT EXISTS idx_biz_system_message_tenant_id ON biz_system_message (tenant_id);
    END IF;
END $$;
