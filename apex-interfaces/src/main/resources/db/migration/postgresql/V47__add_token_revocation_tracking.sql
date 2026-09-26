-- V47: 添加Token撤销追踪字段
-- 用于审计和安全监控

-- 为 oauth2_authorization 表添加撤销追踪字段
ALTER TABLE oauth2_authorization
ADD COLUMN IF NOT EXISTS revoked_at TIMESTAMP NULL,
ADD COLUMN IF NOT EXISTS revoked_by VARCHAR(100) NULL,
ADD COLUMN IF NOT EXISTS revoke_reason VARCHAR(500) NULL;

COMMENT ON COLUMN oauth2_authorization.revoked_at IS 'Token撤销时间';
COMMENT ON COLUMN oauth2_authorization.revoked_by IS '撤销操作人(userId或system)';
COMMENT ON COLUMN oauth2_authorization.revoke_reason IS '撤销原因(expired/logout/security/manual)';

CREATE INDEX IF NOT EXISTS idx_revoked_at ON oauth2_authorization(revoked_at);
CREATE INDEX IF NOT EXISTS idx_revoked_by ON oauth2_authorization(revoked_by);

-- 为 auth_token 表添加撤销追踪字段（如果表存在）
ALTER TABLE auth_token
ADD COLUMN IF NOT EXISTS revoked_at TIMESTAMP NULL,
ADD COLUMN IF NOT EXISTS revoked_by VARCHAR(100) NULL,
ADD COLUMN IF NOT EXISTS revoke_reason VARCHAR(500) NULL,
ADD COLUMN IF NOT EXISTS last_used_at TIMESTAMP NULL,
ADD COLUMN IF NOT EXISTS use_count INTEGER DEFAULT 0;

COMMENT ON COLUMN auth_token.revoked_at IS 'Token撤销时间';
COMMENT ON COLUMN auth_token.revoked_by IS '撤销操作人';
COMMENT ON COLUMN auth_token.revoke_reason IS '撤销原因';
COMMENT ON COLUMN auth_token.last_used_at IS '最后使用时间';
COMMENT ON COLUMN auth_token.use_count IS 'Token使用次数';

CREATE INDEX IF NOT EXISTS idx_token_revoked_at ON auth_token(revoked_at);
CREATE INDEX IF NOT EXISTS idx_token_last_used ON auth_token(last_used_at);

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
