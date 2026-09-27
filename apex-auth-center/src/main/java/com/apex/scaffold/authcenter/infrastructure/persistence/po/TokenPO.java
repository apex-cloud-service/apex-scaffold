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
@TableName("auth_token")
public class TokenPO {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String tenantId;
    private Long userId;
    private String tokenType;
    private String tokenValue;
    private String jti;
    private LocalDateTime expiresAt;
    private Integer revoked;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime revokedAt;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String revokedBy;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String revokeReason;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime lastUsedAt;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer useCount;

    private String deviceId;
    private String clientIp;
    private String userAgent;
    private LocalDateTime createdAt;
}
