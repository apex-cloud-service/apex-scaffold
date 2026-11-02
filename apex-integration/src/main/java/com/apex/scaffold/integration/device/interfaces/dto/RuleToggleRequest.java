package com.apex.scaffold.integration.device.interfaces.dto;

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
public class RuleToggleRequest {

    /** 是否启用。 */
    @NotBlank(message = "enabled 不能为空")
    private String enabled;
}
