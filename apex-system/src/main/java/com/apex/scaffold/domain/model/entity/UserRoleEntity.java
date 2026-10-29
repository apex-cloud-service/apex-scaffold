package com.apex.scaffold.domain.model.entity;

import java.io.Serial;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class UserRoleEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 租户 ID。 */
    private String tenantId;

    /** 用户 ID。 */
    private Long userId;

    /** 角色 ID。 */
    private Long roleId;
}
