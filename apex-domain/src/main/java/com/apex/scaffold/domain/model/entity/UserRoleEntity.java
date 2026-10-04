package com.apex.scaffold.domain.model.entity;

import java.io.Serial;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 用户角色关联持久化对象
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
public class UserRoleEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 创建用户角色关联实体对象。 */
    public UserRoleEntity() {}

    /** 租户 ID。 */
    private String tenantId;

    /** 用户 ID。 */
    private Long userId;

    /** 角色 ID。 */
    private Long roleId;

    /** Sets tenantId. For persistence/construction use only. */
    public void setTenantId(String value) {
        this.tenantId = value;
    }

    /** Sets userId. For persistence/construction use only. */
    public void setUserId(Long value) {
        this.userId = value;
    }

    /** Sets roleId. For persistence/construction use only. */
    public void setRoleId(Long value) {
        this.roleId = value;
    }
}
