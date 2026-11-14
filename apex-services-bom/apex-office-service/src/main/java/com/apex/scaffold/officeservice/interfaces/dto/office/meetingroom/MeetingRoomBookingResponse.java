package com.apex.scaffold.officeservice.interfaces.dto.office.meetingroom;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会议室预订结果响应
 *
 * <p>一次预订对应一条会议室日历事件，返回事件主键供后续取消使用。
 *
 * @author Apex Scaffold Team
 * @since 1.3.2
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "会议室预订结果")
public class MeetingRoomBookingResponse {

    @Schema(description = "预订事件ID", example = "1853274912345678901")
    private Long eventId;

    @Schema(description = "会议室ID", example = "1853274912345678999")
    private Long meetingRoomId;

    @Schema(description = "会议室编码", example = "A301")
    private String meetingRoomCode;

    @Schema(description = "会议主题", example = "项目周会")
    private String title;

    @Schema(description = "预订人ID", example = "1001")
    private String organizerId;

    @Schema(description = "预订人姓名", example = "张三")
    private String organizerName;

    @Schema(description = "开始时间", example = "2026-10-28T14:00:00")
    private LocalDateTime startTime;

    @Schema(description = "结束时间", example = "2026-10-28T16:00:00")
    private LocalDateTime endTime;

    @Schema(description = "状态：CONFIRMED/CANCELLED", example = "CONFIRMED")
    private String status;

    @Schema(description = "地点", example = "A座3层东侧")
    private String location;
}
