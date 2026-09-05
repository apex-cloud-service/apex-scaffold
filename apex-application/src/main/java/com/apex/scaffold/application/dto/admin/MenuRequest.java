package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 菜单创建/更新请求体。
 *
 * <p>系统管理-菜单管理的统一请求 DTO，覆盖目录、菜单、按钮三种类型的
 * 创建与编辑场景。字段约束遵循菜单领域规则：菜单名称与类型必填，
 * 类型取值 0-目录、1-菜单、2-按钮。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "菜单创建/更新请求")
public class MenuRequest {

    /**
     * 父菜单 ID。
     *
     * <p>缺省为 0（顶级菜单），创建子菜单时传入父菜单主键。</p>
     */
    @Schema(description = "父菜单ID，0表示顶级菜单", example = "0")
    private Long parentId;

    /**
     * 菜单名称。
     */
    @Schema(description = "菜单名称", example = "用户管理")
    @NotBlank(message = "{menu.name_not_blank}")
    @Size(max = 50, message = "{menu.name_too_long}")
    private String menuName;

    /**
     * 菜单类型（0-目录，1-菜单，2-按钮）。
     */
    @Schema(description = "菜单类型：0-目录，1-菜单，2-按钮", example = "1")
    @NotNull(message = "{menu.type_not_null}")
    @Min(value = 0, message = "{menu.type_invalid}")
    @Max(value = 2, message = "{menu.type_invalid}")
    private Integer menuType;

    /**
     * 路由地址。
     *
     * <p>目录与菜单类型必填，按钮类型可为空。</p>
     */
    @Schema(description = "路由地址", example = "system/user")
    @Size(max = 200, message = "{menu.path_too_long}")
    private String path;

    /**
     * 组件路径。
     *
     * <p>菜单类型必填，用于前端路由懒加载。</p>
     */
    @Schema(description = "组件路径", example = "system/user/index")
    @Size(max = 255, message = "{menu.component_too_long}")
    private String component;

    /**
     * 权限标识。
     *
     * <p>按钮类型菜单必填，例如 {@code system:user:add}。</p>
     */
    @Schema(description = "权限标识", example = "system:user:add")
    @Size(max = 100, message = "{menu.perms_too_long}")
    private String perms;

    /**
     * 菜单图标。
     */
    @Schema(description = "菜单图标", example = "User")
    @Size(max = 100, message = "{menu.icon_too_long}")
    private String icon;

    /**
     * 显示顺序。
     *
     * <p>同级菜单排序值，数值越小越靠前，缺省为 0。</p>
     */
    @Schema(description = "显示顺序", example = "1")
    private Integer sort;

    /**
     * 是否显示（0-隐藏，1-显示）。
     */
    @Schema(description = "是否显示：0-隐藏，1-显示", example = "1")
    private Integer visible;

    /**
     * 菜单状态（0-停用，1-正常）。
     */
    @Schema(description = "菜单状态：0-停用，1-正常", example = "1")
    private Integer status;
}
