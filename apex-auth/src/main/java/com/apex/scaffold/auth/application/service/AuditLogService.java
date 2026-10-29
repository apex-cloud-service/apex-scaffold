package com.apex.scaffold.auth.application.service;

import com.apex.scaffold.auth.domain.model.entity.AuditLogEntity;
import java.time.LocalDateTime;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 审计日志服务接口
 *
 * <p>提供审计日志的记录、查询和管理功能，用于安全审计、合规检查和问题排查。
 * 支持多维度的日志查询和统计分析。
 *
 * @author Apex Scaffold Team
 * @since 1.2.0
 */
public interface AuditLogService {

    /**
     * 记录审计日志
     *
     * @param auditLog 审计日志实体
     * @return 保存后的审计日志实体
     */
    Mono<AuditLogEntity> log(AuditLogEntity auditLog);

    /**
     * 记录通用审计日志
     *
     * @param eventType 事件类型
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @param action 操作描述
     * @param resourceId 资源ID（可为null）
     * @param entity 关联实体对象（可为null）
     */
    default Mono<Void> log(
            String eventType, String userId, String tenantId, String action, String resourceId, Object entity) {
        Long userIdLong = null;
        try {
            if (userId != null && !userId.isEmpty()) {
                userIdLong = Long.parseLong(userId);
            }
        } catch (NumberFormatException e) {
            // userId 不是数字格式，保持为 null
        }

        AuditLogEntity auditLog = AuditLogEntity.builder()
                .tenantId(tenantId)
                .userId(userIdLong)
                .username(userId)
                .eventType(eventType)
                .eventStatus("SUCCESS")
                .action(action)
                .resourceId(resourceId)
                .riskLevel("MEDIUM")
                .build();

        return log(auditLog).then();
    }

    /**
     * 记录登录事件
     *
     * @param userId 用户ID
     * @param username 用户名
     * @param eventStatus 事件状态（SUCCESS/FAILURE）
     * @param ipAddress IP地址
     * @param userAgent User-Agent
     * @param tenantId 租户ID
     * @param failureReason 失败原因（仅在失败时提供）
     * @return 保存后的审计日志实体
     */
    Mono<AuditLogEntity> logLogin(
            Long userId,
            String username,
            String eventStatus,
            String ipAddress,
            String userAgent,
            String tenantId,
            String failureReason);

    /**
     * 记录登出事件
     *
     * @param userId 用户ID
     * @param username 用户名
     * @param ipAddress IP地址
     * @param tenantId 租户ID
     * @return 保存后的审计日志实体
     */
    Mono<AuditLogEntity> logLogout(Long userId, String username, String ipAddress, String tenantId);

    /**
     * 记录Token颁发事件
     *
     * @param userId 用户ID
     * @param username 用户名
     * @param tokenId Token ID
     * @param ipAddress IP地址
     * @param tenantId 租户ID
     * @return 保存后的审计日志实体
     */
    Mono<AuditLogEntity> logTokenIssued(
            Long userId, String username, String tokenId, String ipAddress, String tenantId);

    /**
     * 记录Token刷新事件
     *
     * @param userId 用户ID
     * @param username 用户名
     * @param tokenId Token ID
     * @param ipAddress IP地址
     * @param tenantId 租户ID
     * @return 保存后的审计日志实体
     */
    Mono<AuditLogEntity> logTokenRefreshed(
            Long userId, String username, String tokenId, String ipAddress, String tenantId);

    /**
     * 记录Token撤销事件
     *
     * @param userId 用户ID
     * @param username 用户名
     * @param tokenId Token ID
     * @param ipAddress IP地址
     * @param tenantId 租户ID
     * @param reason 撤销原因
     * @return 保存后的审计日志实体
     */
    Mono<AuditLogEntity> logTokenRevoked(
            Long userId, String username, String tokenId, String ipAddress, String tenantId, String reason);

    /**
     * 记录权限变更事件
     *
     * @param userId 用户ID
     * @param username 用户名
     * @param action 操作描述
     * @param resourceId 资源ID
     * @param ipAddress IP地址
     * @param tenantId 租户ID
     * @return 保存后的审计日志实体
     */
    Mono<AuditLogEntity> logPermissionChanged(
            Long userId, String username, String action, String resourceId, String ipAddress, String tenantId);

    /**
     * 记录角色分配事件
     *
     * @param userId 用户ID
     * @param username 用户名
     * @param targetUserId 目标用户ID
     * @param roleId 角色ID
     * @param ipAddress IP地址
     * @param tenantId 租户ID
     * @return 保存后的审计日志实体
     */
    Mono<AuditLogEntity> logRoleAssigned(
            Long userId, String username, Long targetUserId, Long roleId, String ipAddress, String tenantId);

    /**
     * 记录会话终止事件
     *
     * @param userId 用户ID
     * @param username 用户名
     * @param sessionId 会话ID
     * @param ipAddress IP地址
     * @param tenantId 租户ID
     * @param reason 终止原因
     * @return 保存后的审计日志实体
     */
    Mono<AuditLogEntity> logSessionTerminated(
            Long userId, String username, String sessionId, String ipAddress, String tenantId, String reason);

    /**
     * 根据ID查询审计日志
     *
     * @param id 日志ID
     * @return 审计日志实体
     */
    Mono<AuditLogEntity> getLogById(Long id);

    /**
     * 查询用户的审计日志
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @return 审计日志实体流
     */
    Flux<AuditLogEntity> listUserLogs(Long userId, String tenantId);

    /**
     * 根据事件类型查询审计日志
     *
     * @param eventType 事件类型
     * @param tenantId 租户ID
     * @return 审计日志实体流
     */
    Flux<AuditLogEntity> listLogsByEventType(String eventType, String tenantId);

    /**
     * 根据时间范围查询审计日志
     *
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param tenantId 租户ID
     * @return 审计日志实体流
     */
    Flux<AuditLogEntity> listLogsByTimeRange(LocalDateTime startTime, LocalDateTime endTime, String tenantId);

    /**
     * 查询失败的审计日志
     *
     * @param tenantId 租户ID
     * @return 失败的审计日志实体流
     */
    Flux<AuditLogEntity> listFailedLogs(String tenantId);

    /**
     * 查询高风险审计日志
     *
     * @param tenantId 租户ID
     * @return 高风险的审计日志实体流
     */
    Flux<AuditLogEntity> listHighRiskLogs(String tenantId);

    /**
     * 根据IP地址查询审计日志
     *
     * @param ipAddress IP地址
     * @param tenantId 租户ID
     * @return 审计日志实体流
     */
    Flux<AuditLogEntity> listLogsByIpAddress(String ipAddress, String tenantId);

    /**
     * 统计指定时间范围内的日志数量
     *
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param tenantId 租户ID
     * @return 日志数量
     */
    Mono<Long> countLogsByTimeRange(LocalDateTime startTime, LocalDateTime endTime, String tenantId);

    /**
     * 统计指定用户的登录失败次数
     *
     * @param userId 用户ID
     * @param startTime 开始时间
     * @param tenantId 租户ID
     * @return 失败次数
     */
    Mono<Long> countLoginFailures(Long userId, LocalDateTime startTime, String tenantId);

    /**
     * 清理过期的审计日志
     *
     * @param beforeTime 截止时间
     * @param tenantId 租户ID
     * @return 删除的日志数量
     */
    Mono<Long> cleanupExpiredLogs(LocalDateTime beforeTime, String tenantId);
}
