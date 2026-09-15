package com.apex.scaffold.infrastructure.office.po;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 日历事件持久化对象
 *
 * <p>对应数据库表office_calendar_event，用于存储日历事件的持久化数据。
 * 使用MyBatis-Plus注解进行ORM映射，支持乐观锁和逻辑删除。
 *
 * @author Apex Scaffold
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("office_calendar_event")
public class CalendarEventPO {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户ID */
    @TableField("tenant_id")
    private Long tenantId;

    /** 办公平台类型 */
    @TableField("platform")
    private String platform;

    /** 平台中的事件ID */
    @TableField("platform_event_id")
    private String platformEventId;

    /** 事件标题 */
    @TableField("title")
    private String title;

    /** 事件描述 */
    @TableField("description")
    private String description;

    /** 事件类型 */
    @TableField("event_type")
    private String eventType;

    /** 事件创建人ID */
    @TableField("organizer_id")
    private String organizerId;

    /** 事件创建人姓名 */
    @TableField("organizer_name")
    private String organizerName;

    /** 开始时间 */
    @TableField("start_time")
    private LocalDateTime startTime;

    /** 结束时间 */
    @TableField("end_time")
    private LocalDateTime endTime;

    /** 是否全天事件 */
    @TableField("is_all_day")
    private Boolean isAllDay;

    /** 事件地点 */
    @TableField("location")
    private String location;

    /** 参与人ID列表（JSON格式） */
    @TableField("participant_ids")
    private String participantIds;

    /** 参与人姓名列表（JSON格式） */
    @TableField("participant_names")
    private String participantNames;

    /** 会议室ID */
    @TableField("meeting_room_id")
    private String meetingRoomId;

    /** 提醒时间（分钟数） */
    @TableField("reminder_minutes")
    private Integer reminderMinutes;

    /** 是否重复事件 */
    @TableField("is_recurring")
    private Boolean isRecurring;

    /** 重复规则 */
    @TableField("recurrence_rule")
    private String recurrenceRule;

    /** 事件状态 */
    @TableField("status")
    private String status;

    /** 乐观锁版本号 */
    @Version
    @TableField("version")
    private Integer version;

    /** 逻辑删除标记 */
    @TableLogic
    @TableField("deleted")
    private Boolean deleted;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
