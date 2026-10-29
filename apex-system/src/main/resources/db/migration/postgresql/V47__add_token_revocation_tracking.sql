-- system 的令牌存储为 oauth2_authorization，无 auth_token 表。
ALTER TABLE oauth2_authorization
ADD COLUMN revoked_at TIMESTAMP NULL,
ADD COLUMN revoked_by VARCHAR(100) NULL,
ADD COLUMN revoke_reason VARCHAR(500) NULL,
ADD COLUMN last_used_at TIMESTAMP NULL,
ADD COLUMN use_count INTEGER DEFAULT 0;

COMMENT ON COLUMN oauth2_authorization.revoked_at IS 'Token撤销时间';
COMMENT ON COLUMN oauth2_authorization.revoked_by IS '撤销操作人(userId或system)';
COMMENT ON COLUMN oauth2_authorization.revoke_reason IS '撤销原因(expired/logout/security/manual)';

CREATE INDEX idx_revoked_at ON oauth2_authorization(revoked_at);
CREATE INDEX idx_revoked_by ON oauth2_authorization(revoked_by);
-- 既有表没有撤销标志或撤销时间，不从 OAuth2 state 参数推断撤销状态。
