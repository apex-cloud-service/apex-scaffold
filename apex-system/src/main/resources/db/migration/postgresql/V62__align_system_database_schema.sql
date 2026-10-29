-- 补齐 MySQL V27/V42/V48/V53/V55/V56/V58 的结构遗漏，不复制示例白名单或凭据种子。
ALTER TABLE sys_user
    ADD COLUMN is_locked BOOLEAN DEFAULT FALSE,
    ADD COLUMN lock_reason VARCHAR(255),
    ADD COLUMN locked_at TIMESTAMP,
    ADD COLUMN locked_until TIMESTAMP,
    ADD COLUMN failed_login_attempts INT DEFAULT 0,
    ADD COLUMN last_failed_login_at TIMESTAMP,
    ADD COLUMN password_changed_at TIMESTAMP,
    ADD COLUMN password_expires_at TIMESTAMP,
    ADD COLUMN must_change_password BOOLEAN DEFAULT FALSE;
-- 不根据创建时间猜测存量用户的密码修改时间。
ALTER TABLE sys_user ALTER COLUMN keycloak_sub TYPE VARCHAR(255);
CREATE INDEX idx_user_is_locked ON sys_user(is_locked);
CREATE INDEX idx_user_password_expires_at ON sys_user(password_expires_at);

ALTER TABLE oauth2_authorization
    ADD COLUMN access_token_hash VARCHAR(64),
    ADD COLUMN access_token_encrypted TEXT,
    ADD COLUMN access_token_iv VARCHAR(32),
    ADD COLUMN refresh_token_hash VARCHAR(64),
    ADD COLUMN refresh_token_encrypted TEXT,
    ADD COLUMN refresh_token_iv VARCHAR(32),
    ADD COLUMN token_family VARCHAR(64),
    ADD COLUMN previous_jti VARCHAR(100),
    ADD COLUMN rotation_count INT DEFAULT 0,
    ADD COLUMN device_id VARCHAR(255),
    ADD COLUMN device_fingerprint VARCHAR(255),
    ADD COLUMN client_ip VARCHAR(64),
    ADD COLUMN is_revoked BOOLEAN DEFAULT FALSE,
    ADD COLUMN last_used_ip VARCHAR(64),
    ADD COLUMN is_encrypted BOOLEAN DEFAULT TRUE,
    ADD COLUMN revoked_reason VARCHAR(100);
CREATE INDEX idx_access_token_hash ON oauth2_authorization(access_token_hash);
CREATE INDEX idx_refresh_token_hash ON oauth2_authorization(refresh_token_hash);
CREATE INDEX idx_token_family ON oauth2_authorization(token_family);
CREATE INDEX idx_oauth2_auth_device_id ON oauth2_authorization(device_id);
CREATE INDEX idx_oauth2_auth_is_revoked ON oauth2_authorization(is_revoked);
CREATE INDEX idx_v62_oauth_revoked_reason ON oauth2_authorization(revoked_reason);
CREATE INDEX idx_v62_oauth_last_used_at ON oauth2_authorization(last_used_at);
-- V20 的状态默认 DRAFT，保留需显式发布的状态语义，不改存量状态。
ALTER TABLE wf_workflow_definition ALTER COLUMN enabled SET DEFAULT 'DRAFT';
-- 对齐 MySQL 租户/部门外键的支持索引，不重复已有主键或单列索引。
CREATE INDEX idx_v62_role_dept_tenant_dept ON sys_role_dept(tenant_id, dept_id);

