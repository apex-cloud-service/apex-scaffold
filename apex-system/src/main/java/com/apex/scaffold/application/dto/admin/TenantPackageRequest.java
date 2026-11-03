package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;
import lombok.Data;

/**
 * 租户套餐创建/更新请求（方案批次 B6）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Data
@Schema(name = "TenantPackageRequest", description = "租户套餐创建/更新请求；菜单/权限/能力集合为空表示全量")
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

    /** 能力订阅集合（能力运营 D4） */
    @Schema(description = "订阅的能力编码集合（空集合 = 全量能力）", example = "[\"file-storage\"]")
    private Set<String> features;

    /** 状态（0-停用，1-正常） */
    @Schema(description = "状态：0-停用，1-正常", example = "1")
    private Integer status;

    /** 备注 */
    @Schema(description = "备注", example = "标准版套餐")
    private String remark;
}
