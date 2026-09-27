-- ============================================================
-- V48: 新增认证配置表与配置、审计菜单权限（MySQL）
-- 变更内容：创建 auth_config 认证配置表；以 INSERT IGNORE 幂等地
--           初始化认证配置与审计相关权限编码、管理员角色权限关联
--           及默认系统策略配置。
-- ============================================================
CREATE TABLE IF NOT EXISTS auth_config (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    config_type VARCHAR(64) NOT NULL,
    config_key VARCHAR(128) NOT NULL,
    config_value TEXT NOT NULL,
    description VARCHAR(500),
    enabled TINYINT NOT NULL DEFAULT 1,
    is_default TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    UNIQUE KEY uk_auth_config_tenant_type_key (tenant_id, config_type, config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO auth_permission
    (id, tenant_id, code, name, resource, action, description, enabled, created_at, updated_at)
VALUES
    (21, 'default', 'auth:config:query', '查询认证配置', 'auth-config', 'read', '查询认证配置列表和详情', 1, NOW(), NOW()),
    (22, 'default', 'auth:config:create', '创建认证配置', 'auth-config', 'create', '创建新的认证配置', 1, NOW(), NOW()),
    (23, 'default', 'auth:config:update', '更新认证配置', 'auth-config', 'update', '更新认证配置', 1, NOW(), NOW()),
    (24, 'default', 'auth:config:delete', '删除认证配置', 'auth-config', 'delete', '删除认证配置', 1, NOW(), NOW()),
    (25, 'default', 'auth:audit:query', '查询审计日志', 'auth-audit', 'read', '查询审计日志列表和详情', 1, NOW(), NOW()),
    (26, 'default', 'auth:audit:export', '导出审计日志', 'auth-audit', 'export', '导出审计日志数据', 1, NOW(), NOW()),
    (27, 'default', 'auth:audit:cleanup', '清理审计日志', 'auth-audit', 'delete', '清理过期的审计日志', 1, NOW(), NOW()),
    (28, 'default', 'auth:audit:stats', '统计审计数据', 'auth-audit', 'read', '统计审计日志数据', 1, NOW(), NOW());

INSERT IGNORE INTO auth_role_permission (id, tenant_id, role_id, permission_id, created_at)
VALUES
    (6203, 'default', 1, 21, NOW()),
    (6204, 'default', 1, 22, NOW()),
    (6205, 'default', 1, 23, NOW()),
    (6206, 'default', 1, 24, NOW()),
    (6207, 'default', 1, 25, NOW()),
    (6208, 'default', 1, 26, NOW()),
    (6209, 'default', 1, 27, NOW()),
    (6210, 'default', 1, 28, NOW());

INSERT IGNORE INTO auth_config
    (id, tenant_id, config_type, config_key, config_value, description, enabled, is_default, version, created_at, updated_at, created_by, updated_by)
VALUES
    (4001, 'default', 'PASSWORD_POLICY', 'default', '{"minLength":8,"maxLength":32,"requireUppercase":true,"requireLowercase":true,"requireDigit":true,"requireSpecialChar":true,"maxAge":90,"historyCount":5,"maxFailedAttempts":5,"lockoutDuration":1800}', '默认密码策略', 1, 1, 1, NOW(), NOW(), 1, 1),
    (4002, 'default', 'SESSION_POLICY', 'default', '{"maxConcurrentSessions":5,"sessionTimeout":3600,"idleTimeout":1800,"extendOnActivity":true,"singleDeviceOnly":false,"requireReauthForSensitiveOps":true,"rememberMeDuration":604800}', '默认会话策略', 1, 1, 1, NOW(), NOW(), 1, 1),
    (4003, 'default', 'SECURITY_POLICY', 'default', '{"enableIpWhitelist":false,"ipWhitelist":[],"enableIpBlacklist":true,"enableRateLimiting":true,"maxRequestsPerMinute":100,"enableCaptchaOnFailedLogin":true,"captchaThreshold":3,"enableAuditLog":true,"auditLogRetentionDays":90}', '默认安全策略', 1, 1, 1, NOW(), NOW(), 1, 1),
    (4004, 'default', 'MFA_POLICY', 'default', '{"enableMfa":false,"mfaRequired":false,"allowedMethods":["TOTP","SMS","EMAIL"],"defaultMethod":"TOTP","totpIssuer":"Apex Scaffold","totpPeriod":30,"totpDigits":6,"smsValidityMinutes":5,"emailValidityMinutes":10}', '默认MFA策略', 1, 1, 1, NOW(), NOW(), 1, 1);
