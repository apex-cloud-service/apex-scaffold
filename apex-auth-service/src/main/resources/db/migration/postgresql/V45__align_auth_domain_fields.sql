-- ============================================================
-- V45: 对齐认证域字段（PostgreSQL）
-- 变更内容：为 auth_permission、auth_role、auth_session、auth_audit_log
--           补充审计、软删除与乐观锁等字段；调整 auth_role.data_scope
--           字段类型；回填审计日志的 event_time 字段。
-- ============================================================
ALTER TABLE auth_permission ADD COLUMN IF NOT EXISTS resource_type VARCHAR(64);
ALTER TABLE auth_permission ADD COLUMN IF NOT EXISTS created_by BIGINT;
ALTER TABLE auth_permission ADD COLUMN IF NOT EXISTS updated_by BIGINT;
ALTER TABLE auth_permission ADD COLUMN IF NOT EXISTS deleted SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE auth_permission ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 0;
ALTER TABLE auth_role ADD COLUMN IF NOT EXISTS sort_order INT NOT NULL DEFAULT 0;
ALTER TABLE auth_role ADD COLUMN IF NOT EXISTS created_by BIGINT;
ALTER TABLE auth_role ADD COLUMN IF NOT EXISTS updated_by BIGINT;
ALTER TABLE auth_role ADD COLUMN IF NOT EXISTS deleted SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE auth_role ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 0;
ALTER TABLE auth_session ADD COLUMN IF NOT EXISTS token_id BIGINT;
ALTER TABLE auth_session ADD COLUMN IF NOT EXISTS device_name VARCHAR(255);
ALTER TABLE auth_session ADD COLUMN IF NOT EXISTS location VARCHAR(255);
ALTER TABLE auth_session ADD COLUMN IF NOT EXISTS status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE auth_session ADD COLUMN IF NOT EXISTS metadata TEXT;
ALTER TABLE auth_session ADD COLUMN IF NOT EXISTS deleted SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE auth_session ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 0;
ALTER TABLE auth_audit_log ADD COLUMN IF NOT EXISTS username VARCHAR(255);
ALTER TABLE auth_audit_log ADD COLUMN IF NOT EXISTS resource_type VARCHAR(64);
ALTER TABLE auth_audit_log ADD COLUMN IF NOT EXISTS resource_id VARCHAR(255);
ALTER TABLE auth_audit_log ADD COLUMN IF NOT EXISTS action VARCHAR(64);
ALTER TABLE auth_audit_log ADD COLUMN IF NOT EXISTS location VARCHAR(255);
ALTER TABLE auth_audit_log ADD COLUMN IF NOT EXISTS risk_level VARCHAR(32);
ALTER TABLE auth_audit_log ADD COLUMN IF NOT EXISTS event_time TIMESTAMP NULL;
ALTER TABLE auth_role ALTER COLUMN data_scope TYPE VARCHAR(32) USING data_scope::VARCHAR(32);
UPDATE auth_audit_log SET event_time = created_at WHERE event_time IS NULL;
