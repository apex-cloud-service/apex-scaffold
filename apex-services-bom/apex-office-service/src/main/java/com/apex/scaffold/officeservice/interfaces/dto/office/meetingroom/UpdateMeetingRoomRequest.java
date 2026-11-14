package com.apex.scaffold.officeservice.interfaces.dto.office.meetingroom;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新会议室请求
 *
 * <p>仅更新显式传入的字段，未传字段保持原值。会议室编码与名称变更时仍需满足租户内唯一。
 *
 * @author Apex Scaffold Team
 * @since 1.3.2
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "更新会议室请求")
public class UpdateMeetingRoomRequest {

    @Size(max = 64, message = "会议室编码长度不能超过64")
    @Schema(description = "会议室编码（租户内唯一）", example = "A301")
    private String roomCode;

    @Size(max = 128, message = "会议室名称长度不能超过128")
    @Schema(description = "会议室名称", example = "A座301会议室")
    private String roomName;

    @Min(value = 1, message = "容纳人数必须为正数")
    @Schema(description = "容纳人数", example = "12")
    private Integer capacity;

    @Size(max = 255, message = "位置描述长度不能超过255")
    @Schema(description = "位置描述", example = "A座3层东侧")
    private String location;

    @Size(max = 32, message = "楼层长度不能超过32")
    @Schema(description = "楼层", example = "3F")
    private String floor;

    @Schema(description = "设备清单", example = "[\"PROJECTOR\", \"VIDEO_CONFERENCE\"]")
    private List<String> equipment;

    @Size(max = 16, message = "状态长度不能超过16")
    @Schema(description = "状态：ENABLED/DISABLED", example = "DISABLED")
    private String status;

    @Size(max = 512, message = "备注长度不能超过512")
    @Schema(description = "备注", example = "设备检修中")
    private String remark;
}
