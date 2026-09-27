-- V50: 创建 TOTP 和 MFA 配置表
-- 多因素认证（MFA）支持，包括 TOTP、短信验证码、邮件验证码、WebAuthn

-- TOTP 配置表
CREATE TABLE IF NOT EXISTS auth_totp (
    id                  BIGINT AUTO_INCREMENT COMMENT '主键ID',
    tenant_id           VARCHAR(64) NOT NULL COMMENT '租户ID',
    user_id             BIGINT NOT NULL COMMENT '用户ID',
    secret              VARCHAR(256) NOT NULL COMMENT 'TOTP密钥（加密存储）',
    verified            TINYINT NOT NULL DEFAULT 0 COMMENT '是否已验证（0-未验证, 1-已验证）',
    backup_codes        TEXT COMMENT '备份码列表（加密存储，逗号分隔）',
    used_backup_codes   TEXT COMMENT '已使用的备份码列表（逗号分隔）',
    enabled             TINYINT NOT NULL DEFAULT 0 COMMENT '启用状态（0-禁用, 1-启用）',
    last_verified_at    DATETIME COMMENT '最后验证时间',
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    version             INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    deleted             TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除（0-未删除, 1-已删除）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_user (tenant_id, user_id, deleted),
    KEY idx_user_id (user_id),
    KEY idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='TOTP多因素认证配置表';

-- MFA 配置表
CREATE TABLE IF NOT EXISTS auth_mfa_config (
    id          BIGINT AUTO_INCREMENT COMMENT '主键ID',
    tenant_id   VARCHAR(64) NOT NULL COMMENT '租户ID',
    user_id     BIGINT NOT NULL COMMENT '用户ID',
    mfa_type    VARCHAR(20) NOT NULL COMMENT 'MFA类型（TOTP, SMS, EMAIL, WEBAUTHN）',
    enabled     TINYINT NOT NULL DEFAULT 0 COMMENT '是否启用（0-禁用, 1-启用）',
    required    TINYINT NOT NULL DEFAULT 0 COMMENT '是否必需（0-非必需, 1-必需）',
    priority    INT NOT NULL DEFAULT 1 COMMENT '优先级（数值越小优先级越高）',
    metadata    TEXT COMMENT '配置元数据（JSON格式）',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    version     INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    deleted     TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除（0-未删除, 1-已删除）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_user_type (tenant_id, user_id, mfa_type, deleted),
    KEY idx_user_id (user_id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_mfa_type (mfa_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MFA多因素认证配置表';

INSERT IGNORE INTO auth_permission
    (id, tenant_id, code, name, resource, action, description, enabled, created_at, updated_at)
VALUES
    (5001, 'default', 'mfa:setup', '设置MFA', '/api/v1/mfa/setup', 'POST', '设置TOTP多因素认证', 1, NOW(), NOW()),
    (5002, 'default', 'mfa:verify', '验证MFA', '/api/v1/mfa/verify', 'POST', '验证TOTP验证码', 1, NOW(), NOW()),
    (5003, 'default', 'mfa:verify-code', '验证MFA码', '/api/v1/mfa/verify-code', 'POST', '登录时验证TOTP', 1, NOW(), NOW()),
    (5004, 'default', 'mfa:verify-backup', '验证备份码', '/api/v1/mfa/verify-backup', 'POST', '验证备份码', 1, NOW(), NOW()),
    (5005, 'default', 'mfa:disable', '禁用MFA', '/api/v1/mfa/disable', 'POST', '禁用多因素认证', 1, NOW(), NOW()),
    (5006, 'default', 'mfa:status', '查询MFA状态', '/api/v1/mfa/status', 'GET', '查询MFA启用状态', 1, NOW(), NOW());

INSERT IGNORE INTO auth_role_permission (id, tenant_id, role_id, permission_id, created_at)
VALUES
    (6301, 'default', 1, 5001, NOW()),
    (6302, 'default', 1, 5002, NOW()),
    (6303, 'default', 1, 5003, NOW()),
    (6304, 'default', 1, 5004, NOW()),
    (6305, 'default', 1, 5005, NOW()),
    (6306, 'default', 1, 5006, NOW());
