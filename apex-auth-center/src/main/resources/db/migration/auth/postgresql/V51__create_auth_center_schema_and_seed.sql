-- ============================================================
-- V51: 创建认证中心 Schema 与种子数据（PostgreSQL）
-- 变更内容：创建认证用户、令牌、会话、权限、角色、审计日志、
--           协议配置、系统配置、TOTP 与 MFA 配置等核心表及索引；
--           初始化默认角色、权限、测试用户、角色权限关联、
--           认证协议配置与系统策略种子数据。
-- ============================================================
CREATE TABLE IF NOT EXISTS auth_user (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    username VARCHAR(50) NOT NULL,
    email VARCHAR(100),
    phone VARCHAR(20),
    password_hash VARCHAR(255),
    open_id VARCHAR(100),
    status SMALLINT NOT NULL DEFAULT 1,
    last_login_at TIMESTAMP,
    last_login_ip VARCHAR(50),
    failed_login_attempts INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_auth_user_tenant_username UNIQUE (tenant_id, username, deleted)
);
CREATE INDEX IF NOT EXISTS idx_auth_user_tenant_phone ON auth_user (tenant_id, phone);
CREATE INDEX IF NOT EXISTS idx_auth_user_open_id ON auth_user (open_id);

CREATE TABLE IF NOT EXISTS auth_token (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    token_type VARCHAR(20) NOT NULL,
    token_value VARCHAR(500) NOT NULL,
    jti VARCHAR(100),
    expires_at TIMESTAMP NOT NULL,
    revoked SMALLINT NOT NULL DEFAULT 0,
    revoked_at TIMESTAMP,
    device_id VARCHAR(100),
    client_ip VARCHAR(50),
    user_agent VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_auth_token_jti UNIQUE (jti)
);
CREATE INDEX IF NOT EXISTS idx_auth_token_tenant_user ON auth_token (tenant_id, user_id);
CREATE INDEX IF NOT EXISTS idx_auth_token_expires_at ON auth_token (expires_at);

CREATE TABLE IF NOT EXISTS auth_session (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    session_id VARCHAR(100) NOT NULL,
    device_id VARCHAR(100),
    device_type VARCHAR(50),
    client_ip VARCHAR(50),
    user_agent VARCHAR(500),
    last_active_at TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_auth_session_id UNIQUE (session_id)
);
CREATE INDEX IF NOT EXISTS idx_auth_session_tenant_user ON auth_session (tenant_id, user_id);
CREATE INDEX IF NOT EXISTS idx_auth_session_expires_at ON auth_session (expires_at);

CREATE TABLE IF NOT EXISTS auth_permission (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    code VARCHAR(100) NOT NULL,
    name VARCHAR(100) NOT NULL,
    resource VARCHAR(200),
    action VARCHAR(50),
    description TEXT,
    resource_type VARCHAR(64),
    resource_identifier VARCHAR(200),
    enabled SMALLINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    deleted SMALLINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_auth_permission_tenant_code UNIQUE (tenant_id, code)
);

CREATE TABLE IF NOT EXISTS auth_role (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    code VARCHAR(100) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    data_scope VARCHAR(32) NOT NULL DEFAULT '0',
    sort_order INT NOT NULL DEFAULT 0,
    enabled SMALLINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    deleted SMALLINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_auth_role_tenant_code UNIQUE (tenant_id, code)
);

CREATE TABLE IF NOT EXISTS auth_user_role (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_auth_user_role UNIQUE (user_id, role_id)
);
CREATE INDEX IF NOT EXISTS idx_auth_user_role_tenant_user ON auth_user_role (tenant_id, user_id);

CREATE TABLE IF NOT EXISTS auth_role_permission (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_auth_role_permission UNIQUE (role_id, permission_id)
);

