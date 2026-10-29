package com.apex.scaffold.officeservice.interfaces.dto.office.meetingroom;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建会议室请求
 *
 * <p>用于新增租户内的会议室资源。会议室编码与名称在租户内必须唯一，
 * 状态未显式指定时按启用处理。
 *
 * @author Apex Scaffold Team
 * @since 1.4.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "创建会议室请求")
public class CreateMeetingRoomRequest {

    @NotBlank(message = "会议室编码不能为空")
    @Size(max = 64, message = "会议室编码长度不能超过64")
    @Schema(description = "会议室编码（租户内唯一）", example = "A301", requiredMode = Schema.RequiredMode.REQUIRED)
    private String roomCode;

    @NotBlank(message = "会议室名称不能为空")
    @Size(max = 128, message = "会议室名称长度不能超过128")
    @Schema(description = "会议室名称", example = "A座301会议室", requiredMode = Schema.RequiredMode.REQUIRED)
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
    @Schema(description = "状态：ENABLED/DISABLED，缺省为 ENABLED", example = "ENABLED")
    private String status;

    @Size(max = 512, message = "备注长度不能超过512")
    @Schema(description = "备注", example = "靠近茶水间")
    private String remark;
}
