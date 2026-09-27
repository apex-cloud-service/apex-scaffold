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
@TableName("auth_session")
public class SessionPO {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String tenantId;
    private String sessionId;
    private Long userId;
    private Long tokenId;
    private String deviceType;
    private String deviceId;
    private String deviceName;

    @TableField("client_ip")
    private String ipAddress;

    private String userAgent;
    private String location;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime lastActiveAt;
    private LocalDateTime expiresAt;
    private String metadata;

    @TableLogic
    private Integer deleted;

    private Integer version;
}
