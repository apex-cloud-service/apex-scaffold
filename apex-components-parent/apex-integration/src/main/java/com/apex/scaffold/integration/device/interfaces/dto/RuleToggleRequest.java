package com.apex.scaffold.integration.device.interfaces.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 规则启停请求（方案 M5 / 批次 E3）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Data
@Schema(name = "RuleToggleRequest", description = "设备规则启停请求")
public class RuleToggleRequest {

    /** 是否启用。 */
    @NotBlank(message = "enabled 不能为空")
    @Schema(
            description = "是否启用，字符串形式的布尔值",
            example = "true",
            allowableValues = {"true", "false"},
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String enabled;
}
