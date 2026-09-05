package com.apex.scaffold.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 权限请求 DTO。
 *
 * <p>用于创建或更新权限信息，包含权限名称、编码、类型、描述和状态。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionRequest {

    /**
     * 权限主键 ID。
     *
     * <p>更新时必填，创建时可为空。</p>
     */
    @Schema(description = "权限ID")
    private Long id;

    /**
     * 权限名称。
     *
     * <p>面向管理员的可读名称，不能为空。</p>
     */
    @NotBlank(message = "{param.not_blank}")
    @Schema(description = "权限名称")
    private String name;

    /**
     * 权限编码。
     *
     * <p>系统内部唯一标识，如 {@code photo:upload}，不能为空。</p>
     */
    @NotBlank(message = "{param.not_blank}")
    @Schema(description = "权限编码")
    private String code;

    /**
     * 权限描述。
     *
     * <p>对权限的补充说明，可选。</p>
     */
    @Schema(description = "权限描述")
    private String description;

    /**
     * 权限类型。
     *
     * <p>用于分类，如菜单、按钮、接口等，不能为空。</p>
     */
    @NotNull(message = "{param.not_null}")
    @Schema(description = "权限类型")
    private Integer type;

    /**
     * 权限状态。
     *
     * <p>取值：0-禁用，1-正常。</p>
     */
    @Schema(description = "权限状态（0-禁用，1-正常）")
    private Integer status;
}
