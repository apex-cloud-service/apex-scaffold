-- JWT密钥管理表
-- 用于存储JWT签名密钥的版本信息和生命周期状态
-- 支持密钥轮换策略，保证在密钥切换期间的平滑过渡

CREATE TABLE IF NOT EXISTS jwt_keys (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    key_version VARCHAR(50) NOT NULL UNIQUE COMMENT '密钥版本号，格式：v20260925_001',
    encrypted_secret TEXT NOT NULL COMMENT 'AES-256加密后的密钥内容',
    algorithm VARCHAR(20) NOT NULL DEFAULT 'HS256' COMMENT '密钥算法：HS256/RS256',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '密钥状态：PENDING/ACTIVE/ROTATING/DEPRECATED/REVOKED',
    activated_at DATETIME NULL COMMENT '密钥启用时间',
    expires_at DATETIME NULL COMMENT '密钥计划过期时间（通常为启用后90天）',
    deprecated_at DATETIME NULL COMMENT '密钥实际废弃时间',
    revoked_at DATETIME NULL COMMENT '密钥吊销时间',
    rotation_reason VARCHAR(50) NULL COMMENT '轮换原因：SCHEDULED/SECURITY_INCIDENT/MANUAL/LEAKED',
    operator_user_id BIGINT NULL COMMENT '操作人用户ID',
    operator_username VARCHAR(100) NULL COMMENT '操作人用户名',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    remark VARCHAR(500) NULL COMMENT '备注说明',
    
    INDEX idx_status (status) COMMENT '密钥状态索引',
    INDEX idx_expires_at (expires_at) COMMENT '过期时间索引，用于定时任务查询即将过期的密钥',
    INDEX idx_activated_at (activated_at) COMMENT '启用时间索引，用于查询轮换中的密钥'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='JWT密钥管理表';

-- 插入系统初始化说明
INSERT INTO jwt_keys (key_version, encrypted_secret, algorithm, status, rotation_reason, 
                      operator_user_id, operator_username, created_at, updated_at, remark)
VALUES ('v00000000_000', 'PLACEHOLDER', 'HS256', 'REVOKED', 'SYSTEM_INITIALIZATION', 
        0, 'SYSTEM', NOW(), NOW(), 
        '占位记录：系统启动时会自动生成真实的初始密钥')
ON DUPLICATE KEY UPDATE id=id;
