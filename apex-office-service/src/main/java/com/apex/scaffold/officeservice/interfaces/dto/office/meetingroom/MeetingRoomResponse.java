package com.apex.scaffold.officeservice.interfaces.dto.office.meetingroom;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会议室响应
 *
 * <p>返回会议室的完整资源信息，包含启停状态与设备清单。
 *
 * @author Apex Scaffold Team
 * @since 1.3.2
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "会议室响应")
public class MeetingRoomResponse {

    @Schema(description = "会议室ID", example = "1853274912345678901")
    private Long id;

    @Schema(description = "租户ID", example = "41")
    private String tenantId;

    @Schema(description = "会议室编码", example = "A301")
    private String roomCode;

    @Schema(description = "会议室名称", example = "A座301会议室")
    private String roomName;

    @Schema(description = "容纳人数", example = "12")
    private Integer capacity;

    @Schema(description = "位置描述", example = "A座3层东侧")
    private String location;

    @Schema(description = "楼层", example = "3F")
    private String floor;

    @Schema(description = "设备清单", example = "[\"PROJECTOR\", \"VIDEO_CONFERENCE\"]")
    private List<String> equipment;

    @Schema(description = "状态：ENABLED/DISABLED", example = "ENABLED")
    private String status;

    @Schema(description = "备注", example = "靠近茶水间")
    private String remark;

    @Schema(description = "创建时间", example = "2026-10-28T10:00:00")
    private LocalDateTime createTime;

    @Schema(description = "更新时间", example = "2026-10-28T10:00:00")
    private LocalDateTime updateTime;
}
