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
@TableName("auth_audit_log")
public class AuditLogPO {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String tenantId;
    private Long userId;
    private String username;
    private String eventType;
    private String eventStatus;
    private String resourceType;
    private String resourceId;
    private String action;

    @TableField("client_ip")
    private String ipAddress;

    private String userAgent;
    private String location;

    @TableField("metadata")
    private String details;

    @TableField("error_message")
    private String failureReason;

    private String riskLevel;
    private LocalDateTime eventTime;
    private LocalDateTime createdAt;
}
