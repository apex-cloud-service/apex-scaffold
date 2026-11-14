package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;
import lombok.Data;

/**
 * 租户套餐创建/更新请求（方案批次 B6）。
 *
 * <p>菜单与权限码的空集合表示对应维度全量；能力集合的空集合表示未订阅任何能力，
 * 不表示全量。当前管理请求不提供 {@code unbounded} 开关，需要授予全部能力时必须
 * 显式列出能力目录中的编码。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Data
@Schema(name = "TenantPackageRequest", description = "租户套餐创建/更新请求；菜单/权限空集合表示全量，能力空集合表示未订阅任何能力")
public class TenantPackageRequest {

    /** 套餐编码（全局唯一） */
    @Schema(description = "套餐编码（全局唯一，创建后不可变）", example = "standard")
    @NotBlank(message = "套餐编码不能为空")
    private String packageCode;

    /** 套餐名称 */
    @Schema(description = "套餐名称", example = "标准版")
    @NotBlank(message = "套餐名称不能为空")
    private String packageName;

    /** 可用菜单 ID 集合（空 = 全量） */
    @Schema(description = "可用菜单 ID 集合（空集合 = 全量菜单）", example = "[1, 2, 3]")
    private Set<Long> menuIds;

    /** 可用权限码集合（空 = 全量） */
    @Schema(description = "可用权限码集合（空集合 = 全量权限）", example = "[\"system:user:read\"]")
    private Set<String> permissionCodes;

    /**
     * 能力订阅集合（能力运营 D4）。
     *
     * <p>空集合表示未订阅任何能力；需要全部能力时须显式列出能力目录编码，
     * 避免漏配被静默解释为全量授权。
     */
    @Schema(description = "订阅的能力编码集合（空集合 = 未订阅任何能力；全量须显式列出编码）", example = "[\"file-storage\"]")
    private Set<String> features;

    /** 状态（0-停用，1-正常） */
    @Schema(description = "状态：0-停用，1-正常", example = "1")
    private Integer status;

    /** 备注 */
    @Schema(description = "备注", example = "标准版套餐")
    private String remark;
}
