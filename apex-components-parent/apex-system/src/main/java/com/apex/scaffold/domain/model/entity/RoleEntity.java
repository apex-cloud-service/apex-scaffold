package com.apex.scaffold.domain.model.entity;

import com.apex.scaffold.common.enums.DataScopeType;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 角色聚合根实体
 *
 * <p>
 * 表示系统中的角色定义。
 * 角色用于权限管理，可关联多个权限。
 * </p>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>角色可被启用或禁用</li>
 *   <li>角色编码唯一，用于系统内部标识</li>
 *   <li>角色可关联多个权限</li>
 * </ul>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 1.0.0
 * @see PermissionEntity
 * @see UserRoleEntity
 * @see BaseEntity
 */
@Getter
@lombok.Setter
@lombok.NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class RoleEntity extends BaseEntity {

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;

    /**
     * 角色名称
     *
     * <p>
     * 角色的显示名称。
     * 用于管理界面展示。
     * </p>
     */
    private String name;

    /**
     * 角色编码
     *
     * <p>
     * 角色的系统标识符。
     * 必须唯一，用于代码中引用角色。
     * </p>
     *
     * <p><strong>命名规范</strong></p>
     * <p>建议使用大写下划线格式，如：ADMIN, SUPER_ADMIN, USER</p>
     */
    private String code;

    /**
     * 角色描述
     *
     * <p>
     * 角色的详细说明。
     * 用于帮助管理员理解角色用途。
     * </p>
     */
    private String description;

    /**
     * 角色状态
     *
     * <p>
     * 标识角色是否可用。
     * <ul>
     *   <li>0 - 禁用（不可使用）</li>
     *   <li>1 - 正常（可正常使用）</li>
     * </ul>
     * </p>
     *
     * @see #isEnabled()
     */
    private Integer status;

    private DataScopeType dataScope;

    /**
     * 静态工厂方法：创建角色
     *
     * @param name 角色名称
     * @param code 角色编码
     * @param description 角色描述
     * @return 新创建的角色实体
     */
    public static RoleEntity create(String name, String code, String description) {
        RoleEntity entity = new RoleEntity();
        entity.name = name;
        entity.code = code;
        entity.description = description;
        entity.status = RoleStatus.ENABLED.getCode();
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
     * 领域行为：检查角色是否启用
     *
     * @return true-启用，false-禁用
     */
    public boolean isEnabled() {
        return status != null && status.equals(RoleStatus.ENABLED.getCode());
    }

    /**
     * 领域行为：更新核心信息
     *
     * <p>
     * 更新角色的名称和描述。
     * </p>
     *
     * @param name 新的角色名称
     * @param description 新的角色描述
     */
    public void updateInfo(String name, String description) {
        this.name = name;
        this.description = description;
        touch();
    }

    /**
     * 领域行为：启用角色
     *
     * <p>
     * 将角色状态设置为正常。
     * </p>
     */
    public void enable() {
        this.status = RoleStatus.ENABLED.getCode();
        touch();
    }

    /**
     * 领域行为：禁用角色
     *
     * <p>
     * 将角色状态设置为禁用。
     * 禁用后，拥有该角色的用户将失去相关权限。
     * </p>
     */
    public void disable() {
        this.status = RoleStatus.DISABLED.getCode();
        touch();
    }

    /**
     * 角色状态枚举
     */
    @Getter
    @lombok.RequiredArgsConstructor
    private enum RoleStatus {
        /** 禁用 */
        DISABLED(0),
        /** 正常 */
        ENABLED(1);

        private final Integer code;
    }
}
