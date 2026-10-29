-- R2DBC MfaConfig uses a separate table from the MyBatis auth_mfa_config model.
-- Preserve compatible manually provisioned tables and their ciphertext; no system data is copied.
CREATE TABLE IF NOT EXISTS mfa_config (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    secret TEXT,
    backup_codes TEXT,
    device_info VARCHAR(255),
    last_verified_at DATETIME,
    failed_attempts INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME,
    UNIQUE KEY uk_mfa_config_tenant_user_type (tenant_id, user_id, type),
    KEY idx_mfa_config_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
