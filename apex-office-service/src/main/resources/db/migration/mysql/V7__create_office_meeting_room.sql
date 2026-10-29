-- ============================================================
-- Apex Scaffold - 办公服务独立库 V7：会议室资源（MySQL）
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
--   MySQL 统一使用 DATETIME，禁止 TIMESTAMP；字符集 utf8mb4。
--
-- @author Apex Team
-- @version 1.0.0
-- @since 1.4.0
-- ============================================================

CREATE TABLE IF NOT EXISTS `office_meeting_room` (
    `id`          BIGINT       NOT NULL COMMENT '主键ID（雪花算法）',
    `tenant_id`   VARCHAR(64)  NOT NULL COMMENT '租户ID',
    `room_code`   VARCHAR(64)  NOT NULL COMMENT '会议室编码（租户内唯一）',
    `room_name`   VARCHAR(128) NOT NULL COMMENT '会议室名称',
    `capacity`    INT                   DEFAULT NULL COMMENT '容纳人数',
    `location`    VARCHAR(255)          DEFAULT NULL COMMENT '位置描述',
    `floor`       VARCHAR(32)           DEFAULT NULL COMMENT '楼层',
    `equipment`   VARCHAR(1024)         DEFAULT NULL COMMENT '设备清单（JSON 数组字符串）',
    `status`      VARCHAR(16)  NOT NULL DEFAULT 'ENABLED' COMMENT '状态（ENABLED/DISABLED）',
    `remark`      VARCHAR(512)          DEFAULT NULL COMMENT '备注',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0-未删除 1-已删除）',
    `version`     INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_office_meeting_room_tenant_code` (`tenant_id`, `room_code`, `deleted`),
    UNIQUE KEY `uk_office_meeting_room_tenant_name` (`tenant_id`, `room_name`, `deleted`),
    KEY `idx_office_meeting_room_tenant_status` (`tenant_id`, `status`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='办公会议室资源';
