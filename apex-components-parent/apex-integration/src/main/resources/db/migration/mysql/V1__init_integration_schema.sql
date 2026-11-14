-- V1: 集成域初始结构（方案 M5 / 批次 E1）
-- 表：连接器配置、租户级外部凭据、出站调用审计、入站事件、webhook 幂等去重。
-- 幂等性：CREATE TABLE IF NOT EXISTS，可重复执行。
-- 租户隔离：全部表含 tenant_id NOT NULL，参与行级隔离（不进拦截器白名单）。

CREATE TABLE IF NOT EXISTS `int_connector_config` (
    `id`             BIGINT       NOT NULL COMMENT '主键ID（雪花算法）',
    `tenant_id`      VARCHAR(64)  NOT NULL COMMENT '租户编码',
    `connector_type` VARCHAR(32)  NOT NULL COMMENT '连接器类型（HTTP/WEBHOOK/WECHAT/CHUANGLAN/...）',
    `config_key`     VARCHAR(64)  NOT NULL COMMENT '配置标识（租户内唯一）',
    `config_value`   TEXT         NOT NULL COMMENT '连接配置（JSON；敏感项以凭据引用表示）',
    `status`         INT          NOT NULL DEFAULT 1 COMMENT '状态（0-停用，1-正常）',
    `remark`         VARCHAR(500) DEFAULT NULL COMMENT '备注',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `version`        INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `deleted`        INT          NOT NULL DEFAULT 0 COMMENT '逻辑删除标记（0-未删除，1-已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_int_connector_config` (`tenant_id`, `config_key`, `deleted`),
    KEY `idx_int_connector_type` (`tenant_id`, `connector_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='集成连接器配置（租户级）';

CREATE TABLE IF NOT EXISTS `int_credential` (
    `id`             BIGINT       NOT NULL COMMENT '主键ID（雪花算法）',
    `tenant_id`      VARCHAR(64)  NOT NULL COMMENT '租户编码',
    `credential_key` VARCHAR(64)  NOT NULL COMMENT '凭据标识（租户内唯一）',
    `credential_type` VARCHAR(32) NOT NULL COMMENT '凭据类型（API_KEY/BASIC/TOKEN/X509）',
    `secret_cipher`  TEXT         NOT NULL COMMENT '密文（AES-256-GCM；明文不落库）',
    `secret_iv`      VARCHAR(64)  NOT NULL COMMENT '加密 IV',
    `version_no`     INT          NOT NULL DEFAULT 1 COMMENT '凭据版本（轮换递增）',
    `rotated_at`     DATETIME     DEFAULT NULL COMMENT '最近轮换时间',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        INT          NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_int_credential` (`tenant_id`, `credential_key`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户级外部系统凭据（密文存储）';

CREATE TABLE IF NOT EXISTS `int_outbound_call_log` (
    `id`             BIGINT       NOT NULL COMMENT '主键ID',
    `tenant_id`      VARCHAR(64)  NOT NULL COMMENT '租户编码',
    `connector_type` VARCHAR(32)  NOT NULL COMMENT '连接器类型',
    `operation`      VARCHAR(64)  NOT NULL COMMENT '操作标识',
    `idempotency_key` VARCHAR(128) DEFAULT NULL COMMENT '幂等键',
    `status`         VARCHAR(16)  NOT NULL COMMENT '状态（SUCCESS/FAILED）',
    `duration_ms`    BIGINT       DEFAULT NULL COMMENT '耗时（毫秒）',
    `error_message`  VARCHAR(1000) DEFAULT NULL COMMENT '失败原因（脱敏）',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_int_outbound_tenant` (`tenant_id`, `create_time`),
    KEY `idx_int_outbound_idem` (`tenant_id`, `idempotency_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='出站调用审计';

CREATE TABLE IF NOT EXISTS `int_inbound_event` (
    `id`             BIGINT       NOT NULL COMMENT '主键ID',
    `tenant_id`      VARCHAR(64)  NOT NULL COMMENT '租户编码',
    `connector_type` VARCHAR(32)  NOT NULL COMMENT '连接器类型',
    `event_type`     VARCHAR(64)  NOT NULL COMMENT '事件类型',
    `payload`        TEXT         NOT NULL COMMENT '事件载荷（JSON）',
    `signature_valid` INT         NOT NULL DEFAULT 0 COMMENT '签名校验（0-失败，1-通过）',
    `processed`      INT          NOT NULL DEFAULT 0 COMMENT '处理状态（0-待处理，1-已处理）',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_int_inbound_tenant` (`tenant_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='入站事件记录';

CREATE TABLE IF NOT EXISTS `int_webhook_dedup` (
    `id`             BIGINT       NOT NULL COMMENT '主键ID',
    `tenant_id`      VARCHAR(64)  NOT NULL COMMENT '租户编码',
    `dedup_key`      VARCHAR(128) NOT NULL COMMENT '去重键（签名+时间戳+事件ID 摘要）',
    `expire_at`      DATETIME     NOT NULL COMMENT '过期时间',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_int_webhook_dedup` (`tenant_id`, `dedup_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='入站 webhook 幂等去重';
