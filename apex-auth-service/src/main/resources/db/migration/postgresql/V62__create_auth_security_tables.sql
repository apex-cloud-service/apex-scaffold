-- ============================================================
-- V62: 补齐认证中心安全能力表（PostgreSQL）
-- ============================================================
-- 背景：apex-auth 模块的 IpAccessControlFilter 在每次请求执行 IP 黑白名单校验，
--       BiometricAuthService / DeviceTrustService 通过 R2DBC 仓储读写生物识别与
--       设备信任数据。上述能力此前只存在于 system 域迁移与 basic/H2 的
--       basic-schema.sql，独立部署的 apex_auth 生产库缺少对应表结构。
-- 说明：字段以 apex-auth 实体（IpAccessControl / BiometricAuth / DeviceTrust）为准；
--       mfa_config 由 V58 创建，此处不重复。
-- 幂等性：CREATE TABLE IF NOT EXISTS，可重复执行。
-- ============================================================

-- ------------------------------------------------------------
-- IP 访问控制表（IpAccessControl）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ip_access_control (
    id          BIGSERIAL    PRIMARY KEY,
    tenant_id   VARCHAR(64)  NOT NULL,
    ip_address  VARCHAR(128) NOT NULL,
    type        VARCHAR(20)  NOT NULL,
    description VARCHAR(500),
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    priority    INTEGER      NOT NULL DEFAULT 100,
    expires_at  TIMESTAMP,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at  TIMESTAMP,
    updated_by  VARCHAR(64)
);
COMMENT ON TABLE ip_access_control IS 'IP访问控制表';
COMMENT ON COLUMN ip_access_control.type IS '控制类型：WHITELIST-白名单, BLACKLIST-黑名单';
CREATE INDEX IF NOT EXISTS idx_ip_acl_tenant_type ON ip_access_control (tenant_id, type);
CREATE INDEX IF NOT EXISTS idx_ip_acl_ip_tenant ON ip_access_control (ip_address, tenant_id);
CREATE INDEX IF NOT EXISTS idx_ip_acl_enabled ON ip_access_control (enabled);
CREATE INDEX IF NOT EXISTS idx_ip_acl_expires_at ON ip_access_control (expires_at);

-- ------------------------------------------------------------
-- 生物识别认证表（BiometricAuth）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biometric_auth (
    id              BIGSERIAL    PRIMARY KEY,
    user_id         VARCHAR(64)  NOT NULL,
    tenant_id       VARCHAR(64)  NOT NULL,
    type            VARCHAR(20)  NOT NULL,
    template_id     TEXT,
    device_id       VARCHAR(128) NOT NULL,
    device_name     VARCHAR(255),
    public_key      TEXT,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    last_auth_at    TIMESTAMP,
    failed_attempts INTEGER      NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP
);
COMMENT ON TABLE biometric_auth IS '生物识别认证表';
COMMENT ON COLUMN biometric_auth.type IS '生物识别类型：FINGERPRINT, FACE_ID, IRIS, VOICE, WEBAUTHN';
COMMENT ON COLUMN biometric_auth.status IS '认证状态：PENDING, ACTIVE, DISABLED, LOCKED';
CREATE INDEX IF NOT EXISTS idx_bio_auth_user ON biometric_auth (user_id);
CREATE INDEX IF NOT EXISTS idx_bio_auth_user_type ON biometric_auth (user_id, type);
CREATE INDEX IF NOT EXISTS idx_bio_auth_device ON biometric_auth (device_id);
CREATE INDEX IF NOT EXISTS idx_bio_auth_tenant ON biometric_auth (tenant_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_bio_auth_user_device_type ON biometric_auth (user_id, device_id, type);

-- ------------------------------------------------------------
-- 设备信任管理表（DeviceTrust）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS device_trust (
    id                 BIGSERIAL    PRIMARY KEY,
    user_id            VARCHAR(64)  NOT NULL,
    tenant_id          VARCHAR(64)  NOT NULL,
    device_id          VARCHAR(128) NOT NULL,
    device_name        VARCHAR(255),
    device_type        VARCHAR(20)  NOT NULL,
    device_fingerprint VARCHAR(512),
    trust_level        VARCHAR(20)  NOT NULL DEFAULT 'UNTRUSTED',
    status             VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    first_auth_at      TIMESTAMP    NOT NULL,
    last_auth_at       TIMESTAMP,
    last_ip            VARCHAR(64),
    last_location      VARCHAR(255),
    auth_count         INTEGER      NOT NULL DEFAULT 0,
    risk_score         INTEGER      NOT NULL DEFAULT 0,
    metadata           TEXT,
    expires_at         TIMESTAMP,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP
);
COMMENT ON TABLE device_trust IS '设备信任管理表';
COMMENT ON COLUMN device_trust.device_type IS '设备类型：DESKTOP, LAPTOP, MOBILE, TABLET, UNKNOWN';
COMMENT ON COLUMN device_trust.trust_level IS '信任级别：UNTRUSTED, LOW, MEDIUM, HIGH, FULL';
COMMENT ON COLUMN device_trust.status IS '信任状态：PENDING, ACTIVE, EXPIRED, REVOKED, LOCKED';
CREATE INDEX IF NOT EXISTS idx_device_trust_user ON device_trust (user_id);
CREATE INDEX IF NOT EXISTS idx_device_trust_device ON device_trust (device_id);
CREATE INDEX IF NOT EXISTS idx_device_trust_tenant ON device_trust (tenant_id);
CREATE INDEX IF NOT EXISTS idx_device_trust_status ON device_trust (status);
CREATE INDEX IF NOT EXISTS idx_device_trust_risk ON device_trust (risk_score);
CREATE INDEX IF NOT EXISTS idx_device_trust_expires ON device_trust (expires_at);
CREATE UNIQUE INDEX IF NOT EXISTS uk_device_trust_user_device ON device_trust (user_id, device_id);
