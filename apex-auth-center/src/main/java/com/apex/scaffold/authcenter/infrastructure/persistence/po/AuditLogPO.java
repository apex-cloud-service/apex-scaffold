package com.apex.scaffold.authcenter.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.*;

/**
 * 认证中心审计日志表 {@code auth_audit_log} 的持久化映射。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("auth_audit_log")
public class AuditLogPO {
    /** 主键，由 MyBatis-Plus 分配。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属认证中心租户标识。 */
    private String tenantId;

    /** 操作用户标识。 */
    private Long userId;

    /** 操作用户名。 */
    private String username;

    /** 事件类型（LOGIN/LOGOUT/MFA_UNLOCK 等）。 */
    private String eventType;

    /** 事件状态（SUCCESS/FAILURE）。 */
    private String eventStatus;

    /** 资源类型（USER/ROLE/PERMISSION 等）。 */
    private String resourceType;

    /** 资源标识。 */
    private String resourceId;

    /** 操作动作（CREATE/UPDATE/DELETE/QUERY 等）。 */
    private String action;

    /** 客户端 IP 地址。 */
    @TableField("client_ip")
    private String ipAddress;

    /** 客户端 User-Agent。 */
    private String userAgent;

    /** 地理位置信息。 */
    private String location;

    /** 事件详情元数据（JSON 格式）。 */
    @TableField("metadata")
    private String details;

    /** 失败原因描述。 */
    @TableField("error_message")
    private String failureReason;

    /** 风险等级（low/medium/high/critical）。 */
    private String riskLevel;

    /** 事件发生时间。 */
    private LocalDateTime eventTime;

    /** 记录创建时间。 */
    private LocalDateTime createdAt;
}
