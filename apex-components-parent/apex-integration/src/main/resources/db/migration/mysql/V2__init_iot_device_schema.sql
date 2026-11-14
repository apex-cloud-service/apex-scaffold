-- V2: 物联网设备域初始结构（方案 M5 / 批次 E3）
-- 表：设备、设备影子（desired/reported + 乐观锁）、遥测、下行命令（幂等 + 状态机）、轻量规则。
-- 幂等性：CREATE TABLE IF NOT EXISTS，可重复执行。
-- 租户隔离：全部表含 tenant_id NOT NULL，参与行级隔离（不进拦截器白名单）。

CREATE TABLE IF NOT EXISTS `iot_device` (
    `id`               BIGINT        NOT NULL COMMENT '主键ID（雪花算法）',
    `tenant_id`        VARCHAR(64)   NOT NULL COMMENT '租户编码',
    `device_key`       VARCHAR(64)   NOT NULL COMMENT '设备标识（租户内唯一；topic 命名空间段，[A-Za-z0-9_-]）',
    `secret_hash`      VARCHAR(256)  NOT NULL COMMENT '设备凭据哈希（PBKDF2-SHA256；明文不落库）',
    `cert_fingerprint` VARCHAR(128)  DEFAULT NULL COMMENT 'X.509 证书指纹（SHA-256 十六进制，可空）',
    `status`           VARCHAR(16)   NOT NULL DEFAULT 'offline' COMMENT '状态（online/offline）',
    `firmware_version` VARCHAR(64)   DEFAULT NULL COMMENT '固件版本',
    `labels`           VARCHAR(1000) DEFAULT NULL COMMENT '标签（JSON 对象）',
    `last_active_at`   DATETIME      DEFAULT NULL COMMENT '最后活跃时间',
    `create_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`          INT           NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_iot_device` (`tenant_id`, `device_key`, `deleted`),
    KEY `idx_iot_device_status` (`tenant_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='物联网设备（租户级）';

CREATE TABLE IF NOT EXISTS `iot_device_shadow` (
    `id`          BIGINT      NOT NULL COMMENT '主键ID（雪花算法）',
    `tenant_id`   VARCHAR(64) NOT NULL COMMENT '租户编码',
    `device_key`  VARCHAR(64) NOT NULL COMMENT '设备标识（对应 iot_device.device_key）',
    `desired`     TEXT        DEFAULT NULL COMMENT '期望状态（JSON）',
    `reported`    TEXT        DEFAULT NULL COMMENT '上报状态（JSON，随遥测更新）',
    `version`     INT         NOT NULL DEFAULT 0 COMMENT '影子版本（乐观锁，每次更新递增）',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_iot_device_shadow` (`tenant_id`, `device_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备影子（desired/reported JSON + version 乐观锁）';

CREATE TABLE IF NOT EXISTS `iot_telemetry` (
    `id`          BIGINT      NOT NULL COMMENT '主键ID',
    `tenant_id`   VARCHAR(64) NOT NULL COMMENT '租户编码',
    `device_key`  VARCHAR(64) NOT NULL COMMENT '设备标识',
    `metric`      TEXT        NOT NULL COMMENT '遥测指标（JSON 对象）',
    `recorded_at` DATETIME    NOT NULL COMMENT '上报时间',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '落库时间',
    PRIMARY KEY (`id`),
    KEY `idx_iot_telemetry_device` (`tenant_id`, `device_key`, `recorded_at`),
    KEY `idx_iot_telemetry_recorded` (`tenant_id`, `recorded_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备遥测记录';

CREATE TABLE IF NOT EXISTS `iot_command` (
    `id`          BIGINT      NOT NULL COMMENT '主键ID',
    `tenant_id`   VARCHAR(64) NOT NULL COMMENT '租户编码',
    `command_id`  VARCHAR(64) NOT NULL COMMENT '业务命令ID（调用方提供，租户内唯一，幂等键）',
    `device_key`  VARCHAR(64) NOT NULL COMMENT '设备标识',
    `payload`     TEXT        NOT NULL COMMENT '命令载荷（JSON）',
    `status`      VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '状态（PENDING/SENT/ACK/TIMEOUT）',
    `sent_at`     DATETIME    DEFAULT NULL COMMENT '下发时间',
    `acked_at`    DATETIME    DEFAULT NULL COMMENT '确认时间',
    `timeout_at`  DATETIME    DEFAULT NULL COMMENT '超时截止时间',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_iot_command` (`tenant_id`, `command_id`),
    KEY `idx_iot_command_device` (`tenant_id`, `device_key`, `create_time`),
    KEY `idx_iot_command_timeout` (`status`, `timeout_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备下行命令（command_id 幂等 + PENDING/SENT/ACK/TIMEOUT 状态机）';

CREATE TABLE IF NOT EXISTS `iot_rule` (
    `id`             BIGINT        NOT NULL COMMENT '主键ID（雪花算法）',
    `tenant_id`      VARCHAR(64)   NOT NULL COMMENT '租户编码',
    `rule_name`      VARCHAR(64)   NOT NULL COMMENT '规则名（租户内唯一）',
    `condition_json` TEXT          NOT NULL COMMENT '结构化条件（JSON：字段/操作符/阈值；仅声明式比较，禁止脚本执行）',
    `action_type`    VARCHAR(16)   NOT NULL COMMENT '动作类型（NOTIFY/WEBHOOK/EVENT）',
    `action_config`  TEXT          DEFAULT NULL COMMENT '动作配置（JSON；WEBHOOK 需 connectorKey）',
    `enabled`        INT           NOT NULL DEFAULT 1 COMMENT '启停（0-停用，1-启用）',
    `create_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        INT           NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_iot_rule` (`tenant_id`, `rule_name`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='物联网轻量规则（结构化条件，无脚本注入面）';
