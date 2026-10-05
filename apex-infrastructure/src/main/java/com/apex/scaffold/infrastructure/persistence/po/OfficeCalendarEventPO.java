package com.apex.scaffold.infrastructure.persistence.po;

import com.apex.scaffold.common.persistence.po.BasePO;
import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 办公平台日历事件持久化对象。
 *
 * <p>对应 {@code office_calendar_event} 表，记录从钉钉、飞书、企业微信等办公平台
 * 同步或创建的日历事件：平台与平台事件标识、标题、描述、事件类型、组织者、
 * 起止时间、全天标志、地点、参与人、会议室、提醒时间，以及重复规则与事件状态。
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName("office_calendar_event")
public class OfficeCalendarEventPO extends BasePO {

    @TableField("tenant_id")
    private String tenantId;

    @TableField("platform")
    private String platform;

    @TableField("platform_event_id")
    private String platformEventId;

    @TableField("title")
    private String title;

    @TableField("description")
    private String description;

    @TableField("event_type")
    private String eventType;

    @TableField("organizer_id")
    private String organizerId;

    @TableField("organizer_name")
    private String organizerName;

    @TableField("start_time")
    private LocalDateTime startTime;

    @TableField("end_time")
    private LocalDateTime endTime;

    @TableField("is_all_day")
    private Boolean isAllDay;

    @TableField("location")
    private String location;

    @TableField("participant_ids")
    private String participantIds;

    @TableField("participant_names")
    private String participantNames;

    @TableField("meeting_room_id")
    private String meetingRoomId;

    @TableField("reminder_minutes")
    private Integer reminderMinutes;

    @TableField("is_recurring")
    private Boolean isRecurring;

    @TableField("recurrence_rule")
    private String recurrenceRule;

    @TableField("status")
    private String status;
}
