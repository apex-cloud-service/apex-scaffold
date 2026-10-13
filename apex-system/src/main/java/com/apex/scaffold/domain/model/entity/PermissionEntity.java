package com.apex.scaffold.domain.model.entity;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 权限聚合根实体
 *
 * <p>
 * 表示系统中的权限定义。
 * 权限是系统中最细粒度的安全控制单位。
 * </p>
 *
 * <h2>核心业务规则</h2>
 * <ul>
 *   <li>权限可被启用或禁用</li>
 *   <li>权限编码唯一，用于系统内部标识</li>
 *   <li>权限可关联到多个角色</li>
 * </ul>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 1.0.0
 * @see RoleEntity
 * @see BaseEntity
 */
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class PermissionEntity extends BaseEntity {

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;

    /**
     * 权限名称
     *
     * <p>
     * 权限的显示名称。
     * </p>
     */
    private String name;

    /**
     * 权限编码
     *
     * <p>
     * 权限的系统标识符。
     * 必须唯一，用于代码中引用权限。
     * </p>
     *
     * <h3>命名规范</h3>
     * <p>建议使用资源:操作格式，如：user:read, user:write</p>
     */
    private String code;

    /**
     * 权限描述
     *
     * <p>
     * 权限的详细说明。
     * </p>
     */
    private String description;

    /**
     * 权限类型
     *
     * <p>
     * 标识权限的类型。
     * </p>
     *
     * @see PermissionType
     */
    private Integer type;

    /**
     * 权限状态
     *
     * <p>
     * 标识权限是否可用。
     * <ul>
     *   <li>0 - 禁用</li>
     *   <li>1 - 正常</li>
     * </ul>
     * </p>
     */
    private Integer status;

    /** Sets name. For persistence/construction use only. */
    public void setName(String value) {
        this.name = value;
    }

    /** Sets code. For persistence/construction use only. */
    public void setCode(String value) {
        this.code = value;
    }

    /** Sets description. For persistence/construction use only. */
    public void setDescription(String value) {
        this.description = value;
    }

    /** Sets type. For persistence/construction use only. */
    public void setType(Integer value) {
        this.type = value;
    }

    /** Sets status. For persistence/construction use only. */
    public void setStatus(Integer value) {
        this.status = value;
    }

    /**
     * 默认构造函数
     */
    public PermissionEntity() {
        super();
    }

    /**
     * 静态工厂方法：创建权限
     *
     * @param name 权限名称
     * @param code 权限编码
     * @param description 权限描述
     * @param type 权限类型
     * @return 新创建的权限实体
     */
    public static PermissionEntity create(String name, String code, String description, Integer type) {
        PermissionEntity entity = new PermissionEntity();
        entity.name = name;
        entity.code = code;
        entity.description = description;
        entity.type = type;
        entity.status = PermissionStatus.ENABLED.getCode();
        entity.setTimestamps();
        return entity;
    }

    /**
     * 设置时间戳
     */
    private void setTimestamps() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        this.setCreateTime(now);
        this.setUpdateTime(now);
        this.setDeleted(LogicalDeletion.NOT_DELETED.getCode());
    }

    /**
     * 领域行为：检查权限是否启用
     *
     * @return true-启用，false-禁用
     */
    public boolean isEnabled() {
        return status != null && status.equals(PermissionStatus.ENABLED.getCode());
    }

    /**
     * 领域行为：更新核心信息
     *
     * @param name 新的权限名称
     * @param description 新的权限描述
     * @param status 新的状态
     */
    public void updateInfo(String name, String description, Integer status) {
        this.name = name;
        this.description = description;
        this.status = status;
        touch();
    }

    /**
     * 领域行为：启用权限
     */
    public void enable() {
        this.status = PermissionStatus.ENABLED.getCode();
        touch();
    }

    /**
     * 领域行为：禁用权限
     */
    public void disable() {
        this.status = PermissionStatus.DISABLED.getCode();
        touch();
    }

    /**
     * 权限类型枚举
     */
    public enum PermissionType {
        /** 菜单权限 */
        MENU(1),
        /** 按钮权限 */
        BUTTON(2),
        /** API权限 */
        API(3);

        private final Integer code;

        /**
         * 创建 {@code PermissionType} 实例。
         *
         * @param code 枚举编码
         */
        PermissionType(Integer code) {
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

        /**
         * 根据枚举编码解析权限类型。
         *
         * @param code 权限类型编码
         * @return 匹配的权限类型；编码为空或未匹配时返回 {@code null}
         */
        public static PermissionType fromCode(Integer code) {
            if (code == null) {
                return null;
            }
            for (PermissionType type : values()) {
                if (type.code.equals(code)) {
                    return type;
                }
            }
            return null;
        }
    }

    /**
     * 权限状态枚举
     */
    private enum PermissionStatus {
        /** 禁用 */
        DISABLED(0),
        /** 正常 */
        ENABLED(1);

        private final Integer code;

        /**
         * 创建 {@code PermissionStatus} 实例。
         *
         * @param code 枚举编码
         */
        PermissionStatus(Integer code) {
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
