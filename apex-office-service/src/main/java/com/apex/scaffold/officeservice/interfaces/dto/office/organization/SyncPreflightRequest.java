package com.apex.scaffold.officeservice.interfaces.dto.office.organization;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 组织同步预检请求 DTO
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Data
@Schema(description = "组织同步预检请求")
public class SyncPreflightRequest {

    @NotBlank(message = "平台类型不能为空")
    @Schema(description = "平台类型（FEISHU/DINGTALK/WECOM）", example = "FEISHU", required = true)
    private String platform;
}
