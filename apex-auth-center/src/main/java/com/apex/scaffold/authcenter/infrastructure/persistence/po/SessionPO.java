package com.apex.scaffold.authcenter.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.*;

/**
 * 认证中心会话数据表 {@code auth_session} 的持久化映射。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("auth_session")
public class SessionPO {
    /** 主键，由 MyBatis-Plus 分配。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属认证中心租户标识。 */
    private String tenantId;

    /** 会话唯一标识。 */
    private String sessionId;

    /** 关联的用户标识。 */
    private Long userId;

    /** 关联的令牌标识。 */
    private Long tokenId;

    /** 设备类型（web/mobile/desktop/server）。 */
    private String deviceType;

    /** 设备唯一标识。 */
    private String deviceId;

    /** 设备名称。 */
    private String deviceName;

    /** 客户端 IP 地址。 */
    @TableField("client_ip")
    private String ipAddress;

    /** 客户端 User-Agent。 */
    private String userAgent;

    /** 地理位置信息。 */
    private String location;

    /** 会话状态（active/expired/revoked）。 */
    private String status;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 最后活跃时间。 */
    private LocalDateTime lastActiveAt;

    /** 过期时间。 */
    private LocalDateTime expiresAt;

    /** 会话元数据（JSON 格式）。 */
    private String metadata;

    /** 软删除标记。 */
    @TableLogic
    private Integer deleted;

    /** 乐观锁版本号。 */
    private Integer version;
}
