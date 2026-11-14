package com.apex.scaffold.officeservice.interfaces.dto.office.calendar;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 日历事件响应DTO
 *
 * <p>用于向客户端返回日历事件的详细信息。
 * 包含事件的所有属性和状态信息。
 *
 * @author Apex Scaffold
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "日历事件响应")
public class CalendarEventResponse {

    @Schema(description = "事件ID", example = "1001")
    private Long id;

    @Schema(description = "租户ID", example = "1")
    private Long tenantId;

    @Schema(description = "办公平台类型", example = "feishu")
    private String platform;

    @Schema(description = "平台中的事件ID", example = "evt_abc123")
    private String platformEventId;

    @Schema(description = "事件标题", example = "项目评审会议")
    private String title;

    @Schema(description = "事件描述", example = "讨论Q1项目规划和资源分配")
    private String description;

    @Schema(description = "开始时间", example = "2026-09-15T14:00:00")
    private LocalDateTime startTime;

    @Schema(description = "结束时间", example = "2026-09-15T16:00:00")
    private LocalDateTime endTime;

    @Schema(description = "是否全天事件", example = "false")
    private Boolean isAllDay;

    @Schema(description = "事件类型", example = "MEETING")
    private String eventType;

    @Schema(description = "地点", example = "A座301会议室")
    private String location;

    @Schema(description = "参与者ID列表", example = "[\"user001\", \"user002\"]")
    private List<String> attendeeIds;

    @Schema(description = "参与者姓名列表", example = "[\"张三\", \"李四\"]")
    private List<String> attendeeNames;

    @Schema(description = "提醒时间（分钟）", example = "15")
    private Integer reminderMinutes;

    @Schema(description = "是否重复事件", example = "false")
    private Boolean isRecurring;

    @Schema(description = "重复规则", example = "FREQ=WEEKLY;BYDAY=MO,WE,FR")
    private String recurrenceRule;

    @Schema(description = "组织者ID", example = "user001")
    private String organizerId;

    @Schema(description = "组织者姓名", example = "张三")
    private String organizerName;

    @Schema(description = "事件状态", example = "CONFIRMED")
    private String status;

    @Schema(description = "是否已取消", example = "false")
    private Boolean isCancelled;

    @Schema(description = "创建时间", example = "2026-09-10T10:00:00")
    private LocalDateTime createTime;

    @Schema(description = "更新时间", example = "2026-09-10T10:00:00")
    private LocalDateTime updateTime;
}
