-- ============================================================
-- Apex Scaffold - 办公服务独立库 V8：统一通知模板与投递请求（PostgreSQL）
-- ============================================================
-- 【用途】
--   统一消息门面（方案 M4 / 批次 D1）收尾：为 NotifyApplicationService 补充
--   模板持久化与投递留痕能力。此前门面仅持有内存通道适配器，
--   模板依赖调用方内联传入，投递结果无处可查。
--
-- 【表清单】
--   off_notify_template   通知模板（$${key} 占位符正文 + 参数契约 + 启停状态）
--   off_notify_request    投递请求留痕（按次记录通道、目标、参数、投递结果与错误）
--
-- 【约束】
--   channel 取值 INBOX/FEISHU/DINGTALK/WECOM/SMS/EMAIL（应用层白名单校验）；
--   off_notify_template.status 取值 ENABLED/DISABLED；
--   off_notify_request.status 取值 PENDING/SUCCESS/FAILED（应用层白名单校验）。
--   模板删除采用逻辑删除，历史投递记录保留；投递记录为追加式留痕，
--   不提供逻辑删除与乐观锁列。
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
-- @since 1.4.0
-- ============================================================

-- ------------------------------------------------------------
-- 通知模板
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS off_notify_template (
    id            BIGSERIAL    NOT NULL,
    tenant_id     VARCHAR(64)  NOT NULL,
    template_code VARCHAR(64)  NOT NULL,
    channel       VARCHAR(16)  NOT NULL,
    title         VARCHAR(255),
    body          TEXT,
    params_schema VARCHAR(1024),
    status        VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    create_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       SMALLINT     NOT NULL DEFAULT 0,
    version       INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_off_notify_template ON off_notify_template (tenant_id, template_code, deleted);
CREATE INDEX IF NOT EXISTS idx_off_notify_template_tenant_status ON off_notify_template (tenant_id, status, deleted);
COMMENT ON TABLE off_notify_template IS '统一通知模板';

-- ------------------------------------------------------------
-- 投递请求留痕
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS off_notify_request (
    id              BIGSERIAL    NOT NULL,
    tenant_id       VARCHAR(64)  NOT NULL,
    channel         VARCHAR(16)  NOT NULL,
    template_code   VARCHAR(64)  NOT NULL,
    recipient       TEXT,
    params          TEXT,
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    error_message   VARCHAR(512),
    outbox_event_id BIGINT,
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_off_notify_request_tenant_status ON off_notify_request (tenant_id, status, create_time);
CREATE INDEX IF NOT EXISTS idx_off_notify_request_template ON off_notify_request (tenant_id, template_code, create_time);
COMMENT ON TABLE off_notify_request IS '统一通知投递请求留痕';
