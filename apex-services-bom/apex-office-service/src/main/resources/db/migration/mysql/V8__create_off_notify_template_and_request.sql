-- ============================================================
-- Apex Scaffold - 办公服务独立库 V8：统一通知模板与投递请求（MySQL）
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
--   CREATE TABLE IF NOT EXISTS，可重复执行。
--
-- 【方言约定】
--   MySQL 统一使用 DATETIME，禁止 TIMESTAMP；字符集 utf8mb4。
--
-- @author Apex Team
-- @version 1.0.0
-- @since 1.4.0
-- ============================================================

-- ------------------------------------------------------------
-- 通知模板
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `off_notify_template` (
    `id`            BIGINT       NOT NULL COMMENT '主键ID（雪花算法）',
    `tenant_id`     VARCHAR(64)  NOT NULL COMMENT '租户ID',
    `template_code` VARCHAR(64)  NOT NULL COMMENT '模板编码（租户内唯一）',
    `channel`       VARCHAR(16)  NOT NULL COMMENT '默认投递通道（INBOX/FEISHU/DINGTALK/WECOM/SMS/EMAIL）',
    `title`         VARCHAR(255)          DEFAULT NULL COMMENT '通知主题（可空）',
    `body`          TEXT                  COMMENT '正文模板（含 $${key} 占位符，缺失参数保留占位符）',
    `params_schema` VARCHAR(1024)         DEFAULT NULL COMMENT '参数契约（JSON 字符串，供管理端提示）',
    `status`        VARCHAR(16)  NOT NULL DEFAULT 'ENABLED' COMMENT '状态（ENABLED/DISABLED）',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0-未删除 1-已删除）',
    `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_off_notify_template` (`tenant_id`, `template_code`, `deleted`),
    KEY `idx_off_notify_template_tenant_status` (`tenant_id`, `status`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='统一通知模板';

-- ------------------------------------------------------------
-- 投递请求留痕
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `off_notify_request` (
    `id`              BIGINT      NOT NULL COMMENT '主键ID（雪花算法）',
    `tenant_id`       VARCHAR(64) NOT NULL COMMENT '租户ID',
    `channel`         VARCHAR(16) NOT NULL COMMENT '实际投递通道（INBOX/FEISHU/DINGTALK/WECOM/SMS/EMAIL）',
    `template_code`   VARCHAR(64) NOT NULL COMMENT '使用的模板编码',
    `recipient`       TEXT                 COMMENT '接收目标（JSON 数组字符串，如 ["user-1"]）',
    `params`          TEXT                 COMMENT '渲染参数（JSON 对象字符串）',
    `status`          VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '投递状态（PENDING/SUCCESS/FAILED）',
    `error_message`   VARCHAR(512)         DEFAULT NULL COMMENT '失败原因（成功时为空）',
    `outbox_event_id` BIGINT               DEFAULT NULL COMMENT '关联的 Outbox 事件ID（可空，事件驱动投递时回填）',
    `create_time`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_off_notify_request_tenant_status` (`tenant_id`, `status`, `create_time`),
    KEY `idx_off_notify_request_template` (`tenant_id`, `template_code`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='统一通知投递请求留痕';
