package com.apex.scaffold.application.dto.admin;

import com.apex.scaffold.application.dto.PageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 租户分页查询请求 DTO。
 *
 * <p>平台管理员的租户目录查询，继承 {@link PageRequest} 的分页参数，
 * 支持按租户编码、名称模糊匹配与状态精确匹配。</p>
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@Schema(description = "租户分页查询请求")
public class TenantPageQuery extends PageRequest {

    @Schema(description = "租户编码（支持模糊查询）", example = "T001")
    private String tenantCode;

    @Schema(description = "租户名称（支持模糊查询）", example = "Acme")
    private String tenantName;

    @Schema(description = "租户状态：ACTIVE/DISABLED/FROZEN/DEACTIVATED/ARCHIVED", example = "ACTIVE")
    private String status;
}
