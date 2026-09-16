package com.apex.scaffold.interfaces.dto.office.calendar;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建日历事件请求DTO
 *
 * <p>用于接收客户端创建日历事件的请求参数，包含事件的基本信息、时间、参与者等。
 * 使用JSR-303注解进行参数校验，确保数据的有效性。
 *
 * @author Apex Scaffold
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "创建日历事件请求")
public class CreateCalendarEventRequest {

    @NotBlank(message = "办公平台类型不能为空")
    @Schema(description = "办公平台类型", example = "feishu", requiredMode = Schema.RequiredMode.REQUIRED)
    private String platform;

    @NotBlank(message = "事件标题不能为空")
    @Schema(description = "事件标题", example = "项目评审会议", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Schema(description = "事件描述", example = "讨论Q1项目规划和资源分配")
    private String description;

    @NotNull(message = "开始时间不能为空")
    @Schema(description = "开始时间", example = "2026-09-15T14:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime startTime;

    @NotNull(message = "结束时间不能为空")
    @Schema(description = "结束时间", example = "2026-09-15T16:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime endTime;

    @Schema(description = "是否全天事件", example = "false")
    private Boolean isAllDay;

    @NotBlank(message = "事件类型不能为空")
    @Schema(
            description = "事件类型",
            example = "MEETING",
            requiredMode = Schema.RequiredMode.REQUIRED,
            allowableValues = {"MEETING", "REMINDER", "OUT_OF_OFFICE", "BIRTHDAY", "ANNIVERSARY", "OTHER"})
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

    @NotBlank(message = "组织者ID不能为空")
    @Schema(description = "组织者ID", example = "user001", requiredMode = Schema.RequiredMode.REQUIRED)
    private String organizerId;

    @NotBlank(message = "组织者姓名不能为空")
    @Schema(description = "组织者姓名", example = "张三", requiredMode = Schema.RequiredMode.REQUIRED)
    private String organizerName;
}
