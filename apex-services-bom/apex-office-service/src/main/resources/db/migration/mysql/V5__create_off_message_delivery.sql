-- ============================================================
-- Apex Scaffold - 办公服务独立库 V5：消息渠道投递记录（MySQL）
-- ============================================================
-- 【用途】
--   O04 三通道统一收件箱（T17）：为站内持久消息补充渠道投递维度。
--   已读归属 off_message_recipient，送达/失败归属本表，互不覆盖；
--   通过 message_id + recipient_id 关联站内消息与接收记录。
--
-- 【表清单】
--   off_message_delivery    办公消息渠道投递记录
--
-- 【约束】
--   channel 取值 INBOX/FEISHU/DINGTALK/WECOM（应用层白名单校验）；
--   delivery_status 取值 PENDING/SUCCESS/FAILED/UNKNOWN。
--
-- 【幂等性】
--   CREATE TABLE IF NOT EXISTS，可重复执行。
--
-- 【方言约定】
--   MySQL 统一使用 DATETIME，禁止 TIMESTAMP；字符集 utf8mb4。
--
-- @author Apex Team
-- @version 1.0.0
-- @since 1.0.0
-- ============================================================

-- ------------------------------------------------------------
-- 办公消息渠道投递记录
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `off_message_delivery` (
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `message_id`          BIGINT       NOT NULL COMMENT '站内消息ID（off_message.id）',
    `recipient_id`        BIGINT       NOT NULL COMMENT '接收人ID',
    `channel`             VARCHAR(16)  NOT NULL COMMENT '投递渠道（INBOX/FEISHU/DINGTALK/WECOM）',
    `delivery_status`     VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '投递状态（PENDING/SUCCESS/FAILED/UNKNOWN）',
    `platform_message_id` VARCHAR(128)          DEFAULT NULL COMMENT '第三方平台消息ID',
    `failure_reason`      VARCHAR(512)          DEFAULT NULL COMMENT '失败原因',
    `delivered_at`        DATETIME              DEFAULT NULL COMMENT '送达时间',
    `tenant_id`           VARCHAR(64)  NOT NULL COMMENT '租户ID',
    `create_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`             TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0-未删除 1-已删除）',
    `version`             INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_off_message_delivery` (`message_id`, `recipient_id`, `channel`, `deleted`),
    KEY `idx_off_delivery_recipient_status` (`recipient_id`, `delivery_status`, `deleted`),
    KEY `idx_off_delivery_tenant` (`tenant_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='办公消息渠道投递记录';
