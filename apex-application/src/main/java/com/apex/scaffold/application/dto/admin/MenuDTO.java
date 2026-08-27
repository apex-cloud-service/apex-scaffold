package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 菜单树节点响应 DTO。
 *
 * <p>系统管理-菜单管理的响应对象，以树形结构承载菜单数据。
 * 每个节点包含自身属性与 {@code children} 子节点列表，供前端
 * 渲染菜单导航树与权限配置树使用。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Schema(description = "菜单树节点")
public class MenuDTO {

    /**
     * 构造菜单树节点。
     */
    public MenuDTO() {}

    /**
     * 菜单 ID。
     */
    @Schema(description = "菜单ID")
    private Long id;

    /**
     * 父菜单 ID。
     */
    @Schema(description = "父菜单ID")
    private Long parentId;

    /**
     * 菜单名称。
     */
    @Schema(description = "菜单名称")
    private String menuName;

    /**
     * 菜单类型（0-目录，1-菜单，2-按钮）。
     */
    @Schema(description = "菜单类型：0-目录，1-菜单，2-按钮")
    private Integer menuType;

    /**
     * 路由地址。
     */
    @Schema(description = "路由地址")
    private String path;

    /**
     * 组件路径。
     */
    @Schema(description = "组件路径")
    private String component;

    /**
     * 权限标识。
     */
    @Schema(description = "权限标识")
    private String perms;

    /**
     * 菜单图标。
     */
    @Schema(description = "菜单图标")
    private String icon;

    /**
     * 显示顺序。
     */
    @Schema(description = "显示顺序")
    private Integer sort;

    /**
     * 是否显示（0-隐藏，1-显示）。
     */
    @Schema(description = "是否显示：0-隐藏，1-显示")
    private Integer visible;

    /**
     * 菜单状态（0-停用，1-正常）。
     */
    @Schema(description = "菜单状态：0-停用，1-正常")
    private Integer status;

    /**
     * 创建时间。
     */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /**
     * 子菜单列表。
     *
     * <p>按显示顺序升序排列，无子节点时为空列表。</p>
     */
    @Schema(description = "子菜单列表")
    private List<MenuDTO> children = new ArrayList<>();

    /**
     * 添加子节点。
     *
     * @param child 子菜单节点
     */
    public void addChild(MenuDTO child) {
        this.children.add(child);
    }
}
