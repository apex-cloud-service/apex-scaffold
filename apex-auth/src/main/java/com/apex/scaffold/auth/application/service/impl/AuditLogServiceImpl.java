package com.apex.scaffold.auth.application.service.impl;

import com.apex.scaffold.auth.application.service.AuditLogService;
import com.apex.scaffold.auth.domain.model.entity.AuditLogEntity;
import com.apex.scaffold.auth.domain.repository.AuditLogRepository;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 审计日志服务实现
 *
 * <p>实现审计日志的业务逻辑，包括日志记录、查询和清理。
 * 所有数据库操作使用专用的JDBC调度器隔离阻塞I/O。
 *
 * @author Apex Scaffold Team
 * @since 1.2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Override
    public Mono<AuditLogEntity> log(AuditLogEntity auditLog) {
        log.debug(
                "Recording audit log: eventType={}, userId={}, tenantId={}",
                auditLog.getEventType(),
                auditLog.getUserId(),
                auditLog.getTenantId());

        // 设置事件时间和创建时间
        if (auditLog.getEventTime() == null) {
            auditLog.setEventTime(LocalDateTime.now());
        }
        auditLog.setCreatedAt(LocalDateTime.now());

        return Mono.fromCallable(() -> auditLog)
                .flatMap(auditLogRepository::save)
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .doOnSuccess(saved -> log.info(
                        "Audit log recorded: id={}, eventType={}, status={}",
                        saved.getId(),
                        saved.getEventType(),
                        saved.getEventStatus()))
                .doOnError(error -> log.error(
                        "Failed to record audit log: eventType={}, error={}",
                        auditLog.getEventType(),
                        error.getMessage(),
                        error));
    }

    @Override
    public Mono<AuditLogEntity> logLogin(
            Long userId,
            String username,
            String eventStatus,
            String ipAddress,
            String userAgent,
            String tenantId,
            String failureReason) {
        log.debug("Logging login event: username={}, status={}, tenantId={}", username, eventStatus, tenantId);

        AuditLogEntity auditLog = AuditLogEntity.builder()
                .tenantId(tenantId)
                .userId(userId)
                .username(username)
                .eventType("LOGIN")
                .eventStatus(eventStatus)
                .resourceType("USER")
                .resourceId(userId != null ? userId.toString() : null)
                .action("User login")
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .failureReason(failureReason)
                .riskLevel("FAILURE".equals(eventStatus) ? "MEDIUM" : "LOW")
                .build();

        return log(auditLog);
    }

    @Override
    public Mono<AuditLogEntity> logLogout(Long userId, String username, String ipAddress, String tenantId) {
        log.debug("Logging logout event: username={}, tenantId={}", username, tenantId);

        AuditLogEntity auditLog = AuditLogEntity.builder()
                .tenantId(tenantId)
                .userId(userId)
                .username(username)
                .eventType("LOGOUT")
                .eventStatus("SUCCESS")
                .resourceType("USER")
                .resourceId(userId.toString())
                .action("User logout")
                .ipAddress(ipAddress)
                .riskLevel("LOW")
                .build();

        return log(auditLog);
    }

    @Override
    public Mono<AuditLogEntity> logTokenIssued(
            Long userId, String username, String tokenId, String ipAddress, String tenantId) {
        log.debug("Logging token issued event: username={}, tokenId={}, tenantId={}", username, tokenId, tenantId);

        AuditLogEntity auditLog = AuditLogEntity.builder()
                .tenantId(tenantId)
                .userId(userId)
                .username(username)
                .eventType("TOKEN_ISSUED")
                .eventStatus("SUCCESS")
                .resourceType("TOKEN")
                .resourceId(tokenId)
                .action("Token issued")
                .ipAddress(ipAddress)
                .riskLevel("LOW")
                .build();

        return log(auditLog);
    }

    @Override
    public Mono<AuditLogEntity> logTokenRefreshed(
            Long userId, String username, String tokenId, String ipAddress, String tenantId) {
        log.debug("Logging token refreshed event: username={}, tokenId={}, tenantId={}", username, tokenId, tenantId);

        AuditLogEntity auditLog = AuditLogEntity.builder()
                .tenantId(tenantId)
                .userId(userId)
                .username(username)
                .eventType("TOKEN_REFRESHED")
                .eventStatus("SUCCESS")
                .resourceType("TOKEN")
                .resourceId(tokenId)
                .action("Token refreshed")
                .ipAddress(ipAddress)
                .riskLevel("LOW")
                .build();

        return log(auditLog);
    }

    @Override
    public Mono<AuditLogEntity> logTokenRevoked(
            Long userId, String username, String tokenId, String ipAddress, String tenantId, String reason) {
        log.debug("Logging token revoked event: username={}, tokenId={}, tenantId={}", username, tokenId, tenantId);

        AuditLogEntity auditLog = AuditLogEntity.builder()
                .tenantId(tenantId)
                .userId(userId)
                .username(username)
                .eventType("TOKEN_REVOKED")
                .eventStatus("SUCCESS")
                .resourceType("TOKEN")
                .resourceId(tokenId)
                .action("Token revoked")
                .ipAddress(ipAddress)
                .details("{\"reason\":\"" + reason + "\"}")
                .riskLevel("MEDIUM")
                .build();

        return log(auditLog);
    }

    @Override
    public Mono<AuditLogEntity> logPermissionChanged(
            Long userId, String username, String action, String resourceId, String ipAddress, String tenantId) {
        log.debug("Logging permission changed event: username={}, action={}, tenantId={}", username, action, tenantId);

        AuditLogEntity auditLog = AuditLogEntity.builder()
                .tenantId(tenantId)
                .userId(userId)
                .username(username)
                .eventType("PERMISSION_CHANGED")
                .eventStatus("SUCCESS")
                .resourceType("PERMISSION")
                .resourceId(resourceId)
                .action(action)
                .ipAddress(ipAddress)
                .riskLevel("HIGH")
                .build();

        return log(auditLog);
    }

    @Override
    public Mono<AuditLogEntity> logRoleAssigned(
            Long userId, String username, Long targetUserId, Long roleId, String ipAddress, String tenantId) {
        log.debug(
                "Logging role assigned event: username={}, targetUserId={}, roleId={}, tenantId={}",
                username,
                targetUserId,
                roleId,
                tenantId);

        AuditLogEntity auditLog = AuditLogEntity.builder()
                .tenantId(tenantId)
                .userId(userId)
                .username(username)
                .eventType("ROLE_ASSIGNED")
                .eventStatus("SUCCESS")
                .resourceType("ROLE")
                .resourceId(roleId.toString())
                .action("Role assigned to user " + targetUserId)
                .ipAddress(ipAddress)
                .riskLevel("HIGH")
                .build();

        return log(auditLog);
    }

    @Override
    public Mono<AuditLogEntity> logSessionTerminated(
            Long userId, String username, String sessionId, String ipAddress, String tenantId, String reason) {
        log.debug(
                "Logging session terminated event: username={}, sessionId={}, tenantId={}",
                username,
                sessionId,
                tenantId);

        AuditLogEntity auditLog = AuditLogEntity.builder()
                .tenantId(tenantId)
                .userId(userId)
                .username(username)
                .eventType("SESSION_TERMINATED")
                .eventStatus("SUCCESS")
                .resourceType("SESSION")
                .resourceId(sessionId)
                .action("Session terminated")
                .ipAddress(ipAddress)
                .details("{\"reason\":\"" + reason + "\"}")
                .riskLevel("MEDIUM")
                .build();

        return log(auditLog);
    }

    @Override
    public Mono<AuditLogEntity> getLogById(Long id) {
        log.debug("Getting audit log by id: id={}", id);
        return auditLogRepository.findById(id).subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Flux<AuditLogEntity> listUserLogs(Long userId, String tenantId) {
        log.debug("Listing user audit logs: userId={}, tenantId={}", userId, tenantId);
        return auditLogRepository.findByUserId(userId, tenantId).subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Flux<AuditLogEntity> listLogsByEventType(String eventType, String tenantId) {
        log.debug("Listing audit logs by event type: eventType={}, tenantId={}", eventType, tenantId);
        return auditLogRepository.findByEventType(eventType, tenantId).subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Flux<AuditLogEntity> listLogsByTimeRange(LocalDateTime startTime, LocalDateTime endTime, String tenantId) {
        log.debug(
                "Listing audit logs by time range: startTime={}, endTime={}, tenantId={}",
                startTime,
                endTime,
                tenantId);
        return auditLogRepository
                .findByTimeRange(startTime, endTime, tenantId)
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Flux<AuditLogEntity> listFailedLogs(String tenantId) {
        log.debug("Listing failed audit logs: tenantId={}", tenantId);
        return auditLogRepository.findFailedEvents(tenantId).subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Flux<AuditLogEntity> listHighRiskLogs(String tenantId) {
        log.debug("Listing high risk audit logs: tenantId={}", tenantId);
        return auditLogRepository.findHighRiskEvents(tenantId).subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Flux<AuditLogEntity> listLogsByIpAddress(String ipAddress, String tenantId) {
        log.debug("Listing audit logs by IP address: ipAddress={}, tenantId={}", ipAddress, tenantId);
        return auditLogRepository.findByIpAddress(ipAddress, tenantId).subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Long> countLogsByTimeRange(LocalDateTime startTime, LocalDateTime endTime, String tenantId) {
        log.debug(
                "Counting audit logs by time range: startTime={}, endTime={}, tenantId={}",
                startTime,
                endTime,
                tenantId);
        return auditLogRepository
                .countByTimeRange(startTime, endTime, tenantId)
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Long> countLoginFailures(Long userId, LocalDateTime startTime, String tenantId) {
        log.debug("Counting login failures: userId={}, startTime={}, tenantId={}", userId, startTime, tenantId);

        LocalDateTime endTime = LocalDateTime.now();
        return auditLogRepository
                .findByUserIdAndEventType(userId, "LOGIN", tenantId)
                .filter(log -> log.isFailure()
                        && log.getEventTime().isAfter(startTime)
                        && log.getEventTime().isBefore(endTime))
                .count()
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Long> cleanupExpiredLogs(LocalDateTime beforeTime, String tenantId) {
        log.info("Cleaning up expired audit logs: beforeTime={}, tenantId={}", beforeTime, tenantId);
        return auditLogRepository
                .deleteByCreatedAtBefore(beforeTime, tenantId)
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .doOnSuccess(
                        count -> log.info("Expired audit logs cleaned up: count={}, tenantId={}", count, tenantId));
    }
}
