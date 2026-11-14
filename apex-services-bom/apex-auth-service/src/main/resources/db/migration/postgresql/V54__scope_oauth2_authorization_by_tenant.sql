-- V54: 为 OAuth2 授权表增加租户隔离（PostgreSQL）
-- 变更内容：新增 tenant_id 列及联合索引。
ALTER TABLE oauth2_authorization ADD COLUMN tenant_id VARCHAR(100) NOT NULL DEFAULT 'default';
CREATE INDEX idx_oauth2_tenant_client ON oauth2_authorization (tenant_id, client_id);
