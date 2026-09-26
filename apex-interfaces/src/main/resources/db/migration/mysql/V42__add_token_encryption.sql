-- V42__add_token_encryption.sql
-- 添加Token加密存储支持
-- 
-- 变更内容：
-- 1. 为oauth2_authorization表添加加密字段
-- 2. 添加Token哈希索引用于快速查找
-- 3. 添加Token Family字段支持轮换机制
-- 4. 添加撤销时间戳字段
--
-- 向后兼容：保留原有字段，逐步迁移

-- 1. 为oauth2_authorization表添加加密和安全字段
ALTER TABLE oauth2_authorization 
ADD COLUMN access_token_hash VARCHAR(64) COMMENT 'Access Token SHA256哈希值（用于快速查找）',
ADD COLUMN access_token_encrypted TEXT COMMENT 'AES-256-GCM加密的Access Token',
ADD COLUMN access_token_iv VARCHAR(32) COMMENT 'Access Token加密向量',
ADD COLUMN refresh_token_hash VARCHAR(64) COMMENT 'Refresh Token SHA256哈希值',
ADD COLUMN refresh_token_encrypted TEXT COMMENT 'AES-256-GCM加密的Refresh Token',
ADD COLUMN refresh_token_iv VARCHAR(32) COMMENT 'Refresh Token加密向量',
ADD COLUMN token_family VARCHAR(64) COMMENT 'Token Family ID（用于轮换检测）',
ADD COLUMN previous_jti VARCHAR(100) COMMENT '前一个Token的JTI（轮换链）',
ADD COLUMN revoked_at DATETIME NULL COMMENT 'Token撤销时间',
ADD COLUMN rotation_count INT DEFAULT 0 COMMENT 'Token轮换次数';

-- 2. 添加索引优化查询性能
CREATE INDEX idx_access_token_hash ON oauth2_authorization(access_token_hash);
CREATE INDEX idx_refresh_token_hash ON oauth2_authorization(refresh_token_hash);
CREATE INDEX idx_token_family ON oauth2_authorization(token_family);
CREATE INDEX idx_revoked_at ON oauth2_authorization(revoked_at);

-- 3. 为token表添加相同字段（如果存在token表）
-- 检查token表是否存在
SET @table_exists = (
    SELECT COUNT(*) 
    FROM information_schema.tables 
    WHERE table_schema = DATABASE() 
    AND table_name = 'token'
);

