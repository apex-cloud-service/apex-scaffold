-- =====================================================
-- 安全增强数据库迁移脚本
-- 版本: V26
-- 描述: 为认证授权系统添加安全增强字段
-- =====================================================

-- 1. 为 token 表添加安全相关字段
ALTER TABLE token
    ADD COLUMN IF NOT EXISTS is_revoked BOOLEAN DEFAULT FALSE COMMENT '是否已撤销',
    ADD COLUMN IF NOT EXISTS revoke_reason VARCHAR(255) COMMENT '撤销原因',
    ADD COLUMN IF NOT EXISTS revoked_at DATETIME COMMENT '撤销时间',
    ADD COLUMN IF NOT EXISTS last_used_at DATETIME COMMENT '最后使用时间',
    ADD COLUMN IF NOT EXISTS last_used_ip VARCHAR(64) COMMENT '最后使用IP',
    ADD COLUMN IF NOT EXISTS device_fingerprint VARCHAR(255) COMMENT '设备指纹',
    ADD COLUMN IF NOT EXISTS is_encrypted BOOLEAN DEFAULT TRUE COMMENT 'Token是否加密存储';

-- 2. 为 token 表添加索引
CREATE INDEX IF NOT EXISTS idx_token_is_revoked ON token(is_revoked);
CREATE INDEX IF NOT EXISTS idx_token_last_used_at ON token(last_used_at);
CREATE INDEX IF NOT EXISTS idx_token_device_fingerprint ON token(device_fingerprint);

-- 3. 为 user 表添加安全相关字段（如果不存在）
ALTER TABLE user
    ADD COLUMN IF NOT EXISTS is_locked BOOLEAN DEFAULT FALSE COMMENT '账户是否被锁定',
    ADD COLUMN IF NOT EXISTS lock_reason VARCHAR(255) COMMENT '锁定原因',
    ADD COLUMN IF NOT EXISTS locked_at DATETIME COMMENT '锁定时间',
    ADD COLUMN IF NOT EXISTS locked_until DATETIME COMMENT '锁定截止时间（null表示永久锁定）',
    ADD COLUMN IF NOT EXISTS failed_login_attempts INT DEFAULT 0 COMMENT '连续登录失败次数',
    ADD COLUMN IF NOT EXISTS last_failed_login_at DATETIME COMMENT '最后失败登录时间',
    ADD COLUMN IF NOT EXISTS password_changed_at DATETIME COMMENT '密码最后修改时间',
    ADD COLUMN IF NOT EXISTS password_expires_at DATETIME COMMENT '密码过期时间',
    ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN DEFAULT FALSE COMMENT '是否必须修改密码';

-- 4. 为 user 表添加索引
CREATE INDEX IF NOT EXISTS idx_user_is_locked ON user(is_locked);
CREATE INDEX IF NOT EXISTS idx_user_password_expires_at ON user(password_expires_at);

