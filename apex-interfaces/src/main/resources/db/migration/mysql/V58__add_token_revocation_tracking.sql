-- V28: 添加Token撤销时间戳和审计字段
-- 用于增强Token管理的可追溯性和审计能力

-- 为 oauth2_authorization 表添加撤销时间戳
ALTER TABLE oauth2_authorization
ADD COLUMN revoked_at DATETIME NULL COMMENT 'Token撤销时间';

-- 添加撤销原因字段
ALTER TABLE oauth2_authorization
ADD COLUMN revoked_reason VARCHAR(100) NULL COMMENT '撤销原因(USER_LOGOUT/ADMIN_REVOKE/SECURITY_BREACH/PASSWORD_CHANGE/EXPIRED)';

-- 添加撤销操作人
ALTER TABLE oauth2_authorization
ADD COLUMN revoked_by VARCHAR(100) NULL COMMENT '撤销操作人ID(用户主动登出时为用户自己,管理员强制撤销时为管理员ID)';

-- 为 oauth2_token 表添加类似字段
ALTER TABLE oauth2_token
ADD COLUMN revoked_at DATETIME NULL COMMENT 'Token撤销时间';

ALTER TABLE oauth2_token
ADD COLUMN revoked_reason VARCHAR(100) NULL COMMENT '撤销原因';

ALTER TABLE oauth2_token
ADD COLUMN revoked_by VARCHAR(100) NULL COMMENT '撤销操作人ID';

-- 添加最后使用时间（用于检测Token活跃度）
ALTER TABLE oauth2_token
ADD COLUMN last_used_at DATETIME NULL COMMENT '最后使用时间';

ALTER TABLE oauth2_token
ADD COLUMN last_used_ip VARCHAR(50) NULL COMMENT '最后使用IP';

-- 为性能优化添加索引
CREATE INDEX idx_oauth2_authorization_revoked_at ON oauth2_authorization(revoked_at);
CREATE INDEX idx_oauth2_authorization_revoked_reason ON oauth2_authorization(revoked_reason);

CREATE INDEX idx_oauth2_token_revoked_at ON oauth2_token(revoked_at);
CREATE INDEX idx_oauth2_token_last_used_at ON oauth2_token(last_used_at);

-- 为审计查询优化的复合索引
CREATE INDEX idx_oauth2_token_user_revoked ON oauth2_token(user_id, revoked_at, revoked_reason);

COMMENT ON COLUMN oauth2_authorization.revoked_at IS 'Token撤销时间戳，用于审计追踪';
COMMENT ON COLUMN oauth2_authorization.revoked_reason IS '撤销原因枚举：USER_LOGOUT(用户登出), ADMIN_REVOKE(管理员撤销), SECURITY_BREACH(安全事件), PASSWORD_CHANGE(密码修改), EXPIRED(过期), SESSION_TIMEOUT(会话超时)';
COMMENT ON COLUMN oauth2_authorization.revoked_by IS '撤销操作执行人，可以是用户自己或管理员';

COMMENT ON COLUMN oauth2_token.last_used_at IS 'Token最后使用时间，用于检测长期未使用的Token';
COMMENT ON COLUMN oauth2_token.last_used_ip IS 'Token最后使用IP地址，用于异常检测';
