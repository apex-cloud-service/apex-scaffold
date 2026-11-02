package com.apex.scaffold.integration.device.interfaces.dto;

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
public class CommandDispatchRequest {

    /** 业务命令 ID（租户内唯一，幂等键；重复下发返回既有命令）。 */
    @NotBlank(message = "命令 ID 不能为空")
    @Size(max = 64, message = "命令 ID 最长 64 位")
    private String commandId;

    /** 命令载荷（JSON 对象）。 */
    private Map<String, Object> payload;

    /** 超时窗口（秒；空取默认 60，上限 3600）。 */
    @PositiveOrZero(message = "超时窗口不能为负")
    private Integer timeoutSeconds;
}
