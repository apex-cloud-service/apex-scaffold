-- 审计日志表 (Flyway V2)
-- 版本: 1.1.0
-- 描述: 创建操作审计日志表,用于记录用户关键操作

CREATE TABLE IF NOT EXISTS `audit_log` (
  `id` BIGINT(20) NOT NULL COMMENT '主键ID',
  `operator_id` VARCHAR(64) DEFAULT NULL COMMENT '操作人ID',
  `operator_name` VARCHAR(100) DEFAULT NULL COMMENT '操作人姓名',
  `operator_ip` VARCHAR(50) DEFAULT NULL COMMENT '操作人IP地址',
  `module` VARCHAR(100) DEFAULT NULL COMMENT '操作模块',
  `operation_type` VARCHAR(20) DEFAULT NULL COMMENT '操作类型(CREATE/UPDATE/DELETE/QUERY等)',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '操作描述',
  `method` VARCHAR(200) DEFAULT NULL COMMENT '请求方法(全限定名)',
  `request_url` VARCHAR(500) DEFAULT NULL COMMENT '请求URL',
  `request_params` TEXT COMMENT '请求参数(JSON格式)',
  `response_result` TEXT COMMENT '响应结果(JSON格式)',
  `duration` BIGINT(20) DEFAULT NULL COMMENT '执行时长(毫秒)',
  `status` VARCHAR(20) DEFAULT NULL COMMENT '操作状态(SUCCESS/FAIL)',
  `error_msg` VARCHAR(1000) DEFAULT NULL COMMENT '错误信息',
  `business_id` VARCHAR(100) DEFAULT NULL COMMENT '业务ID(关联具体业务数据)',
  `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT '租户ID(多租户场景)',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除(0-未删除, 1-已删除)',
  PRIMARY KEY (`id`),
  KEY `idx_operator_id` (`operator_id`),
  KEY `idx_module` (`module`),
  KEY `idx_operation_type` (`operation_type`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_business_id` (`business_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审计日志表';
