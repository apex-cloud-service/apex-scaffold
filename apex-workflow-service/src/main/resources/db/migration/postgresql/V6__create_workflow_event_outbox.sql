-- 工作流域领域事件可靠投递表（与 MySQL V6 配对）。
-- 表名与共享 Outbox 保持一致，便于复用同一投递器与运维口径。
CREATE TABLE IF NOT EXISTS sys_domain_event_outbox (
  id BIGSERIAL PRIMARY KEY,
  event_id VARCHAR(64) NOT NULL,
  event_class VARCHAR(255) NOT NULL,
  payload TEXT NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  attempts INT NOT NULL DEFAULT 0,
  next_attempt_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  locked_by VARCHAR(64),
  locked_at TIMESTAMP(6),
  last_error VARCHAR(1000),
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  delivered_at TIMESTAMP(6)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_outbox_event_id ON sys_domain_event_outbox (event_id);
CREATE INDEX IF NOT EXISTS idx_outbox_dispatch ON sys_domain_event_outbox (status, next_attempt_at, id);
CREATE INDEX IF NOT EXISTS idx_outbox_locked ON sys_domain_event_outbox (status, locked_at);
