-- V42 已创建 revoked_at 及其索引，令牌存储为 oauth2_authorization。
ALTER TABLE oauth2_authorization
ADD COLUMN revoked_by VARCHAR(100) NULL COMMENT '撤销操作人(userId或system)',
ADD COLUMN revoke_reason VARCHAR(500) NULL COMMENT '撤销原因(expired/logout/security/manual)',
ADD COLUMN use_count INT DEFAULT 0 COMMENT 'Token使用次数',
ADD INDEX idx_revoked_by (revoked_by);

-- 无历史撤销时间时记录迁移时刻，不将 OAuth2 state 参数解释为撤销状态。
UPDATE oauth2_authorization
SET revoked_at = CURRENT_TIMESTAMP, revoke_reason = 'legacy_revoked'
WHERE is_revoked = TRUE AND revoked_at IS NULL;
