-- V53: 创建 OAuth2 授权表（PostgreSQL）
-- 变更内容：存储 OAuth2 授权码、access token、refresh token 的哈希值，
--           支撑标准 OAuth2 授权服务器端点。
CREATE TABLE oauth2_authorization (
    id VARCHAR(100) PRIMARY KEY,
    registered_client_id VARCHAR(100) NOT NULL,
    client_id VARCHAR(100) NOT NULL,
    principal_name VARCHAR(200) NOT NULL,
    authorization_grant_type VARCHAR(50) NOT NULL,
    authorized_scopes VARCHAR(1000) NOT NULL,
    redirect_uri VARCHAR(1000),
    code_challenge VARCHAR(128),
    code_challenge_method VARCHAR(10),
    authorization_code_hash CHAR(64) UNIQUE,
    authorization_code_expires_at TIMESTAMP NULL,
    access_token_hash CHAR(64) UNIQUE,
    access_token_value TEXT,
    access_token_expires_at TIMESTAMP NULL,
    refresh_token_hash CHAR(64) UNIQUE,
    refresh_token_value TEXT,
    refresh_token_expires_at TIMESTAMP NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_oauth2_authorization_client ON oauth2_authorization(client_id);
CREATE INDEX idx_oauth2_authorization_principal ON oauth2_authorization(principal_name);
