-- ============================================================
-- Apex Scaffold - 办公服务独立库 V7：会议室资源（PostgreSQL）
-- ============================================================
-- 【用途】
--   O06b 会议室资源管理：为租户提供可维护、可预订的会议室主数据。
--   此前会议室只以 office_calendar_event.meeting_room_id（VARCHAR）
--   弱关联字符串存在，缺少可校验的资源实体与唯一性约束。
--
-- 【变更】
--   新建 office_meeting_room，保存会议室编码、名称、容纳人数、
--   位置、楼层、设备清单（JSON 字符串）、启停状态与备注。
--
-- 【关联约定】
--   占用关系复用日历事件：预订写入 office_calendar_event，
--   其 meeting_room_id 保存本表的 room_code（租户内唯一）。
--   会议室删除采用逻辑删除，历史占用记录保留。
--
-- 【约束】
--   status 取值 ENABLED/DISABLED（应用层白名单校验）。
--   (tenant_id, room_code) 与 (tenant_id, room_name) 在未删除范围内唯一。
--
-- 【方言约定】
--   PostgreSQL 使用 TIMESTAMP 与 SMALLINT 逻辑删除字段；主键由应用侧
--   雪花算法生成，不使用自增序列。
--
-- @author Apex Team
-- @version 1.0.0
-- @since 1.4.0
-- ============================================================

CREATE TABLE IF NOT EXISTS office_meeting_room (
    id          BIGINT       NOT NULL,
    tenant_id   VARCHAR(64)  NOT NULL,
    room_code   VARCHAR(64)  NOT NULL,
    room_name   VARCHAR(128) NOT NULL,
    capacity    INT,
    location    VARCHAR(255),
    floor       VARCHAR(32),
    equipment   VARCHAR(1024),
    status      VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    remark      VARCHAR(512),
    create_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     SMALLINT     NOT NULL DEFAULT 0,
    version     INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_office_meeting_room_tenant_code ON office_meeting_room (tenant_id, room_code, deleted);
CREATE UNIQUE INDEX IF NOT EXISTS uk_office_meeting_room_tenant_name ON office_meeting_room (tenant_id, room_name, deleted);
CREATE INDEX IF NOT EXISTS idx_office_meeting_room_tenant_status ON office_meeting_room (tenant_id, status, deleted);
COMMENT ON TABLE  office_meeting_room IS '办公会议室资源';
COMMENT ON COLUMN office_meeting_room.room_code IS '会议室编码（租户内唯一）';
COMMENT ON COLUMN office_meeting_room.equipment IS '设备清单（JSON 数组字符串）';
COMMENT ON COLUMN office_meeting_room.status IS '状态（ENABLED/DISABLED）';
