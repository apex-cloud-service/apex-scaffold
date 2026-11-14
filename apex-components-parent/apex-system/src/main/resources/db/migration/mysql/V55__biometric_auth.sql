-- 生物识别认证表
CREATE TABLE IF NOT EXISTS biometric_auth (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    user_id VARCHAR(64) NOT NULL COMMENT '用户ID',
    tenant_id VARCHAR(64) NOT NULL COMMENT '租户ID',
    type VARCHAR(20) NOT NULL COMMENT '生物识别类型：FINGERPRINT, FACE_ID, IRIS, VOICE, WEBAUTHN',
    template_id TEXT COMMENT '生物特征模板ID（加密存储）',
    device_id VARCHAR(128) NOT NULL COMMENT '设备ID（绑定设备）',
    device_name VARCHAR(255) COMMENT '设备名称',
    public_key TEXT COMMENT '公钥（用于WebAuthn验证签名）',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '认证状态：PENDING, ACTIVE, DISABLED, LOCKED',
    last_auth_at DATETIME COMMENT '最后认证时间',
    failed_attempts INT NOT NULL DEFAULT 0 COMMENT '认证失败次数',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME COMMENT '更新时间',
    INDEX idx_user_id (user_id),
    INDEX idx_user_type (user_id, type),
    INDEX idx_device_id (device_id),
    INDEX idx_tenant_id (tenant_id),
    UNIQUE KEY uk_user_device_type (user_id, device_id, type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生物识别认证表';
