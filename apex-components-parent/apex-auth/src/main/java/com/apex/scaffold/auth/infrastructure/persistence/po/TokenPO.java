package com.apex.scaffold.auth.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.*;

/**
 * 认证中心令牌数据表 {@code auth_token} 的持久化映射。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("auth_token")
public class TokenPO {
    /** 主键，由 MyBatis-Plus 分配。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属认证中心租户标识。 */
    private String tenantId;

    /** 关联的用户标识。 */
    private Long userId;

    /** 令牌类型（ACCESS/REFRESH）。 */
    private String tokenType;

    /** 令牌密文值（加密存储）。 */
    private String tokenValue;

    /** JWT ID，用于令牌唯一标识与重放检测。 */
    private String jti;

    /** 令牌过期时间。 */
    private LocalDateTime expiresAt;

    /** 是否已撤销（0=未撤销 1=已撤销）。 */
    private Integer revoked;

    /** 撤销时间。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime revokedAt;

    /** 撤销操作者标识。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String revokedBy;

    /** 撤销原因。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String revokeReason;

    /** 最后使用时间。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime lastUsedAt;

    /** 使用次数统计。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer useCount;

    /** 设备 ID。 */
    private String deviceId;

    /** 客户端 IP 地址。 */
    private String clientIp;

    /** 客户端 User-Agent。 */
    private String userAgent;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** Token 哈希（SHA-256），用于快速查找与一致性校验。 */
    private String tokenHash;

    /** Token 加密 IV（Base64），与 token_value 配套用于解密。 */
    private String tokenIv;

    /** Token 家族 ID，同一签发批次的 ACCESS/REFRESH 共享，用于轮换与重放检测。 */
    private String tokenFamily;

    /** 轮换前的上一个刷新令牌 JTI。 */
    private String previousJti;

    /** 轮换次数。 */
    private Integer rotationCount;
}
