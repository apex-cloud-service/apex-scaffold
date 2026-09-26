package com.apex.scaffold.interfaces.dto.office;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 办公平台身份绑定响应。
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "办公平台身份绑定响应")
public class OfficeBindingResponse {
    @Schema(description = "绑定主键", example = "1001")
    private Long id;

    @Schema(description = "租户标识", example = "tenant-a")
    private String tenantId;

    @Schema(description = "用户主键", example = "2001")
    private Long userId;

    @Schema(description = "平台类型", example = "FEISHU")
    private String platform;

    @Schema(description = "外部用户标识", example = "ou_abc123")
    private String externalUserId;

    @Schema(
            description = "审批状态",
            example = "PENDING",
            allowableValues = {"PENDING", "APPROVED", "REJECTED", "REVOKED"})
    private String status;

    @Schema(description = "是否启用", example = "true")
    private Boolean enabled;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;
}
