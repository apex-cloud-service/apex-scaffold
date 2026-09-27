package com.apex.scaffold.authcenter.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审计日志DTO
 *
 * <p>用于API层的审计日志数据传输。
 *
 * @author Apex Scaffold Team
 * @since 1.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "审计日志DTO")
public class AuditLogDTO {

    @Schema(description = "日志ID", example = "1")
    private Long id;

    @Schema(description = "租户ID", example = "tenant-001")
    private String tenantId;

    @Schema(description = "用户ID", example = "1001")
    private Long userId;

    @Schema(description = "用户名", example = "admin")
    private String username;

    @Schema(
            description = "事件类型",
            example = "LOGIN",
            allowableValues = {
                "LOGIN",
                "LOGOUT",
                "TOKEN_ISSUED",
                "TOKEN_REFRESHED",
                "TOKEN_REVOKED",
                "PERMISSION_CHANGED",
                "ROLE_ASSIGNED",
                "SESSION_TERMINATED"
            })
    private String eventType;

    @Schema(
            description = "事件状态",
            example = "SUCCESS",
            allowableValues = {"SUCCESS", "FAILURE", "PARTIAL_SUCCESS"})
    private String eventStatus;

    @Schema(
            description = "资源类型",
            example = "USER",
            allowableValues = {"USER", "TOKEN", "SESSION", "PERMISSION", "ROLE"})
    private String resourceType;

    @Schema(description = "资源ID", example = "1001")
    private String resourceId;

    @Schema(description = "操作描述", example = "User login")
    private String action;

    @Schema(description = "客户端IP地址", example = "192.168.1.100")
    private String ipAddress;

    @Schema(description = "User-Agent", example = "Mozilla/5.0")
    private String userAgent;

    @Schema(description = "地理位置信息", example = "Beijing, China")
    private String location;

    @Schema(description = "事件详情（JSON格式）", example = "{\"device\":\"iPhone\"}")
    private String details;

    @Schema(description = "失败原因", example = "Invalid password")
    private String failureReason;

    @Schema(
            description = "风险等级",
            example = "LOW",
            allowableValues = {"LOW", "MEDIUM", "HIGH", "CRITICAL"})
    private String riskLevel;

    @Schema(description = "事件时间", example = "2024-01-01T12:00:00")
    private LocalDateTime eventTime;

    @Schema(description = "创建时间", example = "2024-01-01T12:00:00")
    private LocalDateTime createdAt;
}
