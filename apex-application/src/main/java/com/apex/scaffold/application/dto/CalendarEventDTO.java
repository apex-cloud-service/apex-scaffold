package com.apex.scaffold.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 日历事件 DTO。
 *
 * <p>办公集成业务域的日历事件数据载体，承载事件标题、类型、起止时间、参与人、
 * 会议室、提醒与重复规则等信息，用于 {@code OfficeIntegrationService}
 * 日历事件管理用例中实体与服务层之间的数据传输。
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "日历事件DTO")
public class CalendarEventDTO {

    @Schema(description = "事件ID")
    private Long id;

    @Schema(description = "办公平台类型")
    private String platform;

    @Schema(description = "平台事件ID")
    private String platformEventId;

    @Schema(description = "事件标题", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Schema(description = "事件描述")
    private String description;

    @Schema(description = "事件类型")
    private String eventType;

    @Schema(description = "组织者ID")
    private String organizerId;

    @Schema(description = "组织者姓名")
    private String organizerName;

    @Schema(description = "开始时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime startTime;

    @Schema(description = "结束时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime endTime;

    @Schema(description = "是否全天事件")
    private Boolean isAllDay;

    @Schema(description = "地点")
    private String location;

    @Schema(description = "参与人IDs")
    private String participantIds;

    @Schema(description = "参与人姓名")
    private String participantNames;

    @Schema(description = "会议室ID")
    private String meetingRoomId;

    @Schema(description = "提醒时间(分钟)")
    private Integer reminderMinutes;

    @Schema(description = "是否重复事件")
    private Boolean isRecurring;

    @Schema(description = "重复规则")
    private String recurrenceRule;

    @Schema(description = "事件状态")
    private String status;
}
