-- V54: 为 OAuth2 授权表增加租户隔离（MySQL）
-- 变更内容：新增 tenant_id 列及联合索引，并统一时间字段类型为 DATETIME。
ALTER TABLE oauth2_authorization ADD COLUMN tenant_id VARCHAR(100) NOT NULL DEFAULT 'default';
CREATE INDEX idx_oauth2_tenant_client ON oauth2_authorization (tenant_id, client_id);
ALTER TABLE oauth2_authorization
    MODIFY authorization_code_expires_at DATETIME NULL,
    MODIFY access_token_expires_at DATETIME NULL,
    MODIFY refresh_token_expires_at DATETIME NULL,
    MODIFY created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    MODIFY updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
