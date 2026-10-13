package com.apex.scaffold.auth.domain.model.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审计日志实体
 *
 * <p>记录所有认证授权相关的关键操作，用于安全审计、合规检查和问题排查。
 *
 * @author Apex Scaffold Team
 * @since 1.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogEntity {

    /**
     * 日志ID
     */
    private Long id;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 事件类型（LOGIN、LOGOUT、TOKEN_ISSUED、TOKEN_REFRESHED、TOKEN_REVOKED、PERMISSION_CHANGED、ROLE_ASSIGNED、SESSION_TERMINATED等）
     */
    private String eventType;

    /**
     * 事件状态（SUCCESS、FAILURE、PARTIAL_SUCCESS）
     */
    private String eventStatus;

    /**
     * 资源类型（USER、TOKEN、SESSION、PERMISSION、ROLE等）
     */
    private String resourceType;

    /**
     * 资源ID
     */
    private String resourceId;

    /**
     * 操作描述
     */
    private String action;

    /**
     * 客户端IP地址
     */
    private String ipAddress;

    /**
     * User-Agent
     */
    private String userAgent;

    /**
     * 地理位置信息
     */
    private String location;

    /**
     * 事件详情（JSON格式）
     */
    private String details;

    /**
     * 失败原因（仅在失败时记录）
     */
    private String failureReason;

    /**
     * 风险等级（LOW、MEDIUM、HIGH、CRITICAL）
     */
    private String riskLevel;

    /**
     * 事件时间
     */
    private LocalDateTime eventTime;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 检查事件是否成功
     *
     * @return 如果事件状态为SUCCESS返回true
     */
    public boolean isSuccess() {
        return "SUCCESS".equals(eventStatus);
    }

    /**
     * 检查事件是否失败
     *
     * @return 如果事件状态为FAILURE返回true
     */
    public boolean isFailure() {
        return "FAILURE".equals(eventStatus);
    }

    /**
     * 检查是否为高风险事件
     *
     * @return 如果风险等级为HIGH或CRITICAL返回true
     */
    public boolean isHighRisk() {
        return "HIGH".equals(riskLevel) || "CRITICAL".equals(riskLevel);
    }

    /**
     * 检查是否为登录相关事件
     *
     * @return 如果是登录或登出事件返回true
     */
    public boolean isLoginRelated() {
        return "LOGIN".equals(eventType) || "LOGOUT".equals(eventType);
    }

    /**
     * 检查是否为Token相关事件
     *
     * @return 如果是Token操作事件返回true
     */
    public boolean isTokenRelated() {
        return eventType != null && eventType.startsWith("TOKEN_");
    }

    /**
     * 检查是否为权限变更事件
     *
     * @return 如果是权限或角色变更事件返回true
     */
    public boolean isPermissionRelated() {
        return "PERMISSION_CHANGED".equals(eventType)
                || "ROLE_ASSIGNED".equals(eventType)
                || "ROLE_REVOKED".equals(eventType);
    }

    /**
     * 标记为高风险事件
     */
    public void markAsHighRisk() {
        this.riskLevel = "HIGH";
    }

    /**
     * 标记为严重风险事件
     */
    public void markAsCriticalRisk() {
        this.riskLevel = "CRITICAL";
    }
}
