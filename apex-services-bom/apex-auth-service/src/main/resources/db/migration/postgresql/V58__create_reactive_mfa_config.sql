-- R2DBC MfaConfig uses a separate table from the MyBatis auth_mfa_config model.
-- Preserve compatible manually provisioned tables and their ciphertext; no system data is copied.
CREATE TABLE IF NOT EXISTS mfa_config (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    secret TEXT,
    backup_codes TEXT,
    device_info VARCHAR(255),
    last_verified_at TIMESTAMP,
    failed_attempts INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT uk_mfa_config_tenant_user_type UNIQUE (tenant_id, user_id, type)
);
CREATE INDEX IF NOT EXISTS idx_mfa_config_user ON mfa_config (user_id);
