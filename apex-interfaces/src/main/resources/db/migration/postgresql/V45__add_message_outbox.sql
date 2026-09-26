-- Apex Scaffold - 消息发件箱表(Outbox模式)
-- 版本: V45
-- 功能: 实现本地消息表,保证业务事务与消息发送的原子性
-- 作者: Apex Team
-- 日期: 2026-09-18

-- 创建消息发件箱表
CREATE TABLE IF NOT EXISTS sys_message_outbox (
  -- 主键与标识
  id BIGSERIAL PRIMARY KEY,
  message_id VARCHAR(64) NOT NULL UNIQUE,
  
  -- 消息路由信息
  topic VARCHAR(255) NOT NULL,
  tag VARCHAR(64) NOT NULL,
  payload TEXT NOT NULL,
  message_type VARCHAR(32) NOT NULL DEFAULT 'BUSINESS_MESSAGE',
  
  -- 状态管理
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  
  -- 重试控制
  attempts INT NOT NULL DEFAULT 0,
  max_attempts INT NOT NULL DEFAULT 5,
  next_attempt_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  retry_delay_seconds INT NOT NULL DEFAULT 10,
  
  -- 分布式锁
  locked_by VARCHAR(64) DEFAULT NULL,
  locked_at TIMESTAMP(6) DEFAULT NULL,
  lock_timeout_seconds INT NOT NULL DEFAULT 300,
  
  -- 错误追踪
  last_error VARCHAR(1000) DEFAULT NULL,
  
  -- 租户隔离
  tenant_id VARCHAR(64) NOT NULL,
  
  -- 审计字段
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  delivered_at TIMESTAMP(6) DEFAULT NULL
);

-- 创建性能索引
CREATE INDEX idx_message_outbox_dispatch 
ON sys_message_outbox(status, next_attempt_at, tenant_id);

CREATE INDEX idx_message_outbox_locked 
ON sys_message_outbox(status, locked_at, locked_by);

CREATE INDEX idx_message_outbox_tenant 
ON sys_message_outbox(tenant_id, created_at);

-- 创建部分索引(仅对PENDING和PROCESSING状态建索引,优化查询性能)
CREATE INDEX idx_message_outbox_status_partial 
ON sys_message_outbox(status, next_attempt_at) 
WHERE status IN ('PENDING', 'PROCESSING');

-- 添加表注释
COMMENT ON TABLE sys_message_outbox IS '消息发件箱表-Outbox模式实现本地消息表';

-- 添加列注释
COMMENT ON COLUMN sys_message_outbox.id IS '主键ID';
COMMENT ON COLUMN sys_message_outbox.message_id IS '消息唯一标识(全局唯一,雪花算法生成)';
COMMENT ON COLUMN sys_message_outbox.topic IS 'RocketMQ Topic主题';
COMMENT ON COLUMN sys_message_outbox.tag IS 'RocketMQ Tag标签,用于消息过滤';
COMMENT ON COLUMN sys_message_outbox.payload IS '消息体JSON载荷,业务数据';
COMMENT ON COLUMN sys_message_outbox.message_type IS '消息类型:DOMAIN_EVENT(领域事件)/BUSINESS_MESSAGE(业务消息)';
COMMENT ON COLUMN sys_message_outbox.status IS '消息状态:PENDING(待投递)/PROCESSING(投递中)/DELIVERED(已投递)/DEAD(死信)';
COMMENT ON COLUMN sys_message_outbox.attempts IS '已尝试投递次数';
COMMENT ON COLUMN sys_message_outbox.max_attempts IS '最大尝试次数,超过后进入死信';
COMMENT ON COLUMN sys_message_outbox.next_attempt_at IS '下次尝试时间,支持延迟投递';
COMMENT ON COLUMN sys_message_outbox.retry_delay_seconds IS '重试延迟基数(秒),实际延迟=基数*2^(attempts-1),指数退避';
COMMENT ON COLUMN sys_message_outbox.locked_by IS '锁定实例ID(UUID),防止多实例并发投递';
COMMENT ON COLUMN sys_message_outbox.locked_at IS '锁定时间,用于检测过期锁';
COMMENT ON COLUMN sys_message_outbox.lock_timeout_seconds IS '锁超时时间(秒),默认5分钟';
COMMENT ON COLUMN sys_message_outbox.last_error IS '最近一次投递失败的错误信息';
COMMENT ON COLUMN sys_message_outbox.tenant_id IS '租户ID,实现多租户数据隔离';
COMMENT ON COLUMN sys_message_outbox.created_at IS '消息创建时间';
COMMENT ON COLUMN sys_message_outbox.updated_at IS '最后更新时间';
COMMENT ON COLUMN sys_message_outbox.delivered_at IS '投递成功时间';

-- 创建触发器:自动更新updated_at字段
CREATE OR REPLACE FUNCTION update_sys_message_outbox_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_sys_message_outbox_updated_at
BEFORE UPDATE ON sys_message_outbox
FOR EACH ROW
EXECUTE FUNCTION update_sys_message_outbox_updated_at();

