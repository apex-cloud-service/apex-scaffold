-- ============================================================
-- V60: 网关访问日志持久化表（MySQL）
-- ============================================================
-- 变更内容：
--   新建 sys_gateway_access_log 网关结构化访问日志表，承载
--   RequestLoggingFilter 在链路末端组装、AccessLogService 异步落库的访问日志。
--   与「独立库 apex_gateway」约定一致，避免网关高频写入污染业务主库。
-- 安全：request_headers 仅存脱敏后的请求头 JSON 快照（敏感头一律 *** 占位），
--       真实凭据禁止入库（见 AccessLogService#sanitizeHeaders）。
-- 幂等性：CREATE TABLE IF NOT EXISTS，可重复执行。
-- 关联方案：.trae/documents/gateway-resilience-enhancement-plan.md §5.3
-- ============================================================

CREATE TABLE IF NOT EXISTS `sys_gateway_access_log` (
  `id`              BIGINT       NOT NULL COMMENT '主键ID(雪花算法)',
  `trace_id`        VARCHAR(64)  DEFAULT NULL COMMENT '链路追踪ID(贯穿网关与下游服务)',
  `method`          VARCHAR(10)  DEFAULT NULL COMMENT 'HTTP方法(GET/POST/...)',
  `path`            VARCHAR(500) DEFAULT NULL COMMENT '请求路径(不含查询串)',
  `route_id`        VARCHAR(100) DEFAULT NULL COMMENT '命中的网关路由ID',
  `service_id`      VARCHAR(100) DEFAULT NULL COMMENT '目标服务ID(lb://后的服务名)',
  `gray_tag`        VARCHAR(20)  DEFAULT NULL COMMENT '灰度标记(gray/stable)',
  `status`          INT          DEFAULT NULL COMMENT '响应HTTP状态码',
  `cost_ms`         BIGINT       DEFAULT NULL COMMENT '端到端耗时(毫秒)',
  `client_ip`       VARCHAR(64)  DEFAULT NULL COMMENT '客户端IP',
  `user_agent`      VARCHAR(512) DEFAULT NULL COMMENT '客户端User-Agent',
  `request_headers` TEXT         DEFAULT NULL COMMENT '脱敏后的请求头JSON快照',
  `user_id`         VARCHAR(64)  DEFAULT NULL COMMENT '用户ID(可空)',
  `tenant_id`       VARCHAR(64)  DEFAULT NULL COMMENT '租户ID(可空)',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录产生时间',
  PRIMARY KEY (`id`),
  KEY `idx_trace_id` (`trace_id`),
  KEY `idx_route_create` (`route_id`, `create_time`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='网关访问日志表';
