-- ============================================================
-- V44: 创建认证中心核心表（PostgreSQL）
-- 变更内容：创建认证用户、令牌、会话、权限、角色、用户角色、
--           角色权限、审计日志等核心表及配套索引。
-- ============================================================
CREATE TABLE IF NOT EXISTS auth_user (
    id BIGINT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    username VARCHAR(50) NOT NULL,
    email VARCHAR(100),
    phone VARCHAR(20),
    password_hash VARCHAR(255),
    open_id VARCHAR(100),
    status SMALLINT DEFAULT 1,
    last_login_at TIMESTAMP,
    last_login_ip VARCHAR(50),
    failed_login_attempts INT DEFAULT 0,
    locked_until TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT DEFAULT 0,
    UNIQUE (tenant_id, username, deleted)
);
CREATE INDEX IF NOT EXISTS auth_user_idx_tenant_username ON auth_user (tenant_id, username);
CREATE INDEX IF NOT EXISTS auth_user_idx_tenant_phone ON auth_user (tenant_id, phone);
CREATE INDEX IF NOT EXISTS auth_user_idx_open_id ON auth_user (open_id);
CREATE INDEX IF NOT EXISTS auth_user_idx_last_login_at ON auth_user (last_login_at);

CREATE TABLE IF NOT EXISTS auth_token (
    id BIGINT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    token_type VARCHAR(20) NOT NULL,
    token_value VARCHAR(500) NOT NULL,
    jti VARCHAR(100) UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    revoked SMALLINT DEFAULT 0,
    revoked_at TIMESTAMP,
    device_id VARCHAR(100),
    client_ip VARCHAR(50),
    user_agent VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS auth_token_idx_tenant_user ON auth_token (tenant_id, user_id);
CREATE UNIQUE INDEX IF NOT EXISTS auth_token_idx_jti ON auth_token (jti);
CREATE INDEX IF NOT EXISTS auth_token_idx_expires_at ON auth_token (expires_at);
CREATE INDEX IF NOT EXISTS auth_token_idx_device_id ON auth_token (device_id);

CREATE TABLE IF NOT EXISTS auth_session (
    id BIGINT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    session_id VARCHAR(100) UNIQUE NOT NULL,
    device_id VARCHAR(100),
    device_type VARCHAR(50),
    client_ip VARCHAR(50),
    user_agent VARCHAR(500),
    last_active_at TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS auth_session_idx_tenant_user ON auth_session (tenant_id, user_id);
CREATE UNIQUE INDEX IF NOT EXISTS auth_session_idx_session_id ON auth_session (session_id);
CREATE INDEX IF NOT EXISTS auth_session_idx_last_active ON auth_session (last_active_at);
CREATE INDEX IF NOT EXISTS auth_session_idx_expires_at ON auth_session (expires_at);

CREATE TABLE IF NOT EXISTS auth_permission (
    id BIGINT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    code VARCHAR(100) NOT NULL,
    name VARCHAR(100) NOT NULL,
    resource VARCHAR(200),
    action VARCHAR(50),
    description TEXT,
    enabled SMALLINT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, code)
);


CREATE TABLE IF NOT EXISTS auth_role (
    id BIGINT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    code VARCHAR(100) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    data_scope SMALLINT DEFAULT 0,
    enabled SMALLINT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, code)
);


CREATE TABLE IF NOT EXISTS auth_user_role (
    id BIGINT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, role_id)
);
CREATE INDEX IF NOT EXISTS auth_user_role_idx_tenant_user ON auth_user_role (tenant_id, user_id);

CREATE TABLE IF NOT EXISTS auth_role_permission (
    id BIGINT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (role_id, permission_id)
);


CREATE TABLE IF NOT EXISTS auth_audit_log (
    id BIGINT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    user_id BIGINT,
    event_type VARCHAR(50) NOT NULL,
    event_status VARCHAR(20) NOT NULL,
    protocol_type VARCHAR(50),
    client_ip VARCHAR(50),
    user_agent VARCHAR(500),
    device_id VARCHAR(100),
    request_path VARCHAR(500),
    error_message TEXT,
    metadata TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS auth_audit_log_idx_tenant_user ON auth_audit_log (tenant_id, user_id);
CREATE INDEX IF NOT EXISTS auth_audit_log_idx_event_type ON auth_audit_log (event_type);
CREATE INDEX IF NOT EXISTS auth_audit_log_idx_created_at ON auth_audit_log (created_at);
CREATE INDEX IF NOT EXISTS auth_audit_log_idx_event_status ON auth_audit_log (event_status);

CREATE TABLE IF NOT EXISTS auth_protocol_config (
    id BIGINT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    protocol_type VARCHAR(50) NOT NULL,
    protocol_name VARCHAR(100) NOT NULL,
    config_json TEXT NOT NULL,
    enabled SMALLINT DEFAULT 1,
    priority INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version INT DEFAULT 1
);
CREATE INDEX IF NOT EXISTS auth_protocol_config_idx_tenant_type ON auth_protocol_config (tenant_id, protocol_type);
