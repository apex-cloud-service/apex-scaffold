package com.apex.scaffold.authcenter.domain.service;

import com.apex.scaffold.common.util.LogSafetyUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 统一审计日志服务
 *
 * <p>统一记录所有安全相关事件，满足合规要求和安全追溯需求：</p>
 * <ul>
 *   <li>认证事件：登录、登出、密码修改、MFA操作</li>
 *   <li>授权事件：权限变更、角色分配、资源访问</li>
 *   <li>OAuth2事件：授权码颁发、Token刷新、客户端操作</li>
 *   <li>SSO事件：SAML/OIDC认证、联邦登录</li>
 *   <li>安全事件：登录失败、暴力破解、异常行为</li>
 *   <li>系统事件：密钥轮换、配置变更、服务启停</li>
 * </ul>
 *
 * <p>审计日志包含字段：</p>
 * <ul>
 *   <li>eventId: 事件唯一标识</li>
 *   <li>eventType: 事件类型（AUTH/AUTHZ/OAUTH2/SSO/SECURITY/SYSTEM）</li>
 *   <li>action: 具体操作（LOGIN/LOGOUT/TOKEN_REFRESH/etc）</li>
 *   <li>userId: 用户ID（如适用）</li>
 *   <li>tenantId: 租户ID（多租户隔离）</li>
 *   <li>clientIp: 客户端IP地址</li>
 *   <li>userAgent: 用户代理字符串</li>
 *   <li>success: 操作是否成功</li>
 *   <li>errorMessage: 失败原因（如适用）</li>
 *   <li>details: 额外详情（JSON格式）</li>
 *   <li>timestamp: 时间戳</li>
 *   <li>traceId: 分布式追踪ID</li>
 * </ul>
 *
 * @author Apex Security Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnifiedAuditService {

    private static final String AUDIT_LOG_STREAM = "audit:stream";
    private static final String AUDIT_SECURITY_ALERT = "audit:security:alert";
    private static final String SEVERITY_HIGH = "HIGH";

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final java.util.concurrent.Semaphore alerts = new java.util.concurrent.Semaphore(64);
    private final java.util.concurrent.Semaphore pending = new java.util.concurrent.Semaphore(256);
    private final java.util.concurrent.atomic.AtomicLong dropped = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong failed = new java.util.concurrent.atomic.AtomicLong();

    public long getDroppedCount() {
        return dropped.get();
    }

    public long getFailedCount() {
        return failed.get();
    }

    private Map<String, Object> copyDetails(Map<String, Object> details) {
        Map<String, Object> copy = new java.util.LinkedHashMap<>();
        if (details != null) {
            details.entrySet().stream().limit(28).forEach(entry -> copy.put(entry.getKey(), entry.getValue()));
        }
        return copy;
    }

    /**
     * 记录认证事件
     *
     * @param action 操作类型（LOGIN/LOGOUT/PASSWORD_CHANGE/MFA_ENABLE）
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @param clientIp 客户端IP
     * @param success 是否成功
     * @param errorMessage 错误信息（如失败）
     * @param details 额外详情
     * @return 审计记录
     */
    public Mono<AuditLog> logAuthenticationEvent(
            String action,
            String userId,
            String tenantId,
            String clientIp,
            boolean success,
            String errorMessage,
            Map<String, Object> details) {

        return logEvent(
                EventType.AUTHENTICATION, action, userId, tenantId, clientIp, null, success, errorMessage, details);
    }

    /**
     * 记录授权事件
     *
     * @param action 操作类型（PERMISSION_CHECK/ROLE_ASSIGN/RESOURCE_ACCESS）
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @param resourceId 资源ID
     * @param success 是否成功
     * @param details 额外详情
     * @return 审计记录
     */
    public Mono<AuditLog> logAuthorizationEvent(
            String action,
            String userId,
            String tenantId,
            String resourceId,
            boolean success,
            Map<String, Object> details) {

        details = copyDetails(details);
        details.put("resourceId", resourceId);

        return logEvent(EventType.AUTHORIZATION, action, userId, tenantId, null, null, success, null, details);
    }

    /**
     * 记录OAuth2事件
     *
     * @param action 操作类型(AUTHORIZE/TOKEN/REFRESH/REVOKE)
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @param clientId OAuth2客户端ID
     * @param clientIp 客户端IP
     * @param success 是否成功
     * @param details 额外详情
     * @return 审计记录
     */
    @SuppressWarnings("PMD.LowerCamelCaseVariableNamingRule")
    public Mono<AuditLog> logOAuth2Event(
            String action,
            String userId,
            String tenantId,
            String clientId,
            String clientIp,
            boolean success,
            Map<String, Object> details) {

        details = copyDetails(details);
        details.put("clientId", clientId);

        return logEvent(EventType.OAUTH2, action, userId, tenantId, clientIp, null, success, null, details);
    }

    /**
     * 记录SSO事件
     *
     * @param action 操作类型（SAML_AUTH/OIDC_AUTH/SSO_LOGOUT）
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @param protocol SSO协议（SAML/OIDC/CAS）
     * @param idpName 身份提供商名称
     * @param clientIp 客户端IP
     * @param success 是否成功
     * @param errorMessage 错误信息
     * @param details 额外详情
     * @return 审计记录
     */
    public Mono<AuditLog> logSsoEvent(
            String action,
            String userId,
            String tenantId,
            String protocol,
            String idpName,
            String clientIp,
            boolean success,
            String errorMessage,
            Map<String, Object> details) {

        details = copyDetails(details);
        details.put("protocol", protocol);
        details.put("idpName", idpName);

        return logEvent(EventType.SSO, action, userId, tenantId, clientIp, null, success, errorMessage, details);
    }

    /**
     * 记录安全事件（高风险事件，需要实时告警）
     *
     * @param action 操作类型（LOGIN_FAILED/BRUTE_FORCE/SUSPICIOUS_ACTIVITY/KEY_LEAKED）
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @param clientIp 客户端IP
     * @param severity 严重程度（HIGH/MEDIUM/LOW）
     * @param errorMessage 事件描述
     * @param details 额外详情
     * @return 审计记录
     */
    public Mono<AuditLog> logSecurityEvent(
            String action,
            String userId,
            String tenantId,
            String clientIp,
            String severity,
            String errorMessage,
            Map<String, Object> details) {

        details = copyDetails(details);
        details.put("severity", severity);

        return logEvent(EventType.SECURITY, action, userId, tenantId, clientIp, null, false, errorMessage, details)
                .flatMap(auditLog -> {
                    if (SEVERITY_HIGH.equals(severity)) {
                        return sendSecurityAlert(auditLog).thenReturn(auditLog);
                    }
                    return Mono.just(auditLog);
                });
    }

    /**
     * 记录系统事件
     *
     * @param action 操作类型（KEY_ROTATION/CONFIG_CHANGE/SERVICE_START/SERVICE_STOP）
     * @param operator 操作员（系统账号或管理员）
     * @param success 是否成功
     * @param details 额外详情
     * @return 审计记录
     */
    public Mono<AuditLog> logSystemEvent(String action, String operator, boolean success, Map<String, Object> details) {

        return logEvent(EventType.SYSTEM, action, operator, null, null, null, success, null, details);
    }

    /**
     * 核心日志记录方法
     */
    private Mono<AuditLog> logEvent(
            EventType eventType,
            String action,
            String userId,
            String tenantId,
            String clientIp,
            String userAgent,
            boolean success,
            String errorMessage,
            Map<String, Object> details) {

        return Mono.deferContextual(context -> {
            if (!pending.tryAcquire()) {
                dropped.incrementAndGet();
                return Mono.empty();
            }
            return Mono.fromCallable(() -> {
                        AuditLog auditLog = new AuditLog();
                        auditLog.setEventId(UUID.randomUUID().toString());
                        auditLog.setEventType(eventType.name());
                        auditLog.setAction(LogSafetyUtil.text(action, 64));
                        auditLog.setUserId(LogSafetyUtil.text(userId, 64));
                        auditLog.setTenantId(LogSafetyUtil.text(tenantId, 64));
                        auditLog.setClientIp(LogSafetyUtil.text(clientIp, 64));
                        auditLog.setUserAgent(LogSafetyUtil.text(userAgent, 256));
                        auditLog.setSuccess(success);
                        auditLog.setErrorMessage(errorMessage == null ? null : "[omitted]");
                        auditLog.setDetails(objectMapper.readValue(
                                LogSafetyUtil.json(details, 2048),
                                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}));
                        auditLog.setTimestamp(Instant.now());
                        auditLog.setTraceId(LogSafetyUtil.text(context.getOrDefault("traceId", getTraceId()), 64));
                        return auditLog;
                    })
                    .flatMap(auditLog -> Mono.fromCallable(() -> objectMapper.writeValueAsString(auditLog))
                            .flatMap(json -> redisTemplate
                                    .opsForStream()
                                    .add(
                                            AUDIT_LOG_STREAM,
                                            Map.of("data", json),
                                            org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions
                                                    .maxlen(100000))
                                    .thenReturn(auditLog)))
                    .timeout(java.time.Duration.ofSeconds(3))
                    .onErrorResume(error -> {
                        failed.incrementAndGet();
                        return Mono.empty();
                    })
                    .doFinally(signal -> pending.release());
        });
    }

    /**
     * 发送安全告警到专用队列
     */
    private Mono<Void> sendSecurityAlert(AuditLog auditLog) {
        return Mono.defer(() -> {
            if (!alerts.tryAcquire()) {
                dropped.incrementAndGet();
                return Mono.empty();
            }
            return Mono.fromCallable(() -> objectMapper.writeValueAsString(auditLog))
                    .flatMap(json -> {
                        Map<String, String> alertData = new HashMap<>(16);
                        alertData.put("alert", json);

                        return redisTemplate
                                .opsForStream()
                                .add(
                                        AUDIT_SECURITY_ALERT,
                                        alertData,
                                        org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions
                                                .maxlen(10000))
                                .then();
                    })
                    .timeout(java.time.Duration.ofSeconds(3))
                    .doOnSuccess(v -> log.debug(
                            "[SECURITY ALERT] 高危安全事件: action={}, userId={}, clientIp={}, error={}",
                            auditLog.getAction(),
                            auditLog.getUserId(),
                            auditLog.getClientIp(),
                            auditLog.getErrorMessage()))
                    .onErrorResume(e -> {
                        failed.incrementAndGet();
                        return Mono.empty();
                    })
                    .doFinally(signal -> alerts.release());
        });
    }

    /**
     * 获取当前请求的TraceID（分布式追踪）
     * 实际项目中应从MDC或Sleuth中获取
     */
    private String getTraceId() {
        String traceId = org.slf4j.MDC.get("traceId");
        return traceId == null ? UUID.randomUUID().toString() : LogSafetyUtil.text(traceId, 64);
    }

    /**
     * 事件类型枚举
     */
    public enum EventType {
        // 认证事件
        AUTHENTICATION,
        // 授权事件
        AUTHORIZATION,
        // OAuth2事件
        OAUTH2,
        // SSO事件
        SSO,
        // 安全事件
        SECURITY,
        // 系统事件
        SYSTEM
    }

    /**
     * 审计日志实体
     */
    @lombok.Data
    public static class AuditLog {
        private String eventId;
        private String eventType;
        private String action;
        private String userId;
        private String tenantId;
        private String clientIp;
        private String userAgent;
        private boolean success;
        private String errorMessage;
        private Map<String, Object> details;
        private Instant timestamp;
        private String traceId;
    }
}
