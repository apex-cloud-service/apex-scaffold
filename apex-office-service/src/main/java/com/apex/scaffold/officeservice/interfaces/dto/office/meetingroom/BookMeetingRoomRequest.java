package com.apex.scaffold.officeservice.interfaces.dto.office.meetingroom;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会议室预订请求
 *
 * <p>预订人取自已认证身份，不接受客户端指定；时间区间必须有效且与该会议室
 * 已有未取消事件不重叠。
 *
 * @author Apex Scaffold Team
 * @since 1.4.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "会议室预订请求")
public class BookMeetingRoomRequest {

    @Size(max = 255, message = "会议主题长度不能超过255")
    @Schema(description = "会议主题，缺省使用会议室名称", example = "项目周会")
    private String title;

    @NotNull(message = "开始时间不能为空")
    @Schema(description = "开始时间", example = "2026-10-28T14:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime startTime;

    @NotNull(message = "结束时间不能为空")
    @Schema(description = "结束时间", example = "2026-10-28T16:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime endTime;
}
