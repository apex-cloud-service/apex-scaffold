package com.apex.scaffold.office.domain.calendar;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 日历事件聚合根
 *
 * <p>表示一个完整的日历事件，包含事件的基本信息、参与人、提醒设置等。
 * 作为聚合根，负责维护日历事件的完整性和一致性，所有对日历事件的操作都需要通过此聚合根进行。
 *
 * <p>日历事件的主要特性：
 * <ul>
 *   <li>支持多平台同步（飞书、钉钉、企业微信）</li>
 *   <li>支持多人参与和权限控制</li>
 *   <li>支持重复事件和提醒设置</li>
 *   <li>支持会议室预订</li>
 * </ul>
 *
 * @author Apex Scaffold
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarEvent {

    /** 主键ID */
    private Long id;

    /** 租户ID */
    private Long tenantId;

    /** 办公平台类型（feishu/dingtalk/wecom） */
    private String platform;

    /** 平台中的事件ID */
    private String platformEventId;

    /** 事件标题 */
    private String title;

    /** 事件描述 */
    private String description;

    /** 事件类型 */
    private CalendarEventType eventType;

    /** 事件创建人ID */
    private String organizerId;

    /** 事件创建人姓名 */
    private String organizerName;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 是否全天事件 */
    private Boolean isAllDay;

    /** 事件地点 */
    private String location;

    /** 参与人ID列表（JSON格式） */
    private String participantIds;

    /** 参与人姓名列表（JSON格式） */
    private String participantNames;

    /** 会议室ID */
    private String meetingRoomId;

    /** 提醒时间（分钟数，-1表示不提醒） */
    private Integer reminderMinutes;

    /** 是否重复事件 */
    private Boolean isRecurring;

    /** 重复规则（RRULE格式） */
    private String recurrenceRule;

    /** 事件状态（TENTATIVE/CONFIRMED/CANCELLED） */
    private String status;

    /** 乐观锁版本号 */
    private Integer version;

    /** 逻辑删除标记 */
    private Boolean deleted;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 检查事件是否已取消
     *
     * @return 如果事件已取消，返回true；否则返回false
     */
    public boolean isCancelled() {
        return "CANCELLED".equals(status);
    }

    /**
     * 检查事件是否已确认
     *
     * @return 如果事件已确认，返回true；否则返回false
     */
    public boolean isConfirmed() {
        return "CONFIRMED".equals(status);
    }

    /**
     * 检查时间范围是否有效
     *
     * @return 如果结束时间晚于开始时间，返回true；否则返回false
     */
    public boolean isTimeRangeValid() {
        return endTime != null && startTime != null && endTime.isAfter(startTime);
    }

    /**
     * 取消事件
     */
    public void cancel() {
        this.status = "CANCELLED";
    }

    /**
     * 确认事件
     */
    public void confirm() {
        this.status = "CONFIRMED";
    }

    /**
     * 校验并更新事件的开始与结束时间；校验失败时保留原有时间。
     *
     * @param startTime 新的开始时间，不得为 {@code null}
     * @param endTime 新的结束时间，不得为 {@code null}，且必须严格晚于开始时间
     * @throws IllegalArgumentException 结束时间早于或等于开始时间时抛出
     * @throws NullPointerException 任一时间参数为 {@code null} 时抛出
     */
    public void updateTime(LocalDateTime startTime, LocalDateTime endTime) {
        if (endTime.isBefore(startTime) || endTime.isEqual(startTime)) {
            throw new IllegalArgumentException("结束时间必须晚于开始时间");
        }
        this.startTime = startTime;
        this.endTime = endTime;
    }
}
