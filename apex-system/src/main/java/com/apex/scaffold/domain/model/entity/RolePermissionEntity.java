package com.apex.scaffold.domain.model.entity;

import java.io.Serial;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 角色权限关联持久化对象
 *
 * <p>领域实体
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class RolePermissionEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 创建角色权限关联实体对象。 */
    public RolePermissionEntity() {}

    /** 角色 ID。 */
    private Long roleId;

    /** 权限 ID。 */
    private Long permissionId;

    /** Sets roleId. For persistence/construction use only. */
    public void setRoleId(Long value) {
        this.roleId = value;
    }

    /** Sets permissionId. For persistence/construction use only. */
    public void setPermissionId(Long value) {
        this.permissionId = value;
    }
}
