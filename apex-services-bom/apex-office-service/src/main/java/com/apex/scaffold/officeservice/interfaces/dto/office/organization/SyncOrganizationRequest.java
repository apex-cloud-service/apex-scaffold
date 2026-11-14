package com.apex.scaffold.officeservice.interfaces.dto.office.organization;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 同步组织请求 DTO
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Data
@Schema(description = "同步组织请求")
public class SyncOrganizationRequest {

    @NotBlank(message = "平台类型不能为空")
    @Schema(description = "平台类型（FEISHU/DINGTALK/WECOM）", example = "FEISHU", required = true)
    private String platform;

    @NotBlank(message = "同步类型不能为空")
    @Schema(description = "同步类型（DEPARTMENT-部门/MEMBER-成员）", example = "DEPARTMENT", required = true)
    private String syncType;

    @NotNull(message = "是否全量同步不能为空")
    @Schema(description = "是否全量同步（true-从头开始，false-增量同步）", example = "false", required = true)
    private Boolean fullSync;
}
