-- PostgreSQL tenant isolation columns and indexes for job tables.
DO $$
BEGIN
    IF to_regclass('sys_job') IS NOT NULL THEN
        ALTER TABLE sys_job ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) DEFAULT NULL;
        CREATE INDEX IF NOT EXISTS idx_job_tenant_id ON sys_job (tenant_id);
    END IF;
END $$;
DO $$
BEGIN
    IF to_regclass('sys_job') IS NOT NULL THEN
        ALTER TABLE sys_job ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) DEFAULT NULL;
        CREATE INDEX IF NOT EXISTS idx_job_tenant_status ON sys_job (tenant_id, status);
    END IF;
END $$;
DO $$
BEGIN
    IF to_regclass('sys_job_log') IS NOT NULL THEN
        ALTER TABLE sys_job_log ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) DEFAULT NULL;
        CREATE INDEX IF NOT EXISTS idx_job_log_tenant_id ON sys_job_log (tenant_id);
    END IF;
END $$;
DO $$
BEGIN
    IF to_regclass('sys_job_log') IS NOT NULL THEN
        ALTER TABLE sys_job_log ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) DEFAULT NULL;
        CREATE INDEX IF NOT EXISTS idx_job_log_tenant_job_start ON sys_job_log (tenant_id, job_id, start_time);
    END IF;
END $$;
