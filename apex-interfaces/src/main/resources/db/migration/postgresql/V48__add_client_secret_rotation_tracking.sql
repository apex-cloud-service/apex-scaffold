-- V48: 添加OAuth2 Client Secret轮换追踪字段
-- 用于支持密钥定期轮换和安全审计

-- 为 oauth2_registered_client 表添加密钥轮换字段
ALTER TABLE oauth2_registered_client
ADD COLUMN IF NOT EXISTS previous_client_secret VARCHAR(500) NULL,
ADD COLUMN IF NOT EXISTS secret_rotated_at TIMESTAMP NULL,
ADD COLUMN IF NOT EXISTS secret_rotation_count INTEGER DEFAULT 0;

COMMENT ON COLUMN oauth2_registered_client.previous_client_secret IS '上一个Client Secret(已加密)';
COMMENT ON COLUMN oauth2_registered_client.secret_rotated_at IS '密钥轮换时间';
COMMENT ON COLUMN oauth2_registered_client.secret_rotation_count IS '密钥轮换次数';

CREATE INDEX IF NOT EXISTS idx_secret_expires_at ON oauth2_registered_client(client_secret_expires_at);
CREATE INDEX IF NOT EXISTS idx_secret_rotated_at ON oauth2_registered_client(secret_rotated_at);

-- 为现有客户端初始化轮换计数
UPDATE oauth2_registered_client 
SET secret_rotation_count = 0
WHERE secret_rotation_count IS NULL;
