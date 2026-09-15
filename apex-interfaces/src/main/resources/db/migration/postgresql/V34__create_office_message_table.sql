-- V29__create_office_message_table.sql
-- 创建办公消息表

CREATE TABLE IF NOT EXISTS office_message (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    platform VARCHAR(50) NOT NULL,
    app_id BIGINT NOT NULL,
    message_type VARCHAR(50) NOT NULL,
    recipient_type VARCHAR(50) NOT NULL,
    recipient_ids TEXT NOT NULL,
    title VARCHAR(500),
    content TEXT NOT NULL,
    extra_data TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    platform_message_id VARCHAR(200),
    result_message VARCHAR(1000),
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retry_count INTEGER NOT NULL DEFAULT 3,
    sent_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    deleted SMALLINT NOT NULL DEFAULT 0,
    version INTEGER NOT NULL DEFAULT 0
);

-- 添加注释
COMMENT ON TABLE office_message IS '办公消息表';
COMMENT ON COLUMN office_message.id IS '消息ID（主键）';
COMMENT ON COLUMN office_message.tenant_id IS '租户ID';
COMMENT ON COLUMN office_message.platform IS '办公平台类型（FEISHU、DINGTALK、WECOM）';
COMMENT ON COLUMN office_message.app_id IS '应用ID';
COMMENT ON COLUMN office_message.message_type IS '消息类型（TEXT、MARKDOWN、CARD、IMAGE、FILE）';
COMMENT ON COLUMN office_message.recipient_type IS '接收者类型（USER、DEPARTMENT、GROUP、USER_LIST）';
COMMENT ON COLUMN office_message.recipient_ids IS '接收者ID列表（JSON数组）';
COMMENT ON COLUMN office_message.title IS '消息标题';
COMMENT ON COLUMN office_message.content IS '消息内容';
COMMENT ON COLUMN office_message.extra_data IS '消息附加数据（JSON格式）';
COMMENT ON COLUMN office_message.status IS '发送状态（PENDING、SENDING、SUCCESS、FAILED、PARTIAL_SUCCESS）';
COMMENT ON COLUMN office_message.platform_message_id IS '平台返回的消息ID';
COMMENT ON COLUMN office_message.result_message IS '发送结果消息';
COMMENT ON COLUMN office_message.retry_count IS '重试次数';
COMMENT ON COLUMN office_message.max_retry_count IS '最大重试次数';
COMMENT ON COLUMN office_message.sent_at IS '发送时间';
COMMENT ON COLUMN office_message.created_at IS '创建时间';
COMMENT ON COLUMN office_message.updated_at IS '更新时间';
COMMENT ON COLUMN office_message.created_by IS '创建者ID';
COMMENT ON COLUMN office_message.updated_by IS '更新者ID';
COMMENT ON COLUMN office_message.deleted IS '逻辑删除标记（0-未删除，1-已删除）';
COMMENT ON COLUMN office_message.version IS '乐观锁版本号';

-- 创建索引
CREATE INDEX idx_office_message_tenant_platform ON office_message(tenant_id, platform);
CREATE INDEX idx_office_message_tenant_status ON office_message(tenant_id, status);
CREATE INDEX idx_office_message_platform_message_id ON office_message(platform_message_id);
CREATE INDEX idx_office_message_created_at ON office_message(created_at);
CREATE INDEX idx_office_message_sent_at ON office_message(sent_at);

-- 创建更新时间触发器
CREATE OR REPLACE FUNCTION update_office_message_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_office_message_updated_at
BEFORE UPDATE ON office_message
FOR EACH ROW
EXECUTE FUNCTION update_office_message_updated_at();
