-- 工作流域领域事件可靠投递表。
-- 与共享 Outbox 表名/结构保持一致，便于复用同一投递语义与运维口径。
CREATE TABLE IF NOT EXISTS `sys_domain_event_outbox` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `event_id` VARCHAR(64) NOT NULL COMMENT '领域事件唯一标识',
  `event_class` VARCHAR(255) NOT NULL COMMENT '领域事件 Java 类型',
  `payload` LONGTEXT NOT NULL COMMENT '事件 JSON 负载（含完整信封）',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='领域事件可靠投递表（工作流）';
