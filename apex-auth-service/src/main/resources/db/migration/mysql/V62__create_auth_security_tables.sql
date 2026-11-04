-- ============================================================
-- V62: 补齐认证中心安全能力表（MySQL）
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
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    tenant_id   VARCHAR(64)  NOT NULL COMMENT '租户ID',
    ip_address  VARCHAR(128) NOT NULL COMMENT 'IP地址或CIDR（支持通配符）',
    type        VARCHAR(20)  NOT NULL COMMENT '控制类型：WHITELIST-白名单, BLACKLIST-黑名单',
    description VARCHAR(500)          DEFAULT NULL COMMENT '规则描述',
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE COMMENT '是否启用',
    priority    INT          NOT NULL DEFAULT 100 COMMENT '优先级（数字越小优先级越高）',
    expires_at  DATETIME              DEFAULT NULL COMMENT '过期时间（黑名单可设置自动解禁）',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by  VARCHAR(64)  NOT NULL DEFAULT 'system' COMMENT '创建人',
    updated_at  DATETIME              DEFAULT NULL COMMENT '更新时间',
    updated_by  VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
    PRIMARY KEY (id),
    KEY idx_ip_acl_tenant_type (tenant_id, type),
    KEY idx_ip_acl_ip_tenant (ip_address, tenant_id),
    KEY idx_ip_acl_enabled (enabled),
    KEY idx_ip_acl_expires_at (expires_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'IP访问控制表';

-- ------------------------------------------------------------
-- 生物识别认证表（BiometricAuth）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biometric_auth (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    user_id         VARCHAR(64)  NOT NULL COMMENT '用户ID',
    tenant_id       VARCHAR(64)  NOT NULL COMMENT '租户ID',
    type            VARCHAR(20)  NOT NULL COMMENT '生物识别类型：FINGERPRINT, FACE_ID, IRIS, VOICE, WEBAUTHN',
    template_id     TEXT                  DEFAULT NULL COMMENT '生物特征模板ID（加密存储）',
    device_id       VARCHAR(128) NOT NULL COMMENT '设备ID（绑定设备）',
    device_name     VARCHAR(255)          DEFAULT NULL COMMENT '设备名称',
    public_key      TEXT                  DEFAULT NULL COMMENT '公钥（用于WebAuthn验证签名）',
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '认证状态：PENDING, ACTIVE, DISABLED, LOCKED',
    last_auth_at    DATETIME              DEFAULT NULL COMMENT '最后认证时间',
    failed_attempts INT          NOT NULL DEFAULT 0 COMMENT '认证失败次数',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME              DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_bio_auth_user (user_id),
    KEY idx_bio_auth_user_type (user_id, type),
    KEY idx_bio_auth_device (device_id),
    KEY idx_bio_auth_tenant (tenant_id),
    UNIQUE KEY uk_bio_auth_user_device_type (user_id, device_id, type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '生物识别认证表';

-- ------------------------------------------------------------
-- 设备信任管理表（DeviceTrust）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS device_trust (
    id                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    user_id            VARCHAR(64)  NOT NULL COMMENT '用户ID',
    tenant_id          VARCHAR(64)  NOT NULL COMMENT '租户ID',
    device_id          VARCHAR(128) NOT NULL COMMENT '设备ID（唯一标识）',
    device_name        VARCHAR(255)          DEFAULT NULL COMMENT '设备名称',
    device_type        VARCHAR(20)  NOT NULL COMMENT '设备类型：DESKTOP, LAPTOP, MOBILE, TABLET, UNKNOWN',
    device_fingerprint VARCHAR(512)          DEFAULT NULL COMMENT '设备指纹',
    trust_level        VARCHAR(20)  NOT NULL DEFAULT 'UNTRUSTED' COMMENT '信任级别：UNTRUSTED, LOW, MEDIUM, HIGH, FULL',
    status             VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '信任状态：PENDING, ACTIVE, EXPIRED, REVOKED, LOCKED',
    first_auth_at      DATETIME     NOT NULL COMMENT '首次认证时间',
    last_auth_at       DATETIME              DEFAULT NULL COMMENT '最后认证时间',
    last_ip            VARCHAR(64)           DEFAULT NULL COMMENT '最后使用IP',
    last_location      VARCHAR(255)          DEFAULT NULL COMMENT '最后使用位置（国家/城市）',
    auth_count         INT          NOT NULL DEFAULT 0 COMMENT '认证次数',
    risk_score         INT          NOT NULL DEFAULT 0 COMMENT '风险评分（0-100）',
    metadata           TEXT                  DEFAULT NULL COMMENT '设备元数据（JSON格式：操作系统、浏览器等）',
    expires_at         DATETIME              DEFAULT NULL COMMENT '信任过期时间',
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at         DATETIME              DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_device_trust_user (user_id),
    KEY idx_device_trust_device (device_id),
    KEY idx_device_trust_tenant (tenant_id),
    KEY idx_device_trust_status (status),
    KEY idx_device_trust_risk (risk_score),
    KEY idx_device_trust_expires (expires_at),
    UNIQUE KEY uk_device_trust_user_device (user_id, device_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '设备信任管理表';
