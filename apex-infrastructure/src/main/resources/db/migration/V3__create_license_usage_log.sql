-- License使用记录表 (Flyway V3)
-- 版本: 1.1.0
-- 描述: 记录License验证和使用情况,用于审计和统计

CREATE TABLE IF NOT EXISTS `license_usage_log` (
  `id` BIGINT(20) NOT NULL COMMENT '主键ID',
  `license_id` VARCHAR(100) DEFAULT NULL COMMENT 'License ID',
  `customer_name` VARCHAR(200) DEFAULT NULL COMMENT '客户名称',
  `version` VARCHAR(20) DEFAULT NULL COMMENT '授权版本',
  `event_type` VARCHAR(50) DEFAULT NULL COMMENT '事件类型(LOAD/VALIDATE/CHECK_FEATURE)',
  `event_detail` VARCHAR(500) DEFAULT NULL COMMENT '事件详情',
  `success` TINYINT(1) DEFAULT 1 COMMENT '是否成功(0-失败, 1-成功)',
  `error_msg` VARCHAR(1000) DEFAULT NULL COMMENT '错误信息',
  `ip_address` VARCHAR(50) DEFAULT NULL COMMENT 'IP地址',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_license_id` (`license_id`),
  KEY `idx_customer_name` (`customer_name`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='License使用记录表';
