package com.apex.scaffold.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 角色请求 DTO。
 *
 * <p>用于创建或更新角色信息，包含角色名称、编码、描述和状态。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
public class RoleRequest {

    /**
     * 角色主键 ID。
     *
     * <p>更新时必填，创建时可为空。</p>
     */
    @Schema(description = "角色ID")
    private Long id;

    /**
     * 角色名称。
     *
     * <p>面向管理员的可读名称，如"超级管理员"，不能为空。</p>
     */
    @NotBlank(message = "{param.not_blank}")
    @Schema(description = "角色名称")
    private String name;

    /**
     * 角色编码。
     *
     * <p>系统内部唯一标识，如 {@code ROLE_ADMIN}，不能为空。</p>
     */
    @NotBlank(message = "{param.not_blank}")
    @Schema(description = "角色编码")
    private String code;

    /**
     * 角色描述。
     *
     * <p>对角色的补充说明，可选。</p>
     */
    @Schema(description = "角色描述")
    private String description;

    /**
     * 角色状态。
     *
     * <p>取值：0-禁用，1-正常。</p>
     */
    @Schema(description = "角色状态（0-禁用，1-正常）")
    private Integer status;
}
