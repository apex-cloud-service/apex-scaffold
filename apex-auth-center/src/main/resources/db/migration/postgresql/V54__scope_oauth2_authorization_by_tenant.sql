ALTER TABLE oauth2_authorization ADD COLUMN tenant_id VARCHAR(100) NOT NULL DEFAULT 'default';
CREATE INDEX idx_oauth2_tenant_client ON oauth2_authorization (tenant_id, client_id);
