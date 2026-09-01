package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 租户响应 DTO。
 *
 * <p>系统管理-租户管理的响应数据，包含租户基础信息、状态与配额。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.0
 */
@Data
@Schema(description = "租户信息")
public class TenantDTO {

    /** 构造租户响应 DTO。 */
    public TenantDTO() {}

    @Schema(description = "租户ID")
    private Long id;

    @Schema(description = "租户编码（全局唯一）")
    private String tenantCode;

    @Schema(description = "租户名称")
    private String tenantName;

    @Schema(description = "租户管理员用户ID")
    private String adminUserId;

    @Schema(description = "套餐标识")
    private String packageCode;

    @Schema(description = "有效期至")
    private LocalDateTime expireTime;

    @Schema(description = "租户状态（ACTIVE/DISABLED/FROZEN/DEACTIVATED/ARCHIVED）")
    private String status;

    @Schema(description = "最大用户数（NULL表示不限制）")
    private Integer maxUsers;

    @Schema(description = "最大存储容量MB（NULL表示不限制）")
    private Integer maxStorageMb;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
