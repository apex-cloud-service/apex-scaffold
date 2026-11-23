package com.apex.scaffold.integration.device.interfaces.dto;

import com.apex.scaffold.integration.device.domain.DeviceCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 命令响应（方案 M5 / 批次 E3）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Data
@Schema(name = "CommandResponse", description = "设备命令响应")
public class CommandResponse {

    @Schema(description = "业务命令 ID", example = "cmd-20261122-0001")
    private String commandId;

    @Schema(description = "设备标识", example = "dev-0001")
    private String deviceKey;

    /** 状态（PENDING/SENT/ACK/TIMEOUT）。 */
    @Schema(
            description = "命令状态",
            example = "SENT",
            allowableValues = {"PENDING", "SENT", "ACK", "TIMEOUT"})
    private String status;

    @Schema(description = "命令载荷，结构化 JSON 对象", example = "{\"action\":\"reboot\",\"delay\":0}")
    private Object payload;

    @Schema(description = "下发时间", example = "2026-11-22T10:15:30", type = "string", format = "date-time")
    private LocalDateTime sentAt;

    @Schema(description = "设备确认时间；未确认时为 null", example = "2026-11-22T10:15:35", type = "string", format = "date-time")
    private LocalDateTime ackedAt;

    @Schema(
            description = "超时时间点；未设置超时窗口时为 null",
            example = "2026-11-22T10:16:30",
            type = "string",
            format = "date-time")
    private LocalDateTime timeoutAt;

    /** 是否重复下发（命中幂等键返回既有命令）。 */
    @Schema(description = "是否重复下发；命中幂等键而返回既有命令时为 true", example = "false")
    private boolean duplicate;

    /**
     * 从命令实体映射。
     *
     * @param command   命令实体
     * @param duplicate 是否重复下发
     * @return 响应
     */
    public static CommandResponse from(DeviceCommand command, boolean duplicate) {
        CommandResponse response = new CommandResponse();
        response.setCommandId(command.getCommandId());
        response.setDeviceKey(command.getDeviceKey());
        response.setStatus(command.getStatus());
        response.setPayload(command.getPayload());
        response.setSentAt(command.getSentAt());
        response.setAckedAt(command.getAckedAt());
        response.setTimeoutAt(command.getTimeoutAt());
        response.setDuplicate(duplicate);
        return response;
    }
}
