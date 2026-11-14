-- ================================================================
-- Apex Scaffold example 测试迁移 V2：操作日志表
-- ================================================================
-- 用途：为 MySQL 集成测试创建 sys_operation_log 表，支持业务操作日志写入。
-- 背景：example 集成测试会调用基础设施层 OperationLogRepository，
--       需要 sys_operation_log 表存在，否则抛出 SQLSyntaxErrorException。
-- 兼容性：面向 MySQL 8.x 方言；DDL 与主应用 V1__init_schema.sql 保持一致。
-- 影响范围：仅作用于 apex_example_test 测试库，不影响生产环境。
-- ================================================================

CREATE TABLE IF NOT EXISTS `sys_operation_log` (
  `id`              BIGINT       NOT NULL COMMENT '主键ID(雪花算法)',
  `module`          VARCHAR(100) NOT NULL COMMENT '操作模块',
  `operation_type`  VARCHAR(50)  DEFAULT NULL COMMENT '操作类型',
  `description`     VARCHAR(255) DEFAULT NULL COMMENT '操作描述',
  `request_method`  VARCHAR(10)  DEFAULT NULL COMMENT '请求方法',
  `request_uri`     VARCHAR(500) DEFAULT NULL COMMENT '请求URI',
  `request_params`  TEXT         DEFAULT NULL COMMENT '请求参数',
  `response_result` TEXT         DEFAULT NULL COMMENT '响应结果',
  `status`          INT(1)       DEFAULT NULL COMMENT '操作状态(0-成功,1-失败)',
  `error_msg`       TEXT         DEFAULT NULL COMMENT '错误信息',
  `execution_time`  BIGINT       DEFAULT NULL COMMENT '执行时间(毫秒)',
  `ip`              VARCHAR(50)  DEFAULT NULL COMMENT '操作IP',
  `location`        VARCHAR(100) DEFAULT NULL COMMENT '操作地点',
  `user_id`         BIGINT       DEFAULT NULL COMMENT '操作用户ID',
  `user_name`       VARCHAR(50)  DEFAULT NULL COMMENT '操作用户名',
  `operation_time`  DATETIME     DEFAULT NULL COMMENT '操作时间',
  `device_type`     VARCHAR(50)  DEFAULT NULL COMMENT '设备类型',
  `browser`         VARCHAR(100) DEFAULT NULL COMMENT '浏览器类型',
  `os`              VARCHAR(100) DEFAULT NULL COMMENT '操作系统',
  `trace_id`        VARCHAR(100) DEFAULT NULL COMMENT '追踪ID',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version`         INT(10)      NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  `deleted`         INT(1)       NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_module` (`module`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_operation_type` (`operation_type`),
  KEY `idx_status` (`status`),
  KEY `idx_trace_id` (`trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';
