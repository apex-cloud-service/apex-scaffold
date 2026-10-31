package com.apex.scaffold.application.dto.admin;

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
public class TenantPackageRequest {

    /** 套餐编码（全局唯一） */
    @NotBlank(message = "套餐编码不能为空")
    private String packageCode;

    /** 套餐名称 */
    @NotBlank(message = "套餐名称不能为空")
    private String packageName;

    /** 可用菜单 ID 集合（空 = 全量） */
    private Set<Long> menuIds;

    /** 可用权限码集合（空 = 全量） */
    private Set<String> permissionCodes;

    /** 能力订阅集合（能力运营 D4） */
    private Set<String> features;

    /** 状态（0-停用，1-正常） */
    private Integer status;

    /** 备注 */
    private String remark;
}
