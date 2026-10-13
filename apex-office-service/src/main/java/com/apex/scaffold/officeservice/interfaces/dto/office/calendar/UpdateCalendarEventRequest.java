package com.apex.scaffold.officeservice.interfaces.dto.office.calendar;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新日历事件请求DTO
 *
 * <p>用于接收客户端更新日历事件的请求参数。
 * 所有字段均为可选，只更新提供的字段。
 *
 * @author Apex Scaffold
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "更新日历事件请求")
public class UpdateCalendarEventRequest {

    @Schema(description = "事件标题", example = "项目评审会议（延期）")
    private String title;

    @Schema(description = "事件描述", example = "因故延期到下周")
    private String description;

    @Schema(description = "开始时间", example = "2026-09-22T14:00:00")
    private LocalDateTime startTime;

    @Schema(description = "结束时间", example = "2026-09-22T16:00:00")
    private LocalDateTime endTime;

    @Schema(description = "是否全天事件", example = "false")
    private Boolean isAllDay;

    @Schema(description = "地点", example = "线上会议室")
    private String location;

    @Schema(description = "参与者ID列表", example = "[\"user001\", \"user002\", \"user003\"]")
    private List<String> attendeeIds;

    @Schema(description = "参与者姓名列表", example = "[\"张三\", \"李四\", \"王五\"]")
    private List<String> attendeeNames;

    @Schema(description = "提醒时间（分钟）", example = "30")
    private Integer reminderMinutes;

    @Schema(description = "重复规则", example = "FREQ=WEEKLY;BYDAY=TU,TH")
    private String recurrenceRule;
}
