-- ============================================================
-- V60: 网关访问日志持久化表（PostgreSQL）
-- ============================================================
-- 变更内容：与 mysql/V60__create_gateway_access_log.sql 对齐。
--   新建 sys_gateway_access_log 网关结构化访问日志表，承载
--   RequestLoggingFilter 在链路末端组装、AccessLogService 异步落库的访问日志。
-- 安全：request_headers 仅存脱敏后的请求头 JSON 快照（敏感头一律 *** 占位）。
-- 幂等性：CREATE TABLE IF NOT EXISTS / CREATE INDEX IF NOT EXISTS，可重复执行。
-- 关联方案：.trae/documents/gateway-resilience-enhancement-plan.md §5.3
-- ============================================================

CREATE TABLE IF NOT EXISTS sys_gateway_access_log (
  id              BIGINT       NOT NULL,
  trace_id        VARCHAR(64)  DEFAULT NULL,
  method          VARCHAR(10)  DEFAULT NULL,
  path            VARCHAR(500) DEFAULT NULL,
  route_id        VARCHAR(100) DEFAULT NULL,
  service_id      VARCHAR(100) DEFAULT NULL,
  gray_tag        VARCHAR(20)  DEFAULT NULL,
  status          INTEGER      DEFAULT NULL,
  cost_ms         BIGINT       DEFAULT NULL,
  client_ip       VARCHAR(64)  DEFAULT NULL,
  user_agent      VARCHAR(512) DEFAULT NULL,
  request_headers TEXT         DEFAULT NULL,
  user_id         VARCHAR(64)  DEFAULT NULL,
  tenant_id       VARCHAR(64)  DEFAULT NULL,
  create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_gateway_access_log_trace_id ON sys_gateway_access_log (trace_id);
CREATE INDEX IF NOT EXISTS idx_gateway_access_log_route_create ON sys_gateway_access_log (route_id, create_time);
CREATE INDEX IF NOT EXISTS idx_gateway_access_log_status ON sys_gateway_access_log (status);

COMMENT ON TABLE sys_gateway_access_log IS '网关访问日志表';
COMMENT ON COLUMN sys_gateway_access_log.id IS '主键ID(雪花算法)';
COMMENT ON COLUMN sys_gateway_access_log.trace_id IS '链路追踪ID(贯穿网关与下游服务)';
COMMENT ON COLUMN sys_gateway_access_log.method IS 'HTTP方法(GET/POST/...)';
COMMENT ON COLUMN sys_gateway_access_log.path IS '请求路径(不含查询串)';
COMMENT ON COLUMN sys_gateway_access_log.route_id IS '命中的网关路由ID';
COMMENT ON COLUMN sys_gateway_access_log.service_id IS '目标服务ID(lb://后的服务名)';
COMMENT ON COLUMN sys_gateway_access_log.gray_tag IS '灰度标记(gray/stable)';
COMMENT ON COLUMN sys_gateway_access_log.status IS '响应HTTP状态码';
COMMENT ON COLUMN sys_gateway_access_log.cost_ms IS '端到端耗时(毫秒)';
COMMENT ON COLUMN sys_gateway_access_log.client_ip IS '客户端IP';
COMMENT ON COLUMN sys_gateway_access_log.user_agent IS '客户端User-Agent';
COMMENT ON COLUMN sys_gateway_access_log.request_headers IS '脱敏后的请求头JSON快照';
COMMENT ON COLUMN sys_gateway_access_log.user_id IS '用户ID(可空)';
COMMENT ON COLUMN sys_gateway_access_log.tenant_id IS '租户ID(可空)';
COMMENT ON COLUMN sys_gateway_access_log.create_time IS '记录产生时间';