-- 5. 创建密码历史表（用于防止密码重用）
CREATE TABLE IF NOT EXISTS password_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    
    INDEX idx_user_id (user_id),
    INDEX idx_tenant_id (tenant_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='密码历史记录表';

-- 6. 创建安全审计日志表
CREATE TABLE IF NOT EXISTS security_audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    event_type VARCHAR(50) NOT NULL COMMENT '事件类型',
    user_id BIGINT COMMENT '用户ID',
    username VARCHAR(100) COMMENT '用户名',
    tenant_id BIGINT COMMENT '租户ID',
    client_ip VARCHAR(64) COMMENT '客户端IP',
    user_agent VARCHAR(500) COMMENT 'User-Agent',
    device_id VARCHAR(255) COMMENT '设备ID',
    success BOOLEAN NOT NULL COMMENT '是否成功',
    failure_reason VARCHAR(500) COMMENT '失败原因',
    additional_info TEXT COMMENT '附加信息（JSON格式）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    
    INDEX idx_event_type (event_type),
    INDEX idx_user_id (user_id),
    INDEX idx_tenant_id (tenant_id),
    INDEX idx_client_ip (client_ip),
    INDEX idx_created_at (created_at),
    INDEX idx_success (success)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='安全审计日志表';

-- 7. 创建JWT密钥版本表（用于密钥轮换）
CREATE TABLE IF NOT EXISTS jwt_key_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    version VARCHAR(50) NOT NULL UNIQUE COMMENT '密钥版本',
    algorithm VARCHAR(20) NOT NULL COMMENT '签名算法 (HS256/RS256)',
    key_data TEXT NOT NULL COMMENT '密钥数据（加密存储）',
    public_key TEXT COMMENT '公钥（仅RSA算法）',
    is_active BOOLEAN DEFAULT TRUE COMMENT '是否为当前活跃密钥',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    expires_at DATETIME COMMENT '过期时间',
    rotated_at DATETIME COMMENT '轮换时间',
    
    INDEX idx_version (version),
    INDEX idx_is_active (is_active),
    INDEX idx_created_at (created_at),
    INDEX idx_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='JWT密钥版本表';

-- 8. 创建OAuth2客户端密钥历史表
CREATE TABLE IF NOT EXISTS oauth2_client_secret_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    client_id VARCHAR(100) NOT NULL COMMENT '客户端ID',
    secret_hash VARCHAR(255) NOT NULL COMMENT '密钥哈希',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    expires_at DATETIME COMMENT '过期时间',
    rotated_at DATETIME COMMENT '轮换时间',
    is_active BOOLEAN DEFAULT TRUE COMMENT '是否为当前活跃密钥',
    
    INDEX idx_client_id (client_id),
    INDEX idx_is_active (is_active),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAuth2客户端密钥历史表';

-- 9. 创建登录历史表
CREATE TABLE IF NOT EXISTS login_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    username VARCHAR(100) NOT NULL COMMENT '用户名',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    login_type VARCHAR(20) NOT NULL COMMENT '登录类型 (PASSWORD/SSO/OAUTH2)',
    success BOOLEAN NOT NULL COMMENT '是否成功',
    failure_reason VARCHAR(255) COMMENT '失败原因',
    client_ip VARCHAR(64) NOT NULL COMMENT '客户端IP',
    user_agent VARCHAR(500) COMMENT 'User-Agent',
    device_id VARCHAR(255) COMMENT '设备ID',
    device_type VARCHAR(50) COMMENT '设备类型',
    location VARCHAR(255) COMMENT '登录地点',
    is_suspicious BOOLEAN DEFAULT FALSE COMMENT '是否可疑登录',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    
    INDEX idx_user_id (user_id),
    INDEX idx_tenant_id (tenant_id),
    INDEX idx_success (success),
    INDEX idx_client_ip (client_ip),
    INDEX idx_created_at (created_at),
    INDEX idx_is_suspicious (is_suspicious)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录历史表';

-- 10. 创建设备信任表
CREATE TABLE IF NOT EXISTS trusted_device (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    device_id VARCHAR(255) NOT NULL COMMENT '设备ID',
    device_fingerprint VARCHAR(255) NOT NULL COMMENT '设备指纹',
    device_name VARCHAR(100) COMMENT '设备名称',
    device_type VARCHAR(50) COMMENT '设备类型',
    last_ip VARCHAR(64) COMMENT '最后使用IP',
    last_location VARCHAR(255) COMMENT '最后位置',
    trust_score INT DEFAULT 100 COMMENT '信任分数 (0-100)',
    is_trusted BOOLEAN DEFAULT TRUE COMMENT '是否信任',
    trusted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '信任时间',
    last_used_at DATETIME COMMENT '最后使用时间',
    expires_at DATETIME COMMENT '过期时间',
    
    UNIQUE KEY uk_user_device (user_id, device_id),
    INDEX idx_tenant_id (tenant_id),
    INDEX idx_device_fingerprint (device_fingerprint),
    INDEX idx_is_trusted (is_trusted),
    INDEX idx_last_used_at (last_used_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='可信设备表';

-- 11. 为 oauth2_authorization 表添加安全字段
ALTER TABLE oauth2_authorization
    ADD COLUMN IF NOT EXISTS device_id VARCHAR(255) COMMENT '设备ID',
    ADD COLUMN IF NOT EXISTS device_fingerprint VARCHAR(255) COMMENT '设备指纹',
    ADD COLUMN IF NOT EXISTS client_ip VARCHAR(64) COMMENT '客户端IP',
    ADD COLUMN IF NOT EXISTS is_revoked BOOLEAN DEFAULT FALSE COMMENT '是否已撤销',
    ADD COLUMN IF NOT EXISTS revoked_at DATETIME COMMENT '撤销时间';

CREATE INDEX IF NOT EXISTS idx_oauth2_auth_device_id ON oauth2_authorization(device_id);
CREATE INDEX IF NOT EXISTS idx_oauth2_auth_is_revoked ON oauth2_authorization(is_revoked);

-- 12. 创建配置加密密钥表
CREATE TABLE IF NOT EXISTS encryption_key (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    key_name VARCHAR(100) NOT NULL UNIQUE COMMENT '密钥名称',
    key_type VARCHAR(50) NOT NULL COMMENT '密钥类型 (TOKEN/CONFIG/JWT)',
    key_data TEXT NOT NULL COMMENT '密钥数据（加密存储）',
    algorithm VARCHAR(50) NOT NULL COMMENT '加密算法',
    version VARCHAR(50) NOT NULL COMMENT '密钥版本',
    is_active BOOLEAN DEFAULT TRUE COMMENT '是否活跃',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    rotated_at DATETIME COMMENT '轮换时间',
    expires_at DATETIME COMMENT '过期时间',
    
    INDEX idx_key_name (key_name),
    INDEX idx_key_type (key_type),
    INDEX idx_is_active (is_active),
    INDEX idx_version (version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='加密密钥表';

-- =====================================================
-- 数据清理和迁移（可选）
-- =====================================================

-- 注意：以下操作会清理现有Token，用户需要重新登录
-- 生产环境请谨慎执行，建议在维护窗口期间执行

-- 13. 清理现有明文Token（可选，根据实际情况决定是否执行）
-- TRUNCATE TABLE token;
-- TRUNCATE TABLE oauth2_authorization;

-- 14. 更新现有用户的密码修改时间（如果字段为空）
UPDATE user 
SET password_changed_at = created_at 
WHERE password_changed_at IS NULL;

-- 15. 设置密码过期策略（可选，90天过期）
-- UPDATE user 
-- SET password_expires_at = DATE_ADD(password_changed_at, INTERVAL 90 DAY)
-- WHERE password_expires_at IS NULL;

-- =====================================================
-- 验证脚本
-- =====================================================

-- 验证 token 表字段
SELECT 
    TABLE_NAME,
    COLUMN_NAME,
    DATA_TYPE,
    COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'token'
  AND COLUMN_NAME IN ('is_revoked', 'is_encrypted', 'device_fingerprint')
ORDER BY ORDINAL_POSITION;

-- 验证新创建的表
SELECT 
    TABLE_NAME,
    TABLE_COMMENT,
    CREATE_TIME
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN (
    'password_history',
    'security_audit_log',
    'jwt_key_version',
    'login_history',
    'trusted_device',
    'encryption_key'
  )
ORDER BY TABLE_NAME;

-- 统计表数据
SELECT 
    'token' as table_name, COUNT(*) as row_count FROM token
UNION ALL
SELECT 'password_history', COUNT(*) FROM password_history
UNION ALL
SELECT 'security_audit_log', COUNT(*) FROM security_audit_log
UNION ALL
SELECT 'login_history', COUNT(*) FROM login_history
UNION ALL
SELECT 'trusted_device', COUNT(*) FROM trusted_device;

-- =====================================================
-- 回滚脚本（如需回滚，请取消注释执行）
-- =====================================================

/*
-- 删除新增的表
DROP TABLE IF EXISTS trusted_device;
DROP TABLE IF EXISTS login_history;
DROP TABLE IF EXISTS oauth2_client_secret_history;
DROP TABLE IF EXISTS jwt_key_version;
DROP TABLE IF EXISTS security_audit_log;
DROP TABLE IF EXISTS password_history;
DROP TABLE IF EXISTS encryption_key;

-- 删除 token 表的新增字段
ALTER TABLE token
    DROP COLUMN IF EXISTS is_revoked,
    DROP COLUMN IF EXISTS revoke_reason,
    DROP COLUMN IF EXISTS revoked_at,
    DROP COLUMN IF EXISTS last_used_at,
    DROP COLUMN IF EXISTS last_used_ip,
    DROP COLUMN IF EXISTS device_fingerprint,
    DROP COLUMN IF EXISTS is_encrypted;

-- 删除 user 表的新增字段
ALTER TABLE user
    DROP COLUMN IF EXISTS is_locked,
    DROP COLUMN IF EXISTS lock_reason,
    DROP COLUMN IF EXISTS locked_at,
    DROP COLUMN IF EXISTS locked_until,
    DROP COLUMN IF EXISTS failed_login_attempts,
    DROP COLUMN IF EXISTS last_failed_login_at,
    DROP COLUMN IF EXISTS password_changed_at,
    DROP COLUMN IF EXISTS password_expires_at,
    DROP COLUMN IF EXISTS must_change_password;

-- 删除 oauth2_authorization 表的新增字段
ALTER TABLE oauth2_authorization
    DROP COLUMN IF EXISTS device_id,
    DROP COLUMN IF EXISTS device_fingerprint,
    DROP COLUMN IF EXISTS client_ip,
    DROP COLUMN IF EXISTS is_revoked,
    DROP COLUMN IF EXISTS revoked_at;
*/
