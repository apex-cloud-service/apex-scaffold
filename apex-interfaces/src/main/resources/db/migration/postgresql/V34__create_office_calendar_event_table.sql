-- 日历事件表
CREATE TABLE IF NOT EXISTS office_calendar_event (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    platform VARCHAR(50) NOT NULL,
    platform_event_id VARCHAR(200) NOT NULL,
    title VARCHAR(500) NOT NULL,
    description TEXT,
    event_type VARCHAR(50) NOT NULL,
    organizer_id VARCHAR(100) NOT NULL,
    organizer_name VARCHAR(100) NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    is_all_day BOOLEAN DEFAULT FALSE,
    location VARCHAR(500),
    participant_ids TEXT,
    participant_names TEXT,
    meeting_room_id VARCHAR(100),
    reminder_minutes INT DEFAULT -1,
    is_recurring BOOLEAN DEFAULT FALSE,
    recurrence_rule TEXT,
    status VARCHAR(50) DEFAULT 'TENTATIVE',
    version INT DEFAULT 0,
    deleted BOOLEAN DEFAULT FALSE,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_calendar_event_tenant_id ON office_calendar_event(tenant_id);
CREATE INDEX IF NOT EXISTS idx_calendar_event_platform_event ON office_calendar_event(platform, platform_event_id);
CREATE INDEX IF NOT EXISTS idx_calendar_event_organizer ON office_calendar_event(organizer_id);
CREATE INDEX IF NOT EXISTS idx_calendar_event_time_range ON office_calendar_event(start_time, end_time);
CREATE INDEX IF NOT EXISTS idx_calendar_event_meeting_room ON office_calendar_event(meeting_room_id, start_time, end_time);
CREATE INDEX IF NOT EXISTS idx_calendar_event_status ON office_calendar_event(status);
CREATE INDEX IF NOT EXISTS idx_calendar_event_create_time ON office_calendar_event(create_time);

-- 添加表注释
COMMENT ON TABLE office_calendar_event IS '办公平台日历事件表';
COMMENT ON COLUMN office_calendar_event.id IS '主键ID';
COMMENT ON COLUMN office_calendar_event.tenant_id IS '租户ID';
COMMENT ON COLUMN office_calendar_event.platform IS '办公平台类型（feishu/dingtalk/wecom）';
COMMENT ON COLUMN office_calendar_event.platform_event_id IS '平台中的事件ID';
COMMENT ON COLUMN office_calendar_event.title IS '事件标题';
COMMENT ON COLUMN office_calendar_event.description IS '事件描述';
COMMENT ON COLUMN office_calendar_event.event_type IS '事件类型（MEETING/TASK/REMINDER/VACATION/BIRTHDAY/OTHER）';
COMMENT ON COLUMN office_calendar_event.organizer_id IS '事件创建人ID';
COMMENT ON COLUMN office_calendar_event.organizer_name IS '事件创建人姓名';
COMMENT ON COLUMN office_calendar_event.start_time IS '开始时间';
COMMENT ON COLUMN office_calendar_event.end_time IS '结束时间';
COMMENT ON COLUMN office_calendar_event.is_all_day IS '是否全天事件';
COMMENT ON COLUMN office_calendar_event.location IS '事件地点';
COMMENT ON COLUMN office_calendar_event.participant_ids IS '参与人ID列表（JSON格式）';
COMMENT ON COLUMN office_calendar_event.participant_names IS '参与人姓名列表（JSON格式）';
COMMENT ON COLUMN office_calendar_event.meeting_room_id IS '会议室ID';
COMMENT ON COLUMN office_calendar_event.reminder_minutes IS '提醒时间（分钟数，-1表示不提醒）';
COMMENT ON COLUMN office_calendar_event.is_recurring IS '是否重复事件';
COMMENT ON COLUMN office_calendar_event.recurrence_rule IS '重复规则（RRULE格式）';
COMMENT ON COLUMN office_calendar_event.status IS '事件状态（TENTATIVE/CONFIRMED/CANCELLED）';
COMMENT ON COLUMN office_calendar_event.version IS '乐观锁版本号';
COMMENT ON COLUMN office_calendar_event.deleted IS '逻辑删除标记';
COMMENT ON COLUMN office_calendar_event.create_time IS '创建时间';
COMMENT ON COLUMN office_calendar_event.update_time IS '更新时间';

-- 创建更新触发器
CREATE OR REPLACE FUNCTION update_office_calendar_event_update_time()
RETURNS TRIGGER AS $$
BEGIN
    NEW.update_time = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_office_calendar_event_update_time
    BEFORE UPDATE ON office_calendar_event
    FOR EACH ROW
    EXECUTE FUNCTION update_office_calendar_event_update_time();
