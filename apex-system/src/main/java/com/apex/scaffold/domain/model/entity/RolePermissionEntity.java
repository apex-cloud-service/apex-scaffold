package com.apex.scaffold.domain.model.entity;

import java.io.Serial;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class RolePermissionEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 角色 ID。 */
    private Long roleId;

    /** 权限 ID。 */
    private Long permissionId;
}
