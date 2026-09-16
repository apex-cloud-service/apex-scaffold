-- 审批流程表
CREATE TABLE IF NOT EXISTS office_approval_process (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    platform VARCHAR(50) NOT NULL,
    platform_instance_id VARCHAR(200) NOT NULL,
    process_instance_id VARCHAR(200) NOT NULL,
    template_id VARCHAR(200) NOT NULL,
    title VARCHAR(500) NOT NULL,
    initiator_id VARCHAR(100) NOT NULL,
    initiator_name VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL,
    form_data TEXT,
    records TEXT,
    current_approver_ids TEXT,
    initiate_time TIMESTAMP NOT NULL,
    complete_time TIMESTAMP,
    version INT DEFAULT 0,
    deleted BOOLEAN DEFAULT FALSE,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_office_approval_tenant_id ON office_approval_process(tenant_id);
CREATE INDEX IF NOT EXISTS idx_office_approval_platform_instance ON office_approval_process(platform, platform_instance_id);
CREATE INDEX IF NOT EXISTS idx_office_approval_process_instance ON office_approval_process(process_instance_id);
CREATE INDEX IF NOT EXISTS idx_office_approval_initiator ON office_approval_process(initiator_id);
CREATE INDEX IF NOT EXISTS idx_office_approval_status ON office_approval_process(status);
CREATE INDEX IF NOT EXISTS idx_office_approval_create_time ON office_approval_process(create_time);

-- 添加表注释
COMMENT ON TABLE office_approval_process IS '办公平台审批流程表';
COMMENT ON COLUMN office_approval_process.id IS '主键ID';
COMMENT ON COLUMN office_approval_process.tenant_id IS '租户ID';
COMMENT ON COLUMN office_approval_process.platform IS '办公平台类型（feishu/dingtalk/wecom）';
COMMENT ON COLUMN office_approval_process.platform_instance_id IS '平台中的审批实例ID';
COMMENT ON COLUMN office_approval_process.process_instance_id IS '业务流程实例ID';
COMMENT ON COLUMN office_approval_process.template_id IS '审批模板ID';
COMMENT ON COLUMN office_approval_process.title IS '审批标题';
COMMENT ON COLUMN office_approval_process.initiator_id IS '发起人ID';
COMMENT ON COLUMN office_approval_process.initiator_name IS '发起人姓名';
COMMENT ON COLUMN office_approval_process.status IS '审批状态（PENDING/IN_PROGRESS/APPROVED/REJECTED/CANCELLED/TRANSFERRED）';
COMMENT ON COLUMN office_approval_process.form_data IS '审批表单数据（JSON格式）';
COMMENT ON COLUMN office_approval_process.records IS '审批记录列表（JSON格式）';
COMMENT ON COLUMN office_approval_process.current_approver_ids IS '当前审批人ID列表（JSON格式）';
COMMENT ON COLUMN office_approval_process.initiate_time IS '审批发起时间';
COMMENT ON COLUMN office_approval_process.complete_time IS '审批完成时间';
COMMENT ON COLUMN office_approval_process.version IS '乐观锁版本号';
COMMENT ON COLUMN office_approval_process.deleted IS '逻辑删除标记';
COMMENT ON COLUMN office_approval_process.create_time IS '创建时间';
COMMENT ON COLUMN office_approval_process.update_time IS '更新时间';

-- 创建更新触发器
CREATE OR REPLACE FUNCTION update_office_approval_process_update_time()
RETURNS TRIGGER AS $$
BEGIN
    NEW.update_time = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_office_approval_process_update_time
    BEFORE UPDATE ON office_approval_process
    FOR EACH ROW
    EXECUTE FUNCTION update_office_approval_process_update_time();
