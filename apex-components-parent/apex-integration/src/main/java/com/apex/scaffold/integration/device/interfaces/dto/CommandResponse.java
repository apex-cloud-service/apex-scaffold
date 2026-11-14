package com.apex.scaffold.integration.device.interfaces.dto;

import com.apex.scaffold.integration.device.domain.DeviceCommand;
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
public class CommandResponse {

    private String commandId;

    private String deviceKey;

    /** 状态（PENDING/SENT/ACK/TIMEOUT）。 */
    private String status;

    private Object payload;

    private LocalDateTime sentAt;

    private LocalDateTime ackedAt;

    private LocalDateTime timeoutAt;

    /** 是否重复下发（命中幂等键返回既有命令）。 */
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
