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
@TableName("auth_user")
public class UserPO {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String tenantId;
    private String username;
    private String email;
    private String phone;
    private String passwordHash;
    private String openId;
    private Integer status;
    private LocalDateTime lastLoginAt;
    private String lastLoginIp;
    private Integer failedLoginAttempts;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime lockedUntil;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
