-- V2: 物联网设备域初始结构（方案 M5 / 批次 E3）
-- 语义与 MySQL 版本一致；全部表含 tenant_id NOT NULL，参与行级隔离。

CREATE TABLE IF NOT EXISTS iot_device (
    id               BIGINT        NOT NULL PRIMARY KEY,
    tenant_id        VARCHAR(64)   NOT NULL,
    device_key       VARCHAR(64)   NOT NULL,
    secret_hash      VARCHAR(256)  NOT NULL,
    cert_fingerprint VARCHAR(128)  DEFAULT NULL,
    status           VARCHAR(16)   NOT NULL DEFAULT 'offline',
    firmware_version VARCHAR(64)   DEFAULT NULL,
    labels           VARCHAR(1000) DEFAULT NULL,
    last_active_at   TIMESTAMP     DEFAULT NULL,
    create_time      TIMESTAMP     NOT NULL DEFAULT NOW(),
    update_time      TIMESTAMP     NOT NULL DEFAULT NOW(),
    deleted          INT           NOT NULL DEFAULT 0,
    CONSTRAINT uk_iot_device UNIQUE (tenant_id, device_key, deleted)
);
CREATE INDEX IF NOT EXISTS idx_iot_device_status ON iot_device (tenant_id, status);

CREATE TABLE IF NOT EXISTS iot_device_shadow (
    id          BIGINT      NOT NULL PRIMARY KEY,
    tenant_id   VARCHAR(64) NOT NULL,
    device_key  VARCHAR(64) NOT NULL,
    desired     TEXT        DEFAULT NULL,
    reported    TEXT        DEFAULT NULL,
    version     INT         NOT NULL DEFAULT 0,
    create_time TIMESTAMP   NOT NULL DEFAULT NOW(),
    update_time TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_iot_device_shadow UNIQUE (tenant_id, device_key)
);

CREATE TABLE IF NOT EXISTS iot_telemetry (
    id          BIGINT      NOT NULL PRIMARY KEY,
    tenant_id   VARCHAR(64) NOT NULL,
    device_key  VARCHAR(64) NOT NULL,
    metric      TEXT        NOT NULL,
    recorded_at TIMESTAMP   NOT NULL,
    create_time TIMESTAMP   NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_iot_telemetry_device ON iot_telemetry (tenant_id, device_key, recorded_at);
CREATE INDEX IF NOT EXISTS idx_iot_telemetry_recorded ON iot_telemetry (tenant_id, recorded_at);

CREATE TABLE IF NOT EXISTS iot_command (
    id          BIGINT      NOT NULL PRIMARY KEY,
    tenant_id   VARCHAR(64) NOT NULL,
    command_id  VARCHAR(64) NOT NULL,
    device_key  VARCHAR(64) NOT NULL,
    payload     TEXT        NOT NULL,
    status      VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    sent_at     TIMESTAMP   DEFAULT NULL,
    acked_at    TIMESTAMP   DEFAULT NULL,
    timeout_at  TIMESTAMP   DEFAULT NULL,
    create_time TIMESTAMP   NOT NULL DEFAULT NOW(),
    update_time TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_iot_command UNIQUE (tenant_id, command_id)
);
CREATE INDEX IF NOT EXISTS idx_iot_command_device ON iot_command (tenant_id, device_key, create_time);
CREATE INDEX IF NOT EXISTS idx_iot_command_timeout ON iot_command (status, timeout_at);

CREATE TABLE IF NOT EXISTS iot_rule (
    id             BIGINT       NOT NULL PRIMARY KEY,
    tenant_id      VARCHAR(64)  NOT NULL,
    rule_name      VARCHAR(64)  NOT NULL,
    condition_json TEXT         NOT NULL,
    action_type    VARCHAR(16)  NOT NULL,
    action_config  TEXT         DEFAULT NULL,
    enabled        INT          NOT NULL DEFAULT 1,
    create_time    TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time    TIMESTAMP    NOT NULL DEFAULT NOW(),
    deleted        INT          NOT NULL DEFAULT 0,
    CONSTRAINT uk_iot_rule UNIQUE (tenant_id, rule_name, deleted)
);