CREATE TABLE IF NOT EXISTS auth_audit_log (
    id BIGINT NOT NULL PRIMARY KEY,
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
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_auth_audit_tenant_user ON auth_audit_log (tenant_id, user_id);
CREATE INDEX IF NOT EXISTS idx_auth_audit_created_at ON auth_audit_log (created_at);

CREATE TABLE IF NOT EXISTS auth_protocol_config (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    protocol_type VARCHAR(50) NOT NULL,
    protocol_name VARCHAR(100) NOT NULL,
    config_json TEXT NOT NULL,
    enabled SMALLINT NOT NULL DEFAULT 1,
    priority INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 1
);
CREATE INDEX IF NOT EXISTS idx_auth_protocol_tenant_type ON auth_protocol_config (tenant_id, protocol_type);

CREATE TABLE IF NOT EXISTS auth_config (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    config_type VARCHAR(64) NOT NULL,
    config_key VARCHAR(128) NOT NULL,
    config_value TEXT NOT NULL,
    description VARCHAR(500),
    enabled SMALLINT NOT NULL DEFAULT 1,
    is_default SMALLINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    CONSTRAINT uk_auth_config_tenant_type_key UNIQUE (tenant_id, config_type, config_key)
);

CREATE TABLE IF NOT EXISTS auth_totp (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    secret VARCHAR(256) NOT NULL,
    verified SMALLINT NOT NULL DEFAULT 0,
    backup_codes TEXT,
    used_backup_codes TEXT,
    enabled SMALLINT NOT NULL DEFAULT 0,
    last_verified_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 1,
    deleted SMALLINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_auth_totp_tenant_user UNIQUE (tenant_id, user_id, deleted)
);

CREATE TABLE IF NOT EXISTS auth_mfa_config (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    mfa_type VARCHAR(20) NOT NULL,
    enabled SMALLINT NOT NULL DEFAULT 0,
    required SMALLINT NOT NULL DEFAULT 0,
    priority INT NOT NULL DEFAULT 1,
    metadata TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 1,
    deleted SMALLINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_auth_mfa_tenant_user_type UNIQUE (tenant_id, user_id, mfa_type, deleted)
);

INSERT INTO auth_role (id, tenant_id, code, name, description, data_scope, enabled, created_at, updated_at)
VALUES
    (1, 'default', 'ROLE_ADMIN', '系统管理员', '系统管理员角色，拥有所有权限', '0', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, 'default', 'ROLE_USER', '普通用户', '普通用户角色，拥有基础权限', '1', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, 'default', 'ROLE_GUEST', '访客', '访客角色，只读权限', '2', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;

INSERT INTO auth_permission (id, tenant_id, code, name, resource, action, description, enabled, created_at, updated_at)
VALUES
    (1, 'default', 'user:read', '查看用户', 'user', 'read', '查看用户信息', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, 'default', 'user:create', '创建用户', 'user', 'create', '创建新用户', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, 'default', 'user:update', '更新用户', 'user', 'update', '更新用户信息', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (4, 'default', 'user:delete', '删除用户', 'user', 'delete', '删除用户信息', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (5, 'default', 'role:read', '查看角色', 'role', 'read', '查看角色信息', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (6, 'default', 'role:create', '创建角色', 'role', 'create', '创建角色', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (7, 'default', 'role:update', '更新角色', 'role', 'update', '更新角色', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (8, 'default', 'role:delete', '删除角色', 'role', 'delete', '删除角色信息', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (9, 'default', 'role:assign', '分配权限', 'role', 'assign', '分配角色权限', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (10, 'default', 'permission:read', '查看权限', 'permission', 'read', '查看权限信息', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (11, 'default', 'permission:create', '创建权限', 'permission', 'create', '创建权限', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (12, 'default', 'permission:update', '更新权限', 'permission', 'update', '更新权限信息', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (13, 'default', 'permission:delete', '删除权限', 'permission', 'delete', '删除权限信息', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (14, 'default', 'session:read', '查看会话', 'session', 'read', '查看会话信息', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (15, 'default', 'session:terminate', '终止会话', 'session', 'terminate', '终止用户会话', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (16, 'default', 'session:statistics', '会话统计', 'session', 'statistics', '查看会话统计', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (17, 'default', 'audit:read', '查看审计日志', 'audit', 'read', '查看审计日志', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (18, 'default', 'audit:export', '导出审计日志', 'audit', 'export', '导出审计日志', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (19, 'default', 'protocol:read', '查看协议配置', 'protocol', 'read', '查看协议配置', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (20, 'default', 'protocol:update', '更新协议配置', 'protocol', 'update', '更新协议配置', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;

INSERT INTO auth_user (id, tenant_id, username, email, password_hash, status, created_at, updated_at, deleted)
VALUES
    (1, 'default', 'admin', 'admin@apex.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (2, 'default', 'user', 'user@apex.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (1001, 'default', 'test', 'test@apex.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON CONFLICT DO NOTHING;

INSERT INTO auth_user_role (id, tenant_id, user_id, role_id, created_at)
VALUES
    (1, 'default', 1, 1, CURRENT_TIMESTAMP),
    (2, 'default', 2, 2, CURRENT_TIMESTAMP),
    (1001, 'default', 1001, 2, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;

INSERT INTO auth_role_permission (id, tenant_id, role_id, permission_id, created_at)
SELECT id, 'default', 1, id, CURRENT_TIMESTAMP FROM auth_permission WHERE tenant_id = 'default'
ON CONFLICT DO NOTHING;
INSERT INTO auth_role_permission (id, tenant_id, role_id, permission_id, created_at)
VALUES
    (101, 'default', 2, 1, CURRENT_TIMESTAMP),
    (102, 'default', 2, 5, CURRENT_TIMESTAMP),
    (103, 'default', 2, 10, CURRENT_TIMESTAMP),
    (104, 'default', 2, 14, CURRENT_TIMESTAMP),
    (201, 'default', 3, 1, CURRENT_TIMESTAMP),
    (202, 'default', 3, 5, CURRENT_TIMESTAMP),
    (203, 'default', 3, 10, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;

INSERT INTO auth_protocol_config (id, tenant_id, protocol_type, protocol_name, config_json, enabled, priority, created_at, updated_at, version)
VALUES
    (1, 'default', 'JWT', 'JWT认证', '{"algorithm":"HS256","issuer":"apex-auth-center","accessTokenExpiry":7200,"refreshTokenExpiry":604800}', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (2, 'default', 'OAUTH2', 'OAuth2认证', '{"authorizeEndpoint":"/oauth2/authorize","tokenEndpoint":"/oauth2/token","grantTypes":["authorization_code","refresh_token"]}', 1, 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON CONFLICT DO NOTHING;

INSERT INTO auth_config (id, tenant_id, config_type, config_key, config_value, description, enabled, is_default, version, created_at, updated_at)
VALUES
    (1, 'default', 'PASSWORD_POLICY', 'default', '{"minLength":8,"maxLength":32,"requireUppercase":true,"requireLowercase":true,"requireDigit":true,"requireSpecialChar":true}', '默认密码策略', 1, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, 'default', 'SESSION_POLICY', 'default', '{"maxConcurrentSessions":5,"sessionTimeout":3600,"idleTimeout":1800}', '默认会话策略', 1, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, 'default', 'MFA_POLICY', 'default', '{"enableMfa":false,"mfaRequired":false,"defaultMethod":"TOTP"}', '默认多因素认证策略', 1, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;
