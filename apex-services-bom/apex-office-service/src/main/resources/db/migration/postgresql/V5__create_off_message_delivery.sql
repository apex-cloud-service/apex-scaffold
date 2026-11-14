-- ============================================================
-- Apex Scaffold - 办公服务独立库 V5：消息渠道投递记录（PostgreSQL）
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
--   CREATE TABLE IF NOT EXISTS、CREATE [UNIQUE] INDEX IF NOT EXISTS，
--   可重复执行。
--
-- 【方言约定】
--   PostgreSQL 使用 TIMESTAMP 与 SMALLINT 逻辑删除字段；自增主键 BIGSERIAL。
--
-- @author Apex Team
-- @version 1.0.0
-- @since 1.0.0
-- ============================================================

-- ------------------------------------------------------------
-- 办公消息渠道投递记录
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS off_message_delivery (
    id                  BIGSERIAL    NOT NULL,
    message_id          BIGINT       NOT NULL,
    recipient_id        BIGINT       NOT NULL,
    channel             VARCHAR(16)  NOT NULL,
    delivery_status     VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    platform_message_id VARCHAR(128),
    failure_reason      VARCHAR(512),
    delivered_at        TIMESTAMP,
    tenant_id           VARCHAR(64)  NOT NULL,
    create_time         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT     NOT NULL DEFAULT 0,
    version             INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_off_message_delivery ON off_message_delivery (message_id, recipient_id, channel, deleted);
CREATE INDEX IF NOT EXISTS idx_off_delivery_recipient_status ON off_message_delivery (recipient_id, delivery_status, deleted);
CREATE INDEX IF NOT EXISTS idx_off_delivery_tenant ON off_message_delivery (tenant_id, deleted);
COMMENT ON TABLE off_message_delivery IS '办公消息渠道投递记录';
