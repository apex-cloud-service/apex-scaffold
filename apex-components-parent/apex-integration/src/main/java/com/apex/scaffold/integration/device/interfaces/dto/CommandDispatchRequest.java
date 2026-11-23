package com.apex.scaffold.integration.device.interfaces.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.Map;
import lombok.Data;

/**
 * 命令下发请求（方案 M5 / 批次 E3，内部端点）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Data
@Schema(name = "CommandDispatchRequest", description = "命令下发请求；commandId 为幂等键，重复下发返回既有命令")
public class CommandDispatchRequest {

    /** 业务命令 ID（租户内唯一，幂等键；重复下发返回既有命令）。 */
    @NotBlank(message = "命令 ID 不能为空")
    @Size(max = 64, message = "命令 ID 最长 64 位")
    @Schema(
            description = "业务命令 ID，租户内唯一且作为幂等键；重复下发返回既有命令",
            example = "cmd-20261122-0001",
            maxLength = 64,
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String commandId;

    /** 命令载荷（JSON 对象）。 */
    @Schema(description = "命令载荷，结构化 JSON 对象，原样下发至设备", example = "{\"action\":\"reboot\",\"delay\":0}")
    private Map<String, Object> payload;

    /** 超时窗口（秒；空取默认 60，上限 3600）。 */
    @PositiveOrZero(message = "超时窗口不能为负")
    @Schema(
            description = "命令超时窗口（秒）；为空时取默认 60，上限 3600",
            example = "60",
            type = "integer",
            format = "int32",
            minimum = "0",
            maximum = "3600",
            defaultValue = "60")
    private Integer timeoutSeconds;
}
