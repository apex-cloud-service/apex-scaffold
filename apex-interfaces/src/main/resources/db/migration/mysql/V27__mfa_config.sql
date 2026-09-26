-- MFA配置表
CREATE TABLE IF NOT EXISTS mfa_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    user_id VARCHAR(64) NOT NULL COMMENT '用户ID',
    tenant_id VARCHAR(64) NOT NULL COMMENT '租户ID',
    type VARCHAR(20) NOT NULL COMMENT 'MFA类型：TOTP, SMS, EMAIL, BACKUP_CODE',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'MFA状态：PENDING, ACTIVE, DISABLED, LOCKED',
    secret TEXT COMMENT 'TOTP密钥（加密存储）',
    backup_codes TEXT COMMENT '备用验证码（加密存储，逗号分隔）',
    device_info VARCHAR(255) COMMENT '设备信息（手机号/邮箱等）',
    last_verified_at DATETIME COMMENT '最后验证时间',
    failed_attempts INT NOT NULL DEFAULT 0 COMMENT '验证失败次数',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME COMMENT '更新时间',
    INDEX idx_user_id (user_id),
    INDEX idx_user_type (user_id, type),
    INDEX idx_tenant_id (tenant_id),
    UNIQUE KEY uk_user_type (user_id, type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='多因素认证配置表';
