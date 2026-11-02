-- ============================================================
-- Apex Scaffold - 集成服务 basic Profile 初始化脚本（H2）
-- ============================================================
-- 【用途】
--   基础版（basic）单机环境使用 H2 内存库（MODE=MySQL）时，
--   通过 spring.sql.init 加载本脚本初始化表结构，替代 Flyway。
--   表结构与 apex-integration 库 db/migration 下 V1/V2 MySQL 脚本对齐
--   （H2 方言：TEXT→CLOB、DATETIME→TIMESTAMP、独立索引语句、无 ON UPDATE）。
--
-- 【表清单】（方案 M5 / 批次 E1、E3）
--   int_connector_config  集成连接器配置（租户级）
--   int_credential        租户级外部系统凭据（密文存储）
--   int_outbound_call_log 出站调用审计
--   int_inbound_event     入站事件记录
--   int_webhook_dedup     入站 webhook 幂等去重
--   iot_device            物联网设备（租户级）
--   iot_device_shadow     设备影子（desired/reported + version 乐观锁）
--   iot_telemetry         设备遥测记录
--   iot_command           设备下行命令（command_id 幂等 + 状态机）
--   iot_rule              物联网轻量规则
--
-- @author Apex Team
-- @version 1.3.1
-- @since 1.3.1
-- ============================================================

-- ------------------------------------------------------------
-- 集成连接器配置（租户级）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS int_connector_config (
    id             BIGINT       NOT NULL,
    tenant_id      VARCHAR(64)  NOT NULL,
    connector_type VARCHAR(32)  NOT NULL,
    config_key     VARCHAR(64)  NOT NULL,
    config_value   CLOB         NOT NULL,
    status         INT          NOT NULL DEFAULT 1,
    remark         VARCHAR(500),
    create_time    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version        INT          NOT NULL DEFAULT 0,
    deleted        TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_int_connector_config ON int_connector_config (tenant_id, config_key, deleted);
CREATE INDEX IF NOT EXISTS idx_int_connector_type ON int_connector_config (tenant_id, connector_type);

-- ------------------------------------------------------------
-- 租户级外部系统凭据（密文存储）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS int_credential (
    id              BIGINT       NOT NULL,
    tenant_id       VARCHAR(64)  NOT NULL,
    credential_key  VARCHAR(64)  NOT NULL,
    credential_type VARCHAR(32)  NOT NULL,
    secret_cipher   CLOB         NOT NULL,
    secret_iv       VARCHAR(64)  NOT NULL,
    version_no      INT          NOT NULL DEFAULT 1,
    rotated_at      TIMESTAMP,
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_int_credential ON int_credential (tenant_id, credential_key, deleted);

-- ------------------------------------------------------------
-- 出站调用审计
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS int_outbound_call_log (
    id              BIGINT        NOT NULL,
    tenant_id       VARCHAR(64)   NOT NULL,
    connector_type  VARCHAR(32)   NOT NULL,
    operation       VARCHAR(64)   NOT NULL,
    idempotency_key VARCHAR(128),
    status          VARCHAR(16)   NOT NULL,
    duration_ms     BIGINT,
    error_message   VARCHAR(1000),
    create_time     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_int_outbound_tenant ON int_outbound_call_log (tenant_id, create_time);
CREATE INDEX IF NOT EXISTS idx_int_outbound_idem ON int_outbound_call_log (tenant_id, idempotency_key);

-- ------------------------------------------------------------
-- 入站事件记录
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS int_inbound_event (
    id              BIGINT      NOT NULL,
    tenant_id       VARCHAR(64) NOT NULL,
    connector_type  VARCHAR(32) NOT NULL,
    event_type      VARCHAR(64) NOT NULL,
    payload         CLOB        NOT NULL,
    signature_valid INT         NOT NULL DEFAULT 0,
    processed       INT         NOT NULL DEFAULT 0,
    create_time     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_int_inbound_tenant ON int_inbound_event (tenant_id, create_time);

-- ------------------------------------------------------------
-- 入站 webhook 幂等去重
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS int_webhook_dedup (
    id          BIGINT       NOT NULL,
    tenant_id   VARCHAR(64)  NOT NULL,
    dedup_key   VARCHAR(128) NOT NULL,
    expire_at   TIMESTAMP    NOT NULL,
    create_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_int_webhook_dedup ON int_webhook_dedup (tenant_id, dedup_key);

-- ------------------------------------------------------------
-- 物联网设备（租户级）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS iot_device (
    id               BIGINT        NOT NULL,
    tenant_id        VARCHAR(64)   NOT NULL,
    device_key       VARCHAR(64)   NOT NULL,
    secret_hash      VARCHAR(256)  NOT NULL,
    cert_fingerprint VARCHAR(128),
    status           VARCHAR(16)   NOT NULL DEFAULT 'offline',
    firmware_version VARCHAR(64),
    labels           VARCHAR(1000),
    last_active_at   TIMESTAMP,
    create_time      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted          TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_iot_device ON iot_device (tenant_id, device_key, deleted);
CREATE INDEX IF NOT EXISTS idx_iot_device_status ON iot_device (tenant_id, status);

-- ------------------------------------------------------------
-- 设备影子（desired/reported JSON + version 乐观锁）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS iot_device_shadow (
    id          BIGINT      NOT NULL,
    tenant_id   VARCHAR(64) NOT NULL,
    device_key  VARCHAR(64) NOT NULL,
    desired     CLOB,
    reported    CLOB,
    version     INT         NOT NULL DEFAULT 0,
    create_time TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_iot_device_shadow ON iot_device_shadow (tenant_id, device_key);

-- ------------------------------------------------------------
-- 设备遥测记录
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS iot_telemetry (
    id          BIGINT      NOT NULL,
    tenant_id   VARCHAR(64) NOT NULL,
    device_key  VARCHAR(64) NOT NULL,
    metric      CLOB        NOT NULL,
    recorded_at TIMESTAMP   NOT NULL,
    create_time TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_iot_telemetry_device ON iot_telemetry (tenant_id, device_key, recorded_at);
CREATE INDEX IF NOT EXISTS idx_iot_telemetry_recorded ON iot_telemetry (tenant_id, recorded_at);

-- ------------------------------------------------------------
-- 设备下行命令（command_id 幂等 + PENDING/SENT/ACK/TIMEOUT 状态机）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS iot_command (
    id          BIGINT      NOT NULL,
    tenant_id   VARCHAR(64) NOT NULL,
    command_id  VARCHAR(64) NOT NULL,
    device_key  VARCHAR(64) NOT NULL,
    payload     CLOB        NOT NULL,
    status      VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    sent_at     TIMESTAMP,
    acked_at    TIMESTAMP,
    timeout_at  TIMESTAMP,
    create_time TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_iot_command ON iot_command (tenant_id, command_id);
CREATE INDEX IF NOT EXISTS idx_iot_command_device ON iot_command (tenant_id, device_key, create_time);
CREATE INDEX IF NOT EXISTS idx_iot_command_timeout ON iot_command (status, timeout_at);

-- ------------------------------------------------------------
-- 物联网轻量规则（结构化条件，无脚本注入面）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS iot_rule (
    id            BIGINT       NOT NULL,
    tenant_id     VARCHAR(64)  NOT NULL,
    rule_name     VARCHAR(64)  NOT NULL,
    condition_json CLOB        NOT NULL,
    action_type   VARCHAR(16)  NOT NULL,
    action_config CLOB,
    enabled       INT          NOT NULL DEFAULT 1,
    create_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_iot_rule ON iot_rule (tenant_id, rule_name, deleted);
