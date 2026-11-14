package com.apex.scaffold.auth.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.*;

/**
 * 认证中心角色-权限关联表 {@code auth_role_permission} 的持久化映射。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("auth_role_permission")
public class RolePermissionPO {
    /** 主键，由 MyBatis-Plus 分配。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属认证中心租户标识。 */
    private String tenantId;

    /** 关联的角色标识。 */
    private Long roleId;

    /** 关联的权限标识。 */
    private Long permissionId;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}