-- 如果token表存在，添加相同字段
SET @alter_sql = IF(
    @table_exists > 0,
    'ALTER TABLE token 
     ADD COLUMN IF NOT EXISTS access_token_hash VARCHAR(64) COMMENT ''Access Token SHA256哈希'',
     ADD COLUMN IF NOT EXISTS access_token_encrypted TEXT COMMENT ''加密的Access Token'',
     ADD COLUMN IF NOT EXISTS access_token_iv VARCHAR(32) COMMENT ''加密向量'',
     ADD COLUMN IF NOT EXISTS refresh_token_hash VARCHAR(64) COMMENT ''Refresh Token哈希'',
     ADD COLUMN IF NOT EXISTS refresh_token_encrypted TEXT COMMENT ''加密的Refresh Token'',
     ADD COLUMN IF NOT EXISTS refresh_token_iv VARCHAR(32) COMMENT ''Refresh Token IV'',
     ADD COLUMN IF NOT EXISTS token_family VARCHAR(64) COMMENT ''Token Family ID'',
     ADD COLUMN IF NOT EXISTS previous_jti VARCHAR(100) COMMENT ''前一个JTI'',
     ADD COLUMN IF NOT EXISTS revoked_at DATETIME NULL COMMENT ''撤销时间'',
     ADD COLUMN IF NOT EXISTS rotation_count INT DEFAULT 0 COMMENT ''轮换次数''',
    'SELECT ''token表不存在，跳过'' AS result'
);

PREPARE stmt FROM @alter_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4. 为token表添加索引（如果表存在）
SET @index_sql = IF(
    @table_exists > 0,
    'CREATE INDEX IF NOT EXISTS idx_access_token_hash ON token(access_token_hash);
     CREATE INDEX IF NOT EXISTS idx_refresh_token_hash ON token(refresh_token_hash);
     CREATE INDEX IF NOT EXISTS idx_token_family ON token(token_family);
     CREATE INDEX IF NOT EXISTS idx_revoked_at ON token(revoked_at)',
    'SELECT ''token表不存在，跳过索引创建'' AS result'
);

PREPARE stmt FROM @index_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 5. 创建Token轮换历史表（用于审计和异常检测）
CREATE TABLE IF NOT EXISTS token_rotation_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    tenant_id VARCHAR(50) COMMENT '租户ID',
    token_family VARCHAR(64) NOT NULL COMMENT 'Token Family ID',
    old_jti VARCHAR(100) NOT NULL COMMENT '旧Token的JTI',
    new_jti VARCHAR(100) NOT NULL COMMENT '新Token的JTI',
    rotation_reason VARCHAR(50) DEFAULT 'NORMAL_REFRESH' COMMENT '轮换原因: NORMAL_REFRESH, SECURITY_CONCERN, EXPIRED',
    client_ip VARCHAR(45) COMMENT '客户端IP',
    user_agent VARCHAR(500) COMMENT 'User Agent',
    rotated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '轮换时间',
    INDEX idx_user_id (user_id),
    INDEX idx_token_family (token_family),
    INDEX idx_rotated_at (rotated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Token轮换历史记录';

-- 6. 创建Token安全事件表（记录安全异常）
CREATE TABLE IF NOT EXISTS token_security_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_type VARCHAR(50) NOT NULL COMMENT '事件类型: TOKEN_REUSE, MULTIPLE_IP, RAPID_REFRESH, FAMILY_REVOKED',
    severity VARCHAR(20) NOT NULL COMMENT '严重程度: LOW, MEDIUM, HIGH, CRITICAL',
    user_id BIGINT COMMENT '用户ID',
    tenant_id VARCHAR(50) COMMENT '租户ID',
    token_family VARCHAR(64) COMMENT 'Token Family ID',
    jti VARCHAR(100) COMMENT 'Token JTI',
    description TEXT COMMENT '事件描述',
    client_ip VARCHAR(45) COMMENT '客户端IP',
    user_agent VARCHAR(500) COMMENT 'User Agent',
    additional_data JSON COMMENT '附加数据',
    detected_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '检测时间',
    handled BOOLEAN DEFAULT FALSE COMMENT '是否已处理',
    handled_at DATETIME COMMENT '处理时间',
    INDEX idx_event_type (event_type),
    INDEX idx_severity (severity),
    INDEX idx_user_id (user_id),
    INDEX idx_detected_at (detected_at),
    INDEX idx_handled (handled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Token安全事件记录';

-- 7. 备份现有Token数据（如果需要）
-- 注意：由于包含敏感数据，实际生产环境需要加密迁移
-- CREATE TABLE oauth2_authorization_backup_v41 AS 
-- SELECT * FROM oauth2_authorization WHERE id IS NOT NULL;

-- 8. 添加数据迁移说明注释
-- 重要：现有明文Token需要在应用层迁移为加密存储
-- 迁移步骤：
-- 1. 应用部署新版本（支持加密和明文双读）
-- 2. 运行数据迁移脚本（将明文Token加密）
-- 3. 验证所有Token已加密
-- 4. 在下一个版本中移除明文字段

-- 9. 创建视图用于监控Token健康状态
CREATE OR REPLACE VIEW v_token_health AS
SELECT 
    DATE(issued_at) as issue_date,
    COUNT(*) as total_tokens,
    SUM(CASE WHEN access_token_encrypted IS NOT NULL THEN 1 ELSE 0 END) as encrypted_tokens,
    SUM(CASE WHEN status = 'ACTIVE' THEN 1 ELSE 0 END) as active_tokens,
    SUM(CASE WHEN status = 'REVOKED' THEN 1 ELSE 0 END) as revoked_tokens,
    SUM(CASE WHEN status = 'ROTATED' THEN 1 ELSE 0 END) as rotated_tokens,
    AVG(rotation_count) as avg_rotation_count,
    COUNT(DISTINCT token_family) as active_families
FROM oauth2_authorization
WHERE issued_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)
GROUP BY DATE(issued_at)
ORDER BY issue_date DESC;

-- 10. 添加配置表用于密钥版本管理
CREATE TABLE IF NOT EXISTS encryption_key_version (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    key_version INT NOT NULL UNIQUE COMMENT '密钥版本号',
    key_id VARCHAR(100) NOT NULL COMMENT '密钥标识符（KMS Key ID）',
    algorithm VARCHAR(50) DEFAULT 'AES-256-GCM' COMMENT '加密算法',
    active BOOLEAN DEFAULT TRUE COMMENT '是否为当前活跃密钥',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    rotated_at DATETIME COMMENT '轮换时间',
    expires_at DATETIME COMMENT '过期时间',
    INDEX idx_key_version (key_version),
    INDEX idx_active (active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='加密密钥版本管理';

-- 插入初始密钥版本记录
INSERT INTO encryption_key_version (key_version, key_id, active) 
VALUES (1, 'default-key-v1', TRUE)
ON DUPLICATE KEY UPDATE active = TRUE;
