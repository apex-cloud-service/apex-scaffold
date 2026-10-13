-- ============================================================
-- V56: 创建领域事件 Outbox 表（PostgreSQL）
-- ============================================================
-- 变更内容：新建 sys_domain_event_outbox，支撑迁移方案 Phase 2.6 登出传播——
--           认证中心登出撤销 Token 后发布 TokenRevokedEvent，先写 Outbox 再由
--           投递器可靠投递，通知各业务服务清理本地权限缓存与在线态副本。
-- 表结构与 apex-interfaces V1__init_schema.sql 保持完全一致，保证跨服务复用同一规范。
-- 幂等性：CREATE TABLE IF NOT EXISTS + 唯一约束，可重复执行。
-- 注意：投递器 SQL 使用 MySQL 方言（ON DUPLICATE KEY UPDATE），PostgreSQL 部署时
--       需由存储层的方言适配（如需）或改用 ON CONFLICT 变体，本迁移仅负责建表。
-- ============================================================

CREATE TABLE IF NOT EXISTS sys_domain_event_outbox (
  id BIGSERIAL PRIMARY KEY,
  event_id VARCHAR(64) NOT NULL,
  event_class VARCHAR(255) NOT NULL,
  payload TEXT NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  attempts INT NOT NULL DEFAULT 0,
  next_attempt_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  locked_by VARCHAR(64) DEFAULT NULL,
  locked_at TIMESTAMP(6) DEFAULT NULL,
  last_error VARCHAR(1000) DEFAULT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  delivered_at TIMESTAMP(6) DEFAULT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_outbox_event_id ON sys_domain_event_outbox(event_id);
CREATE INDEX IF NOT EXISTS idx_outbox_dispatch ON sys_domain_event_outbox(status, next_attempt_at, id);
CREATE INDEX IF NOT EXISTS idx_outbox_locked ON sys_domain_event_outbox(status, locked_at);
COMMENT ON TABLE sys_domain_event_outbox IS '领域事件可靠投递表（登出传播事件总线）';
COMMENT ON COLUMN sys_domain_event_outbox.event_id IS '领域事件唯一标识';
COMMENT ON COLUMN sys_domain_event_outbox.event_class IS '领域事件 Java 类型';
COMMENT ON COLUMN sys_domain_event_outbox.payload IS '事件 JSON 负载';
COMMENT ON COLUMN sys_domain_event_outbox.status IS 'PENDING/PROCESSING/DELIVERED/DEAD';
COMMENT ON COLUMN sys_domain_event_outbox.attempts IS '已失败次数';
COMMENT ON COLUMN sys_domain_event_outbox.next_attempt_at IS '下次投递时间';
COMMENT ON COLUMN sys_domain_event_outbox.locked_by IS '投递实例令牌';
COMMENT ON COLUMN sys_domain_event_outbox.locked_at IS '领取时间';
COMMENT ON COLUMN sys_domain_event_outbox.last_error IS '最近一次错误摘要';
COMMENT ON COLUMN sys_domain_event_outbox.created_at IS '创建时间';
COMMENT ON COLUMN sys_domain_event_outbox.delivered_at IS '完成时间';