-- V1: 集成域初始结构（方案 M5 / 批次 E1）
-- 语义与 MySQL 版本一致；全部表含 tenant_id NOT NULL，参与行级隔离。

CREATE TABLE IF NOT EXISTS int_connector_config (
    id             BIGINT       NOT NULL PRIMARY KEY,
    tenant_id      VARCHAR(64)  NOT NULL,
    connector_type VARCHAR(32)  NOT NULL,
    config_key     VARCHAR(64)  NOT NULL,
    config_value   TEXT         NOT NULL,
    status         INT          NOT NULL DEFAULT 1,
    remark         VARCHAR(500) DEFAULT NULL,
    create_time    TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time    TIMESTAMP    NOT NULL DEFAULT NOW(),
    version        INT          NOT NULL DEFAULT 0,
    deleted        INT          NOT NULL DEFAULT 0,
    CONSTRAINT uk_int_connector_config UNIQUE (tenant_id, config_key, deleted)
);
CREATE INDEX IF NOT EXISTS idx_int_connector_type ON int_connector_config (tenant_id, connector_type);

CREATE TABLE IF NOT EXISTS int_credential (
    id              BIGINT       NOT NULL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    credential_key  VARCHAR(64)  NOT NULL,
    credential_type VARCHAR(32)  NOT NULL,
    secret_cipher   TEXT         NOT NULL,
    secret_iv       VARCHAR(64)  NOT NULL,
    version_no      INT          NOT NULL DEFAULT 1,
    rotated_at      TIMESTAMP    DEFAULT NULL,
    create_time     TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time     TIMESTAMP    NOT NULL DEFAULT NOW(),
    deleted         INT          NOT NULL DEFAULT 0,
    CONSTRAINT uk_int_credential UNIQUE (tenant_id, credential_key, deleted)
);

CREATE TABLE IF NOT EXISTS int_outbound_call_log (
    id              BIGINT       NOT NULL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    connector_type  VARCHAR(32)  NOT NULL,
    operation       VARCHAR(64)  NOT NULL,
    idempotency_key VARCHAR(128) DEFAULT NULL,
    status          VARCHAR(16)  NOT NULL,
    duration_ms     BIGINT       DEFAULT NULL,
    error_message   VARCHAR(1000) DEFAULT NULL,
    create_time     TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_int_outbound_tenant ON int_outbound_call_log (tenant_id, create_time);
CREATE INDEX IF NOT EXISTS idx_int_outbound_idem ON int_outbound_call_log (tenant_id, idempotency_key);

CREATE TABLE IF NOT EXISTS int_inbound_event (
    id              BIGINT       NOT NULL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    connector_type  VARCHAR(32)  NOT NULL,
    event_type      VARCHAR(64)  NOT NULL,
    payload         TEXT         NOT NULL,
    signature_valid INT          NOT NULL DEFAULT 0,
    processed       INT          NOT NULL DEFAULT 0,
    create_time     TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_int_inbound_tenant ON int_inbound_event (tenant_id, create_time);

CREATE TABLE IF NOT EXISTS int_webhook_dedup (
    id          BIGINT       NOT NULL PRIMARY KEY,
    tenant_id   VARCHAR(64)  NOT NULL,
    dedup_key   VARCHAR(128) NOT NULL,
    expire_at   TIMESTAMP    NOT NULL,
    create_time TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_int_webhook_dedup UNIQUE (tenant_id, dedup_key)
);
