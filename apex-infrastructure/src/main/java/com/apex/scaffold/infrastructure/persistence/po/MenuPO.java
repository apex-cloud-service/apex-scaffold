package com.apex.scaffold.infrastructure.persistence.po;

import com.apex.scaffold.common.persistence.po.BasePO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 系统菜单持久化对象。
 *
 * <p>对应数据库中的 {@code sys_menu} 表，存储管理后台菜单树与按钮权限定义。
 * 菜单通过 {@code parentId} 组织树形结构，支持目录/菜单/按钮三种类型。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName("sys_menu")
public class MenuPO extends BasePO {

    /**
     * 构造系统菜单持久化对象。
     */

    /**
     * 父菜单 ID。
     *
     * <p>值为 0 表示顶级菜单。</p>
     */
    private Long parentId;

    /**
     * 菜单名称。
     */
    private String menuName;

    /**
     * 菜单类型（0-目录，1-菜单，2-按钮）。
     */
    private Integer menuType;

    /**
     * 路由地址。
     */
    private String path;

    /**
     * 组件路径。
     */
    private String component;

    /**
     * 权限标识。
     */
    private String perms;

    /**
     * 菜单图标。
     */
    private String icon;

    /**
     * 显示顺序。
     */
    private Integer sort;

    /**
     * 是否显示（0-隐藏，1-显示）。
     */
    private Integer visible;

    /**
     * 菜单状态（0-停用，1-正常）。
     */
    private Integer status;
}
