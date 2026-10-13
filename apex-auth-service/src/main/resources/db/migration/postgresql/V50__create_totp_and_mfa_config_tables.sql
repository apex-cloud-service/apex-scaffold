-- V50: 创建 TOTP 和 MFA 配置表
-- 多因素认证（MFA）支持，包括 TOTP、短信验证码、邮件验证码、WebAuthn

-- TOTP 配置表
CREATE TABLE IF NOT EXISTS auth_totp (
    id                  BIGINT NOT NULL PRIMARY KEY,
    tenant_id           VARCHAR(64) NOT NULL,
    user_id             BIGINT NOT NULL,
    secret              VARCHAR(256) NOT NULL,
    verified            SMALLINT NOT NULL DEFAULT 0,
    backup_codes        TEXT,
    used_backup_codes   TEXT,
    enabled             SMALLINT NOT NULL DEFAULT 0,
    last_verified_at    TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version             INT NOT NULL DEFAULT 1,
    deleted             SMALLINT NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, user_id, deleted)
);

CREATE INDEX IF NOT EXISTS auth_totp_idx_user_id ON auth_totp (user_id);
CREATE INDEX IF NOT EXISTS auth_totp_idx_tenant_id ON auth_totp (tenant_id);

COMMENT ON TABLE auth_totp IS 'TOTP多因素认证配置表';
COMMENT ON COLUMN auth_totp.id IS '主键ID';
COMMENT ON COLUMN auth_totp.tenant_id IS '租户ID';
COMMENT ON COLUMN auth_totp.user_id IS '用户ID';
COMMENT ON COLUMN auth_totp.secret IS 'TOTP密钥（加密存储）';
COMMENT ON COLUMN auth_totp.verified IS '是否已验证（0-未验证, 1-已验证）';
COMMENT ON COLUMN auth_totp.backup_codes IS '备份码列表（加密存储，逗号分隔）';
COMMENT ON COLUMN auth_totp.used_backup_codes IS '已使用的备份码列表（逗号分隔）';
COMMENT ON COLUMN auth_totp.enabled IS '启用状态（0-禁用, 1-启用）';
COMMENT ON COLUMN auth_totp.last_verified_at IS '最后验证时间';
COMMENT ON COLUMN auth_totp.created_at IS '创建时间';
COMMENT ON COLUMN auth_totp.updated_at IS '更新时间';
COMMENT ON COLUMN auth_totp.version IS '乐观锁版本号';
COMMENT ON COLUMN auth_totp.deleted IS '逻辑删除（0-未删除, 1-已删除）';

-- MFA 配置表
CREATE TABLE IF NOT EXISTS auth_mfa_config (
    id          BIGINT NOT NULL PRIMARY KEY,
    tenant_id   VARCHAR(64) NOT NULL,
    user_id     BIGINT NOT NULL,
    mfa_type    VARCHAR(20) NOT NULL,
    enabled     SMALLINT NOT NULL DEFAULT 0,
    required    SMALLINT NOT NULL DEFAULT 0,
    priority    INT NOT NULL DEFAULT 1,
    metadata    TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version     INT NOT NULL DEFAULT 1,
    deleted     SMALLINT NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, user_id, mfa_type, deleted)
);

CREATE INDEX IF NOT EXISTS auth_mfa_config_idx_user_id ON auth_mfa_config (user_id);
CREATE INDEX IF NOT EXISTS auth_mfa_config_idx_tenant_id ON auth_mfa_config (tenant_id);
CREATE INDEX IF NOT EXISTS auth_mfa_config_idx_mfa_type ON auth_mfa_config (mfa_type);

COMMENT ON TABLE auth_mfa_config IS 'MFA多因素认证配置表';
COMMENT ON COLUMN auth_mfa_config.id IS '主键ID';
COMMENT ON COLUMN auth_mfa_config.tenant_id IS '租户ID';
COMMENT ON COLUMN auth_mfa_config.user_id IS '用户ID';
COMMENT ON COLUMN auth_mfa_config.mfa_type IS 'MFA类型（TOTP, SMS, EMAIL, WEBAUTHN）';
COMMENT ON COLUMN auth_mfa_config.enabled IS '是否启用（0-禁用, 1-启用）';
COMMENT ON COLUMN auth_mfa_config.required IS '是否必需（0-非必需, 1-必需）';
COMMENT ON COLUMN auth_mfa_config.priority IS '优先级（数值越小优先级越高）';
COMMENT ON COLUMN auth_mfa_config.metadata IS '配置元数据（JSON格式）';
COMMENT ON COLUMN auth_mfa_config.created_at IS '创建时间';
COMMENT ON COLUMN auth_mfa_config.updated_at IS '更新时间';
COMMENT ON COLUMN auth_mfa_config.version IS '乐观锁版本号';
COMMENT ON COLUMN auth_mfa_config.deleted IS '逻辑删除（0-未删除, 1-已删除）';

INSERT INTO auth_permission
    (id, tenant_id, code, name, resource, action, description, enabled, created_at, updated_at)
VALUES
    (5001, 'default', 'mfa:setup', '设置MFA', '/api/v1/mfa/setup', 'POST', '设置TOTP多因素认证', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (5002, 'default', 'mfa:verify', '验证MFA', '/api/v1/mfa/verify', 'POST', '验证TOTP验证码', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (5003, 'default', 'mfa:verify-code', '验证MFA码', '/api/v1/mfa/verify-code', 'POST', '登录时验证TOTP', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (5004, 'default', 'mfa:verify-backup', '验证备份码', '/api/v1/mfa/verify-backup', 'POST', '验证备份码', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (5005, 'default', 'mfa:disable', '禁用MFA', '/api/v1/mfa/disable', 'POST', '禁用多因素认证', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (5006, 'default', 'mfa:status', '查询MFA状态', '/api/v1/mfa/status', 'GET', '查询MFA启用状态', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;

INSERT INTO auth_role_permission (id, tenant_id, role_id, permission_id, created_at)
VALUES
    (6301, 'default', 1, 5001, CURRENT_TIMESTAMP),
    (6302, 'default', 1, 5002, CURRENT_TIMESTAMP),
    (6303, 'default', 1, 5003, CURRENT_TIMESTAMP),
    (6304, 'default', 1, 5004, CURRENT_TIMESTAMP),
    (6305, 'default', 1, 5005, CURRENT_TIMESTAMP),
    (6306, 'default', 1, 5006, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;
