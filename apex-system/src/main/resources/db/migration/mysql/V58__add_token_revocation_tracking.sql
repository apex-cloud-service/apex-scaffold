-- 撤销时间、操作人及使用时间已由 V42/V48/V52 建立，实际存储为 oauth2_authorization。
ALTER TABLE oauth2_authorization
ADD COLUMN revoked_reason VARCHAR(100) NULL COMMENT '撤销原因枚举(USER_LOGOUT/ADMIN_REVOKE/SECURITY_BREACH/PASSWORD_CHANGE/EXPIRED)';

CREATE INDEX idx_oauth2_authorization_revoked_reason ON oauth2_authorization(revoked_reason);
CREATE INDEX idx_oauth2_authorization_last_used_at ON oauth2_authorization(last_used_at);
