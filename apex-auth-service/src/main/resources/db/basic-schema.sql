-- Apex 认证中心基础 Schema：认证授权域核心表结构基线（审计日志、令牌、密钥等）。
-- 本文件为建表基线脚本，增量变更请使用 db/migration 下的 Flyway 版本脚本。
CREATE TABLE IF NOT EXISTS auth_audit_log (
    id BIGINT PRIMARY KEY,
    tenant_id VARCHAR(4096),
    user_id BIGINT,
    username VARCHAR(4096),
    event_type VARCHAR(4096),
    event_status VARCHAR(4096),
    resource_type VARCHAR(4096),
    resource_id VARCHAR(4096),
    action VARCHAR(4096),
    ip_address VARCHAR(4096),
    user_agent VARCHAR(4096),
    location VARCHAR(4096),
    details VARCHAR(4096),
    failure_reason VARCHAR(4096),
    risk_level VARCHAR(4096),
    event_time TIMESTAMP,
    created_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS auth_config (
    id BIGINT PRIMARY KEY,
    tenant_id VARCHAR(4096),
    config_type VARCHAR(4096),
    config_key VARCHAR(4096),
    config_value VARCHAR(4096),
    description VARCHAR(4096),
    enabled BOOLEAN,
    is_default BOOLEAN,
    version INT,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT
);

CREATE TABLE IF NOT EXISTS auth_mfa_config (
    id BIGINT PRIMARY KEY,
    tenant_id VARCHAR(4096),
    user_id BIGINT,
    mfa_type VARCHAR(4096),
    enabled INT,
    required INT,
    priority INT,
    metadata VARCHAR(4096),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    version INT,
    deleted INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS auth_permission (
    id BIGINT PRIMARY KEY,
    tenant_id VARCHAR(4096),
    code VARCHAR(4096),
    name VARCHAR(4096),
    description VARCHAR(4096),
    resource_type VARCHAR(4096),
    resource_identifier VARCHAR(4096),
    action VARCHAR(4096),
    enabled INT,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    deleted INT DEFAULT 0,
    version INT
);

CREATE TABLE IF NOT EXISTS auth_role (
    id BIGINT PRIMARY KEY,
    tenant_id VARCHAR(4096),
    code VARCHAR(4096),
    name VARCHAR(4096),
    description VARCHAR(4096),
    data_scope VARCHAR(4096),
    sort_order INT,
    enabled INT,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    deleted INT DEFAULT 0,
    version INT
);

CREATE TABLE IF NOT EXISTS auth_role_permission (
    id BIGINT PRIMARY KEY,
    tenant_id VARCHAR(4096),
    role_id BIGINT,
    permission_id BIGINT,
    created_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS auth_session (
    id BIGINT PRIMARY KEY,
    tenant_id VARCHAR(4096),
    session_id VARCHAR(4096),
    user_id BIGINT,
    token_id BIGINT,
    device_type VARCHAR(4096),
    device_id VARCHAR(4096),
    device_name VARCHAR(4096),
    client_ip VARCHAR(4096),
    user_agent VARCHAR(4096),
    location VARCHAR(4096),
    status VARCHAR(4096),
    created_at TIMESTAMP,
    last_active_at TIMESTAMP,
    expires_at TIMESTAMP,
    metadata VARCHAR(4096),
    deleted INT DEFAULT 0,
    version INT
);

CREATE TABLE IF NOT EXISTS auth_token (
    id BIGINT PRIMARY KEY,
    tenant_id VARCHAR(4096),
    user_id BIGINT,
    token_type VARCHAR(4096),
    token_value VARCHAR(4096),
    jti VARCHAR(4096),
    expires_at TIMESTAMP,
    revoked INT DEFAULT 0,
    revoked_at TIMESTAMP,
    revoked_by VARCHAR(4096),
    revoke_reason VARCHAR(4096),
    last_used_at TIMESTAMP,
    use_count INT,
    device_id VARCHAR(4096),
    client_ip VARCHAR(4096),
    user_agent VARCHAR(4096),
    token_hash VARCHAR(4096),
    token_iv VARCHAR(4096),
    token_family VARCHAR(4096),
    previous_jti VARCHAR(4096),
    rotation_count INT,
    created_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS auth_totp (
    id BIGINT PRIMARY KEY,
    tenant_id VARCHAR(4096),
    user_id BIGINT,
    secret VARCHAR(4096),
    verified INT,
    backup_codes VARCHAR(4096),
    used_backup_codes VARCHAR(4096),
    enabled INT,
    last_verified_at TIMESTAMP,
    last_used_step BIGINT,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    version INT,
    deleted INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS auth_user (
    id BIGINT PRIMARY KEY,
    tenant_id VARCHAR(4096),
    username VARCHAR(4096),
    email VARCHAR(4096),
    phone VARCHAR(4096),
    password_hash VARCHAR(4096),
    open_id VARCHAR(4096),
    account_id BIGINT,
    status INT,
    last_login_at TIMESTAMP,
    last_login_ip VARCHAR(4096),
    failed_login_attempts INT,
    locked_until TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    deleted INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS auth_user_role (
    id BIGINT PRIMARY KEY,
    tenant_id VARCHAR(4096),
    user_id BIGINT,
    role_id BIGINT,
    created_at TIMESTAMP
);

-- ===== R2DBC 仓储实体表（basic profile 下与上方 JDBC 共享同一 H2 内存库） =====

CREATE TABLE IF NOT EXISTS ip_access_control (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(4096),
    ip_address VARCHAR(4096),
    type VARCHAR(4096),
    description VARCHAR(4096),
    enabled BOOLEAN,
    priority INT,
    expires_at TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(4096),
    updated_by VARCHAR(4096)
);

CREATE TABLE IF NOT EXISTS biometric_auth (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(4096),
    tenant_id VARCHAR(4096),
    type VARCHAR(4096),
    template_id VARCHAR(4096),
    device_id VARCHAR(4096),
    device_name VARCHAR(4096),
    public_key VARCHAR(4096),
    status VARCHAR(4096),
    last_auth_at TIMESTAMP,
    failed_attempts INT,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS device_trust (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(4096),
    tenant_id VARCHAR(4096),
    device_id VARCHAR(4096),
    device_name VARCHAR(4096),
    device_type VARCHAR(4096),
    device_fingerprint VARCHAR(4096),
    trust_level VARCHAR(4096),
    status VARCHAR(4096),
    first_auth_at TIMESTAMP,
    last_auth_at TIMESTAMP,
    last_ip VARCHAR(4096),
    last_location VARCHAR(4096),
    auth_count INT,
    risk_score INT,
    metadata VARCHAR(4096),
    expires_at TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS mfa_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(4096),
    tenant_id VARCHAR(4096),
    type VARCHAR(4096),
    status VARCHAR(4096),
    secret VARCHAR(4096),
    backup_codes VARCHAR(4096),
    device_info VARCHAR(4096),
    last_verified_at TIMESTAMP,
    failed_attempts INT,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS oauth2_authorization (
    id VARCHAR(100) PRIMARY KEY,
    tenant_id VARCHAR(100) NOT NULL,
    registered_client_id VARCHAR(100) NOT NULL,
    client_id VARCHAR(100) NOT NULL,
    principal_name VARCHAR(200) NOT NULL,
    authorization_grant_type VARCHAR(50) NOT NULL,
    authorized_scopes VARCHAR(1000) NOT NULL,
    redirect_uri VARCHAR(1000),
    code_challenge VARCHAR(128),
    code_challenge_method VARCHAR(10),
    authorization_code_hash CHAR(64) UNIQUE,
    authorization_code_expires_at TIMESTAMP,
    access_token_hash CHAR(64) UNIQUE,
    access_token_expires_at TIMESTAMP,
    refresh_token_hash CHAR(64) UNIQUE,
    refresh_token_expires_at TIMESTAMP,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_oauth2_tenant_client ON oauth2_authorization(tenant_id, client_id);

-- 领域事件可靠投递表（迁移方案 Phase 2.6 登出传播）。
-- 注意：basic/H2 环境默认关闭 Outbox（apex.events.outbox.enabled=false），
-- 此处仅保证表结构存在与集成测试的建表一致性，不参与实际投递。
CREATE TABLE IF NOT EXISTS sys_domain_event_outbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL,
    event_class VARCHAR(255) NOT NULL,
    payload VARCHAR(4096) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_by VARCHAR(64),
    locked_at TIMESTAMP,
    last_error VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    delivered_at TIMESTAMP,
    CONSTRAINT uk_outbox_event_id UNIQUE (event_id)
);
CREATE INDEX IF NOT EXISTS idx_outbox_dispatch ON sys_domain_event_outbox(status, next_attempt_at, id);
CREATE INDEX IF NOT EXISTS idx_outbox_locked ON sys_domain_event_outbox(status, locked_at);

CREATE TABLE IF NOT EXISTS auth_oauth2_consent (
    id BIGINT PRIMARY KEY,
    tenant_id VARCHAR(4096),
    principal VARCHAR(4096),
    client_id VARCHAR(4096),
    scope VARCHAR(4096),
    consented_at TIMESTAMP,
    version INT DEFAULT 0,
    deleted INT DEFAULT 0,
    create_time TIMESTAMP,
    update_time TIMESTAMP
);
