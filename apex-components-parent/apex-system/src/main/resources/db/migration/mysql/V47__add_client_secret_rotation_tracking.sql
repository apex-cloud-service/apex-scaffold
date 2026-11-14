-- V27: 添加OAuth2 Client Secret轮换追踪字段
-- 用于支持密钥定期轮换和安全审计

-- 为 oauth2_registered_client 表添加密钥轮换字段
ALTER TABLE oauth2_registered_client
ADD COLUMN previous_client_secret VARCHAR(500) NULL COMMENT '上一个Client Secret(已加密)' AFTER client_secret_expires_at,
ADD COLUMN secret_rotated_at DATETIME NULL COMMENT '密钥轮换时间' AFTER previous_client_secret,
ADD COLUMN secret_rotation_count INT DEFAULT 0 COMMENT '密钥轮换次数' AFTER secret_rotated_at,
ADD INDEX idx_secret_expires_at (client_secret_expires_at),
ADD INDEX idx_secret_rotated_at (secret_rotated_at);

-- 为现有客户端初始化轮换计数
UPDATE oauth2_registered_client 
SET secret_rotation_count = 0
WHERE secret_rotation_count IS NULL;
