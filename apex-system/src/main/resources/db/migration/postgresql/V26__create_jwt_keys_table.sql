-- JWT密钥管理表
-- 用于存储JWT签名密钥的版本信息和生命周期状态
-- 支持密钥轮换策略，保证在密钥切换期间的平滑过渡

CREATE TABLE IF NOT EXISTS jwt_keys (
    id BIGSERIAL PRIMARY KEY,
    key_version VARCHAR(50) NOT NULL UNIQUE,
    encrypted_secret TEXT NOT NULL,
    algorithm VARCHAR(20) NOT NULL DEFAULT 'HS256',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    activated_at TIMESTAMP NULL,
    expires_at TIMESTAMP NULL,
    deprecated_at TIMESTAMP NULL,
    revoked_at TIMESTAMP NULL,
    rotation_reason VARCHAR(50) NULL,
    operator_user_id BIGINT NULL,
    operator_username VARCHAR(100) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark VARCHAR(500) NULL
);

COMMENT ON TABLE jwt_keys IS 'JWT密钥管理表';
COMMENT ON COLUMN jwt_keys.id IS '主键ID';
COMMENT ON COLUMN jwt_keys.key_version IS '密钥版本号，格式：v20260925_001';
COMMENT ON COLUMN jwt_keys.encrypted_secret IS 'AES-256加密后的密钥内容';
COMMENT ON COLUMN jwt_keys.algorithm IS '密钥算法：HS256/RS256';
COMMENT ON COLUMN jwt_keys.status IS '密钥状态：PENDING/ACTIVE/ROTATING/DEPRECATED/REVOKED';
COMMENT ON COLUMN jwt_keys.activated_at IS '密钥启用时间';
COMMENT ON COLUMN jwt_keys.expires_at IS '密钥计划过期时间（通常为启用后90天）';
COMMENT ON COLUMN jwt_keys.deprecated_at IS '密钥实际废弃时间';
COMMENT ON COLUMN jwt_keys.revoked_at IS '密钥吊销时间';
COMMENT ON COLUMN jwt_keys.rotation_reason IS '轮换原因：SCHEDULED/SECURITY_INCIDENT/MANUAL/LEAKED';
COMMENT ON COLUMN jwt_keys.operator_user_id IS '操作人用户ID';
COMMENT ON COLUMN jwt_keys.operator_username IS '操作人用户名';
COMMENT ON COLUMN jwt_keys.created_at IS '创建时间';
COMMENT ON COLUMN jwt_keys.updated_at IS '更新时间';
COMMENT ON COLUMN jwt_keys.remark IS '备注说明';

-- 创建索引
CREATE INDEX idx_jwt_keys_status ON jwt_keys(status);
CREATE INDEX idx_jwt_keys_expires_at ON jwt_keys(expires_at);
CREATE INDEX idx_jwt_keys_activated_at ON jwt_keys(activated_at);

-- 创建更新时间触发器
CREATE OR REPLACE FUNCTION update_jwt_keys_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_jwt_keys_updated_at
BEFORE UPDATE ON jwt_keys
FOR EACH ROW
EXECUTE FUNCTION update_jwt_keys_updated_at();

-- 插入系统初始化说明
INSERT INTO jwt_keys (key_version, encrypted_secret, algorithm, status, rotation_reason, 
                      operator_user_id, operator_username, created_at, updated_at, remark)
VALUES ('v00000000_000', 'PLACEHOLDER', 'HS256', 'REVOKED', 'SYSTEM_INITIALIZATION', 
        0, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 
        '占位记录：系统启动时会自动生成真实的初始密钥')
ON CONFLICT (key_version) DO NOTHING;
