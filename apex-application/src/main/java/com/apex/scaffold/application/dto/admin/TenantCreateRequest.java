package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 租户创建请求体。
 *
 * <p>系统管理-租户管理的创建请求 DTO。租户编码全局唯一，创建后不可变更，
 * 作为 {@code TenantContext} 的租户 ID 贯穿全链路。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.0
 */
@Data
@Schema(description = "租户创建请求")
public class TenantCreateRequest {

    /** 构造租户创建请求体。 */
    public TenantCreateRequest() {}

    @Schema(description = "租户编码（全局唯一，创建后不可变更）", example = "T001")
    @NotBlank(message = "{tenant.code_not_blank}")
    @Size(max = 64, message = "{tenant.code_too_long}")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "{tenant.code_invalid}")
    private String tenantCode;

    @Schema(description = "租户名称", example = "Acme Corp")
    @NotBlank(message = "{tenant.name_not_blank}")
    @Size(max = 128, message = "{tenant.name_too_long}")
    private String tenantName;

    @Schema(description = "租户管理员用户ID")
    @Size(max = 64, message = "{tenant.admin_too_long}")
    private String adminUserId;

    @Schema(description = "套餐标识")
    @Size(max = 64, message = "{tenant.package_too_long}")
    private String packageCode;

    @Schema(description = "最大用户数（NULL表示不限制）")
    @Positive(message = "{tenant.max_users_positive}")
    private Integer maxUsers;

    @Schema(description = "最大存储容量MB（NULL表示不限制）")
    @Positive(message = "{tenant.max_storage_positive}")
    private Integer maxStorageMb;

    @Schema(description = "备注")
    @Size(max = 500, message = "{tenant.remark_too_long}")
    private String remark;
}
