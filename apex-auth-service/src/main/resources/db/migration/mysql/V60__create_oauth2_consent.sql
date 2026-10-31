-- V60: OAuth2 用户授权同意记录（统一认证收口 A5）
-- 幂等性：CREATE TABLE IF NOT EXISTS + 唯一键，可重复执行。

CREATE TABLE IF NOT EXISTS `auth_oauth2_consent` (
    `id`           BIGINT       NOT NULL COMMENT '主键ID',
    `tenant_id`    VARCHAR(64)  NOT NULL COMMENT '租户ID',
    `principal`    VARCHAR(100) NOT NULL COMMENT '已认证主体（授权用户标识）',
    `client_id`    VARCHAR(100) NOT NULL COMMENT 'OAuth2 客户端ID',
    `scope`        VARCHAR(500) DEFAULT NULL COMMENT '同意的授权范围',
    `consented_at` DATETIME     NOT NULL COMMENT '同意时间',
    `version`      INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `deleted`      INT          NOT NULL DEFAULT 0 COMMENT '逻辑删除标记（0-未删除，1-已删除）',
    `create_time`  DATETIME     NOT NULL COMMENT '创建时间',
    `update_time`  DATETIME     NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_auth_oauth2_consent` (`tenant_id`, `principal`, `client_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAuth2 用户授权同意记录（平台级契约表）';

