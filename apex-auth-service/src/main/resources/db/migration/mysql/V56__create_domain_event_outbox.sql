-- ============================================================
-- V56: 创建领域事件 Outbox 表（MySQL）
-- ============================================================
-- 变更内容：新建 sys_domain_event_outbox，支撑迁移方案 Phase 2.6 登出传播——
--           认证中心登出撤销 Token 后发布 TokenRevokedEvent，先写 Outbox 再由
--           投递器可靠投递，通知各业务服务清理本地权限缓存与在线态副本。
-- 表结构与 apex-interfaces V1__init_schema.sql 保持完全一致，保证跨服务复用同一规范。
-- 幂等性：CREATE TABLE IF NOT EXISTS + 唯一键，可重复执行。
-- ============================================================

CREATE TABLE IF NOT EXISTS sys_domain_event_outbox (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `event_id` VARCHAR(64) NOT NULL COMMENT '领域事件唯一标识',
  `event_class` VARCHAR(255) NOT NULL COMMENT '领域事件 Java 类型',
  `payload` LONGTEXT NOT NULL COMMENT '事件 JSON 负载',
  `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/DELIVERED/DEAD',
  `attempts` INT NOT NULL DEFAULT 0 COMMENT '已失败次数',
  `next_attempt_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '下次投递时间',
  `locked_by` VARCHAR(64) DEFAULT NULL COMMENT '投递实例令牌',
  `locked_at` DATETIME(6) DEFAULT NULL COMMENT '领取时间',
  `last_error` VARCHAR(1000) DEFAULT NULL COMMENT '最近一次错误摘要',
  `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
  `delivered_at` DATETIME(6) DEFAULT NULL COMMENT '完成时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_outbox_event_id` (`event_id`),
  KEY `idx_outbox_dispatch` (`status`, `next_attempt_at`, `id`),
  KEY `idx_outbox_locked` (`status`, `locked_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='领域事件可靠投递表（登出传播事件总线）';