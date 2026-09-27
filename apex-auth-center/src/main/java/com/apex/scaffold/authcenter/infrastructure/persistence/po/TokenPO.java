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

    /** Token哈希（SHA-256），用于快速查找与一致性校验 */
    private String tokenHash;

    /** Token加密IV（Base64），与token_value配套用于解密 */
    private String tokenIv;

    /** Token家族ID，同一签发批次的ACCESS/REFRESH共享，用于轮换与重放检测 */
    private String tokenFamily;

    /** 轮换前的上一个刷新令牌JTI */
    private String previousJti;

    /** 轮换次数 */
    private Integer rotationCount;
}