CREATE TABLE password_history (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_password_history_user ON password_history(user_id);
CREATE INDEX idx_password_history_tenant ON password_history(tenant_id);
CREATE INDEX idx_password_history_created ON password_history(created_at);

CREATE TABLE security_audit_log (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    user_id BIGINT,
    username VARCHAR(100),
    tenant_id BIGINT,
    client_ip VARCHAR(64),
    user_agent VARCHAR(500),
    device_id VARCHAR(255),
    success BOOLEAN NOT NULL,
    failure_reason VARCHAR(500),
    additional_info TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_security_audit_event ON security_audit_log(event_type);
CREATE INDEX idx_security_audit_user ON security_audit_log(user_id);
CREATE INDEX idx_security_audit_tenant ON security_audit_log(tenant_id);
CREATE INDEX idx_security_audit_ip ON security_audit_log(client_ip);
CREATE INDEX idx_security_audit_created ON security_audit_log(created_at);
CREATE INDEX idx_security_audit_success ON security_audit_log(success);

CREATE TABLE jwt_key_version (
    id BIGSERIAL PRIMARY KEY,
    version VARCHAR(50) NOT NULL UNIQUE,
    algorithm VARCHAR(20) NOT NULL,
    key_data TEXT NOT NULL,
    public_key TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    rotated_at TIMESTAMP
);
-- UNIQUE(version) 已覆盖版本查找，不复制历史的非唯一同键索引。
CREATE INDEX idx_jwt_key_active ON jwt_key_version(is_active);
CREATE INDEX idx_jwt_key_created ON jwt_key_version(created_at);
CREATE INDEX idx_jwt_key_expires ON jwt_key_version(expires_at);

CREATE TABLE oauth2_client_secret_history (
    id BIGSERIAL PRIMARY KEY,
    client_id VARCHAR(100) NOT NULL,
    secret_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    rotated_at TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE
);
CREATE INDEX idx_client_secret_history_client ON oauth2_client_secret_history(client_id);
CREATE INDEX idx_client_secret_history_active ON oauth2_client_secret_history(is_active);
CREATE INDEX idx_client_secret_history_created ON oauth2_client_secret_history(created_at);

CREATE TABLE login_history (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    username VARCHAR(100) NOT NULL,
    tenant_id BIGINT NOT NULL,
    login_type VARCHAR(20) NOT NULL,
    success BOOLEAN NOT NULL,
    failure_reason VARCHAR(255),
    client_ip VARCHAR(64) NOT NULL,
    user_agent VARCHAR(500),
    device_id VARCHAR(255),
    device_type VARCHAR(50),
    location VARCHAR(255),
    is_suspicious BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_login_history_user ON login_history(user_id);
CREATE INDEX idx_login_history_tenant ON login_history(tenant_id);
CREATE INDEX idx_login_history_success ON login_history(success);
CREATE INDEX idx_login_history_ip ON login_history(client_ip);
CREATE INDEX idx_login_history_created ON login_history(created_at);
CREATE INDEX idx_login_history_suspicious ON login_history(is_suspicious);

CREATE TABLE trusted_device (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    device_id VARCHAR(255) NOT NULL,
    device_fingerprint VARCHAR(255) NOT NULL,
    device_name VARCHAR(100),
    device_type VARCHAR(50),
    last_ip VARCHAR(64),
    last_location VARCHAR(255),
    trust_score INT DEFAULT 100,
    is_trusted BOOLEAN DEFAULT TRUE,
    trusted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP,
    expires_at TIMESTAMP,
    UNIQUE (user_id, device_id)
);
CREATE INDEX idx_trusted_device_tenant ON trusted_device(tenant_id);
CREATE INDEX idx_trusted_device_fingerprint ON trusted_device(device_fingerprint);
CREATE INDEX idx_trusted_device_trusted ON trusted_device(is_trusted);
CREATE INDEX idx_trusted_device_last_used ON trusted_device(last_used_at);

CREATE TABLE encryption_key (
    id BIGSERIAL PRIMARY KEY,
    key_name VARCHAR(100) NOT NULL UNIQUE,
    key_type VARCHAR(50) NOT NULL,
    key_data TEXT NOT NULL,
    algorithm VARCHAR(50) NOT NULL,
    version VARCHAR(50) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    rotated_at TIMESTAMP,
    expires_at TIMESTAMP
);
CREATE INDEX idx_encryption_key_type ON encryption_key(key_type);
CREATE INDEX idx_encryption_key_active ON encryption_key(is_active);
CREATE INDEX idx_encryption_key_version ON encryption_key(version);

CREATE TABLE token_rotation_history (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    tenant_id VARCHAR(50),
    token_family VARCHAR(64) NOT NULL,
    old_jti VARCHAR(100) NOT NULL,
    new_jti VARCHAR(100) NOT NULL,
    rotation_reason VARCHAR(50) DEFAULT 'NORMAL_REFRESH',
    client_ip VARCHAR(45),
    user_agent VARCHAR(500),
    rotated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_token_rotation_user ON token_rotation_history(user_id);
CREATE INDEX idx_token_rotation_family ON token_rotation_history(token_family);
CREATE INDEX idx_token_rotation_rotated ON token_rotation_history(rotated_at);

CREATE TABLE token_security_event (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    user_id BIGINT,
    tenant_id VARCHAR(50),
    token_family VARCHAR(64),
    jti VARCHAR(100),
    description TEXT,
    client_ip VARCHAR(45),
    user_agent VARCHAR(500),
    additional_data JSONB,
    detected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    handled BOOLEAN DEFAULT FALSE,
    handled_at TIMESTAMP
);
CREATE INDEX idx_token_security_event_type ON token_security_event(event_type);
CREATE INDEX idx_token_security_severity ON token_security_event(severity);
CREATE INDEX idx_token_security_user ON token_security_event(user_id);
CREATE INDEX idx_token_security_detected ON token_security_event(detected_at);
CREATE INDEX idx_token_security_handled ON token_security_event(handled);

CREATE TABLE encryption_key_version (
    id BIGSERIAL PRIMARY KEY,
    key_version INT NOT NULL UNIQUE,
    key_id VARCHAR(100) NOT NULL,
    algorithm VARCHAR(50) DEFAULT 'AES-256-GCM',
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    rotated_at TIMESTAMP,
    expires_at TIMESTAMP
);
CREATE INDEX idx_encryption_key_version_active ON encryption_key_version(active);

CREATE TABLE mfa_config (
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
    UNIQUE (user_id, type)
);
CREATE INDEX idx_mfa_config_user ON mfa_config(user_id);
CREATE INDEX idx_mfa_config_tenant ON mfa_config(tenant_id);

CREATE TABLE ip_access_control (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    ip_address VARCHAR(128) NOT NULL,
    type VARCHAR(20) NOT NULL,
    description VARCHAR(500),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    priority INT NOT NULL DEFAULT 100,
    expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64) NOT NULL,
    updated_at TIMESTAMP,
    updated_by VARCHAR(64)
);
CREATE INDEX idx_ip_access_tenant_type ON ip_access_control(tenant_id, type);
CREATE INDEX idx_ip_access_ip_tenant ON ip_access_control(ip_address, tenant_id);
CREATE INDEX idx_ip_access_enabled ON ip_access_control(enabled);
CREATE INDEX idx_ip_access_expires ON ip_access_control(expires_at);

CREATE TABLE biometric_auth (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    type VARCHAR(20) NOT NULL,
    template_id TEXT,
    device_id VARCHAR(128) NOT NULL,
    device_name VARCHAR(255),
    public_key TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    last_auth_at TIMESTAMP,
    failed_attempts INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    UNIQUE (user_id, device_id, type)
);
CREATE INDEX idx_biometric_user ON biometric_auth(user_id);
CREATE INDEX idx_biometric_user_type ON biometric_auth(user_id, type);
CREATE INDEX idx_biometric_device ON biometric_auth(device_id);
CREATE INDEX idx_biometric_tenant ON biometric_auth(tenant_id);

CREATE TABLE device_trust (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    device_id VARCHAR(128) NOT NULL,
    device_name VARCHAR(255),
    device_type VARCHAR(20) NOT NULL,
    device_fingerprint VARCHAR(512),
    trust_level VARCHAR(20) NOT NULL DEFAULT 'UNTRUSTED',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    first_auth_at TIMESTAMP NOT NULL,
    last_auth_at TIMESTAMP,
    last_ip VARCHAR(64),
    last_location VARCHAR(255),
    auth_count INT NOT NULL DEFAULT 0,
    risk_score INT NOT NULL DEFAULT 0,
    metadata TEXT,
    expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    UNIQUE (user_id, device_id)
);
CREATE INDEX idx_device_trust_user ON device_trust(user_id);
CREATE INDEX idx_device_trust_device ON device_trust(device_id);
CREATE INDEX idx_device_trust_tenant ON device_trust(tenant_id);
CREATE INDEX idx_device_trust_status ON device_trust(status);
CREATE INDEX idx_device_trust_risk ON device_trust(risk_score);
CREATE INDEX idx_device_trust_expires ON device_trust(expires_at);

CREATE VIEW v_token_health AS
SELECT DATE(access_token_issued_at) AS issue_date,
    COUNT(*) AS total_tokens,
    SUM(CASE WHEN access_token_encrypted IS NOT NULL THEN 1 ELSE 0 END) AS encrypted_tokens,
    SUM(CASE WHEN revoked_at IS NULL AND access_token_expires_at > NOW() THEN 1 ELSE 0 END) AS active_tokens,
    SUM(CASE WHEN revoked_at IS NOT NULL THEN 1 ELSE 0 END) AS revoked_tokens,
    SUM(CASE WHEN rotation_count > 0 THEN 1 ELSE 0 END) AS rotated_tokens,
    AVG(rotation_count) AS avg_rotation_count,
    COUNT(DISTINCT token_family) AS active_families
FROM oauth2_authorization
WHERE access_token_issued_at >= NOW() - INTERVAL '7 days'
GROUP BY DATE(access_token_issued_at)
ORDER BY issue_date DESC;
