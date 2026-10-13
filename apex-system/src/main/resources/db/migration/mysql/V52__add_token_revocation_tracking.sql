-- V26: 添加Token撤销追踪字段
-- 用于审计和安全监控

-- 为 oauth2_authorization 表添加撤销追踪字段
ALTER TABLE oauth2_authorization
ADD COLUMN revoked_at DATETIME NULL COMMENT 'Token撤销时间' AFTER state,
ADD COLUMN revoked_by VARCHAR(100) NULL COMMENT '撤销操作人(userId或system)' AFTER revoked_at,
ADD COLUMN revoke_reason VARCHAR(500) NULL COMMENT '撤销原因(expired/logout/security/manual)' AFTER revoked_by,
ADD INDEX idx_revoked_at (revoked_at),
ADD INDEX idx_revoked_by (revoked_by);

-- 为 auth_token 表添加撤销追踪字段（如果表存在）
ALTER TABLE auth_token
ADD COLUMN revoked_at DATETIME NULL COMMENT 'Token撤销时间' AFTER revoked,
ADD COLUMN revoked_by VARCHAR(100) NULL COMMENT '撤销操作人' AFTER revoked_at,
ADD COLUMN revoke_reason VARCHAR(500) NULL COMMENT '撤销原因' AFTER revoked_by,
ADD COLUMN last_used_at DATETIME NULL COMMENT '最后使用时间' AFTER revoke_reason,
ADD COLUMN use_count INT DEFAULT 0 COMMENT 'Token使用次数' AFTER last_used_at,
ADD INDEX idx_token_revoked_at (revoked_at),
ADD INDEX idx_token_last_used (last_used_at);

-- 更新现有已撤销的Token，设置撤销时间为更新时间
UPDATE oauth2_authorization 
SET revoked_at = updated_at,
    revoke_reason = 'legacy_revoked'
WHERE state = 'REVOKED' 
  AND revoked_at IS NULL;

UPDATE auth_token 
SET revoked_at = updated_at,
    revoke_reason = 'legacy_revoked'
WHERE revoked = true 
  AND revoked_at IS NULL;
