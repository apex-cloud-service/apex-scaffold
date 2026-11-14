-- V60: OAuth2 用户授权同意记录（统一认证收口 A5）
-- 幂等性：CREATE TABLE IF NOT EXISTS + 唯一约束，可重复执行。

CREATE TABLE IF NOT EXISTS auth_oauth2_consent (
    id           BIGINT       NOT NULL PRIMARY KEY,
    tenant_id    VARCHAR(64)  NOT NULL,
    principal    VARCHAR(100) NOT NULL,
    client_id    VARCHAR(100) NOT NULL,
    scope        VARCHAR(500) DEFAULT NULL,
    consented_at TIMESTAMP    NOT NULL,
    version      INT          NOT NULL DEFAULT 0,
    deleted      INT          NOT NULL DEFAULT 0,
    create_time  TIMESTAMP    NOT NULL,
    update_time  TIMESTAMP    NOT NULL,
    CONSTRAINT uk_auth_oauth2_consent UNIQUE (tenant_id, principal, client_id)
);

COMMENT ON TABLE auth_oauth2_consent IS 'OAuth2 用户授权同意记录（平台级契约表）';
