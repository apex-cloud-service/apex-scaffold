package com.apex.scaffold.domain.model.entity;

import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 办公日历事件实体
 *
 * <p>用于表示来自第三方办公平台的日历事件，支持会议室预订、参与者管理和循环事件。
 *
 * @author Apex Scaffold Team
 * @since 1.2.0
 */
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class OfficeCalendarEventEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private String platform;
    private String platformEventId;
    private String title;
    private String description;
    private String eventType;
    private String organizerId;
    private String organizerName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Boolean isAllDay;
    private String location;
    private String participantIds;
    private String participantNames;
    private String meetingRoomId;
    private Integer reminderMinutes;
    private Boolean isRecurring;
    private String recurrenceRule;
    private String status;
    private String tenantId;

    public OfficeCalendarEventEntity() {
        super();
    }

    /**
     * 设置事件来源的第三方办公平台标识，并刷新实体更新时间。
     *
     * @param platform 办公平台标识（如飞书、钉钉等）
     */
    public void setPlatform(String platform) {
        this.platform = platform;
        touch();
    }

    /**
     * 设置第三方平台中的事件唯一标识，并刷新实体更新时间。
     *
     * @param platformEventId 来源平台侧的事件 ID，用于与本地记录对齐
     */
    public void setPlatformEventId(String platformEventId) {
        this.platformEventId = platformEventId;
        touch();
    }

    /**
     * 设置事件标题，并刷新实体更新时间。
     *
     * @param title 日历事件的标题
     */
    public void setTitle(String title) {
        this.title = title;
        touch();
    }

    /**
     * 设置事件描述，并刷新实体更新时间。
     *
     * @param description 日历事件的详细描述，可为空
     */
    public void setDescription(String description) {
        this.description = description;
        touch();
    }

    /**
     * 设置事件类型，并刷新实体更新时间。
     *
     * @param eventType 事件类型字符串（对应 MEETING/TASK/REMINDER/VACATION/BIRTHDAY/OTHER）
     */
    public void setEventType(String eventType) {
        this.eventType = eventType;
        touch();
    }

    /**
     * 设置事件组织者 ID，并刷新实体更新时间。
     *
     * @param organizerId 事件创建人（组织者）的用户 ID
     */
    public void setOrganizerId(String organizerId) {
        this.organizerId = organizerId;
        touch();
    }

    /**
     * 设置事件组织者姓名，并刷新实体更新时间。
     *
     * @param organizerName 事件创建人（组织者）的姓名
     */
    public void setOrganizerName(String organizerName) {
        this.organizerName = organizerName;
        touch();
    }

    /**
     * 设置事件开始时间，并刷新实体更新时间。
     *
     * @param startTime 日历事件的开始时间
     */
    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
        touch();
    }

    /**
     * 设置事件结束时间，并刷新实体更新时间。
     *
     * @param endTime 日历事件的结束时间
     */
    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
        touch();
    }

    /**
     * 设置是否全天事件，并刷新实体更新时间。
     *
     * @param isAllDay true-全天事件，false-非全天事件
     */
    public void setIsAllDay(Boolean isAllDay) {
        this.isAllDay = isAllDay;
        touch();
    }

    /**
     * 设置事件地点，并刷新实体更新时间。
     *
     * @param location 日历事件的发生地点，可为空
     */
    public void setLocation(String location) {
        this.location = location;
        touch();
    }

    /**
     * 设置参与人 ID 列表，并刷新实体更新时间。
     *
     * @param participantIds 参与人 ID 列表（JSON 格式存储）
     */
    public void setParticipantIds(String participantIds) {
        this.participantIds = participantIds;
        touch();
    }

    /**
     * 设置参与人姓名列表，并刷新实体更新时间。
     *
     * @param participantNames 参与人姓名列表（JSON 格式存储）
     */
    public void setParticipantNames(String participantNames) {
        this.participantNames = participantNames;
        touch();
    }

    /**
     * 设置会议室 ID，并刷新实体更新时间。
     *
     * @param meetingRoomId 预订的会议室 ID，非会议室预订事件时为空
     */
    public void setMeetingRoomId(String meetingRoomId) {
        this.meetingRoomId = meetingRoomId;
        touch();
    }

    /**
     * 设置提醒时间，并刷新实体更新时间。
     *
     * @param reminderMinutes 事件开始前的提醒提前分钟数（-1 表示不提醒）
     */
    public void setReminderMinutes(Integer reminderMinutes) {
        this.reminderMinutes = reminderMinutes;
        touch();
    }

    /**
     * 设置是否循环事件，并刷新实体更新时间。
     *
     * @param isRecurring true-循环事件，false-单次事件
     */
    public void setIsRecurring(Boolean isRecurring) {
        this.isRecurring = isRecurring;
        touch();
    }

    /**
     * 设置循环事件的重复规则，并刷新实体更新时间。
     *
     * @param recurrenceRule 重复规则描述（RRULE 格式），非循环事件时为空
     */
    public void setRecurrenceRule(String recurrenceRule) {
        this.recurrenceRule = recurrenceRule;
        touch();
    }

    /**
     * 设置事件状态，并刷新实体更新时间。
     *
     * @param status 事件状态（TENTATIVE/CONFIRMED/CANCELLED）
     */
    public void setStatus(String status) {
        this.status = status;
        touch();
    }

    /**
     * 设置租户 ID，并刷新实体更新时间。
     *
     * @param tenantId 事件所属租户的唯一标识
     */
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
        touch();
    }
}
