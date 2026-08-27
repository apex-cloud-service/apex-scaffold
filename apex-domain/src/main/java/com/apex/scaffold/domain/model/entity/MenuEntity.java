package com.apex.scaffold.domain.model.entity;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 系统菜单聚合根实体。
 *
 * <p>表示管理后台中的菜单与按钮权限定义，是 RBAC 权限模型中
 * "菜单-权限"维度的领域对象。菜单采用父子树形结构组织，
 * 支持目录、菜单、按钮三种类型，是管理系统导航与操作权限的载体。</p>
 *
 * <h2>核心业务规则</h2>
 * <ul>
 *   <li>菜单通过 {@code parentId} 形成树形结构，顶级菜单的父 ID 为 0</li>
 *   <li>菜单类型分为目录（0）/ 菜单（1）/ 按钮（2）三级</li>
 *   <li>按钮类型菜单必须携带权限标识 {@code perms}，用于接口级权限校验</li>
 *   <li>菜单可被启用或停用，停用后对用户不可见且权限校验失败</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see BaseEntity
 */
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class MenuEntity extends BaseEntity {

    /**
     * 序列化版本号。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 顶级菜单的父 ID 常量。
     *
     * <p>父 ID 为 0 表示该菜单为根节点（顶级菜单）。</p>
     */
    public static final long ROOT_PARENT_ID = 0L;

    /**
     * 父菜单 ID。
     *
     * <p>指向父菜单的主键，{@link #ROOT_PARENT_ID} 表示顶级菜单。</p>
     */
    private Long parentId;

    /**
     * 菜单名称。
     *
     * <p>菜单的显示名称，用于管理后台导航与菜单树的展示。</p>
     */
    private String menuName;

    /**
     * 菜单类型。
     *
     * <p>取值说明：0-目录，1-菜单，2-按钮。</p>
     *
     * @see MenuType
     */
    private Integer menuType;

    /**
     * 路由地址。
     *
     * <p>前端路由路径，目录与菜单类型必填，按钮类型可为空。</p>
     */
    private String path;

    /**
     * 组件路径。
     *
     * <p>前端组件文件路径，菜单类型必填，用于路由懒加载。</p>
     */
    private String component;

    /**
     * 权限标识。
     *
     * <p>按钮/接口的权限编码，例如 {@code system:user:add}，
     * 与 {@code sys_permission.code} 语义一致，用于接口级权限校验。</p>
     */
    private String perms;

    /**
     * 菜单图标。
     *
     * <p>图标样式类名（如 Element Plus / Ant Design 图标名），目录与菜单类型使用。</p>
     */
    private String icon;

    /**
     * 显示顺序。
     *
     * <p>同级菜单的排序值，数值越小越靠前。</p>
     */
    private Integer sort;

    /**
     * 是否显示。
     *
     * <p>取值说明：0-隐藏，1-显示。隐藏的菜单不出现在导航中但路由仍可访问。</p>
     */
    private Integer visible;

    /**
     * 菜单状态。
     *
     * <p>取值说明：0-停用，1-正常。停用的菜单在导航中隐藏且权限校验失败。</p>
     */
    private Integer status;

    /** Sets parentId. For persistence/construction use only. */
    public void setParentId(Long value) {
        this.parentId = value;
    }

    /** Sets menuName. For persistence/construction use only. */
    public void setMenuName(String value) {
        this.menuName = value;
    }

    /** Sets menuType. For persistence/construction use only. */
    public void setMenuType(Integer value) {
        this.menuType = value;
    }

    /** Sets path. For persistence/construction use only. */
    public void setPath(String value) {
        this.path = value;
    }

    /** Sets component. For persistence/construction use only. */
    public void setComponent(String value) {
        this.component = value;
    }

    /** Sets perms. For persistence/construction use only. */
    public void setPerms(String value) {
        this.perms = value;
    }

    /** Sets icon. For persistence/construction use only. */
    public void setIcon(String value) {
        this.icon = value;
    }

    /** Sets sort. For persistence/construction use only. */
    public void setSort(Integer value) {
        this.sort = value;
    }

    /** Sets visible. For persistence/construction use only. */
    public void setVisible(Integer value) {
        this.visible = value;
    }

    /** Sets status. For persistence/construction use only. */
    public void setStatus(Integer value) {
        this.status = value;
    }

    /**
     * 默认构造函数。
     */
    public MenuEntity() {
        super();
    }

    /**
     * 静态工厂方法：创建菜单。
     *
     * @param parentId 父菜单 ID，顶级菜单传 {@link #ROOT_PARENT_ID}
     * @param menuName 菜单名称
     * @param menuType 菜单类型（0-目录，1-菜单，2-按钮）
     * @param path 路由地址
     * @param component 组件路径
     * @param perms 权限标识
     * @param sort 显示顺序
     * @return 新创建的菜单实体，默认启用且可见
     */
    public static MenuEntity create(
            Long parentId,
            String menuName,
            Integer menuType,
            String path,
            String component,
            String perms,
            Integer sort) {
        MenuEntity entity = new MenuEntity();
        entity.parentId = parentId == null ? ROOT_PARENT_ID : parentId;
        entity.menuName = menuName;
        entity.menuType = menuType;
        entity.path = path;
        entity.component = component;
        entity.perms = perms;
        entity.sort = sort == null ? 0 : sort;
        entity.visible = MenuVisible.SHOWN.getCode();
        entity.status = MenuState.ENABLED.getCode();
        entity.initTimestamps();
        return entity;
    }

    /**
     * 领域行为：更新菜单核心信息。
     *
     * <p>更新除父节点与状态外的全部展示与路由属性。</p>
     *
     * @param menuName 菜单名称
     * @param path 路由地址
     * @param component 组件路径
     * @param perms 权限标识
     * @param icon 菜单图标
     * @param sort 显示顺序
     */
    public void updateInfo(String menuName, String path, String component, String perms, String icon, Integer sort) {
        this.menuName = menuName;
        this.path = path;
        this.component = component;
        this.perms = perms;
        this.icon = icon;
        this.sort = sort == null ? 0 : sort;
        touch();
    }

    /**
     * 领域行为：移动菜单到新的父节点。
     *
     * @param newParentId 新的父菜单 ID
     */
    public void moveTo(Long newParentId) {
        this.parentId = newParentId == null ? ROOT_PARENT_ID : newParentId;
        touch();
    }

    /**
     * 领域行为：启用菜单。
     *
     * <p>将状态置为正常，菜单在导航中可见且权限校验通过。</p>
     */
    public void enable() {
        this.status = MenuState.ENABLED.getCode();
        touch();
    }

    /**
     * 领域行为：停用菜单。
     *
     * <p>将状态置为停用，菜单在导航中隐藏且权限校验失败。</p>
     */
    public void disable() {
        this.status = MenuState.DISABLED.getCode();
        touch();
    }

    /**
     * 领域行为：检查菜单是否已启用。
     *
     * @return true-已启用，false-已停用
     */
    public boolean isEnabled() {
        return status != null && status.equals(MenuState.ENABLED.getCode());
    }

    /**
     * 领域行为：检查菜单是否为按钮类型。
     *
     * @return true-按钮类型，false-非按钮类型
     */
    public boolean isButton() {
        return menuType != null && menuType.equals(MenuType.BUTTON.getCode());
    }

    /**
     * 领域行为：检查菜单是否为顶级菜单。
     *
     * @return true-顶级菜单，false-子菜单
     */
    public boolean isRoot() {
        return parentId == null || parentId == ROOT_PARENT_ID;
    }

    /**
     * 菜单类型枚举。
     */
    public enum MenuType {
        /** 目录：导航分组，不含具体路由页面 */
        DIRECTORY(0),
        /** 菜单：具体路由页面 */
        MENU(1),
        /** 按钮：页面内操作按钮（权限点） */
        BUTTON(2);

        private final Integer code;

        /**
         * 创建 {@code MenuType} 实例。
         *
         * @param code 枚举编码
         */
        MenuType(Integer code) {
            this.code = code;
        }

        /**
         * 获取枚举编码。
         *
         * @return 枚举编码
         */
        public Integer getCode() {
            return code;
        }
    }

    /**
     * 菜单状态枚举。
     */
    public enum MenuState {
        /** 停用 */
        DISABLED(0),
        /** 正常 */
        ENABLED(1);

        private final Integer code;

        /**
         * 创建 {@code MenuState} 实例。
         *
         * @param code 枚举编码
         */
        MenuState(Integer code) {
            this.code = code;
        }

        /**
         * 获取枚举编码。
         *
         * @return 枚举编码
         */
        public Integer getCode() {
            return code;
        }
    }

    /**
     * 菜单可见性枚举。
     */
    public enum MenuVisible {
        /** 隐藏 */
        HIDDEN(0),
        /** 显示 */
        SHOWN(1);

        private final Integer code;

        /**
         * 创建 {@code MenuVisible} 实例。
         *
         * @param code 枚举编码
         */
        MenuVisible(Integer code) {
            this.code = code;
        }

        /**
         * 获取枚举编码。
         *
         * @return 枚举编码
         */
        public Integer getCode() {
            return code;
        }
    }
}
