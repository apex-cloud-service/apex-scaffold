package com.apex.scaffold.authcenter.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.*;

/**
 * 认证数据持久化映射。
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("auth_role_permission")
public class RolePermissionPO {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String tenantId;
    private Long roleId;
    private Long permissionId;
    private LocalDateTime createdAt;
}
