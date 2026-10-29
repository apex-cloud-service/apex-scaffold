package com.apex.scaffold.common.audit;

import com.apex.scaffold.common.util.TraceIdUtil;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 安全审计服务实现
 *
 * <p>实现 {@link SecurityAuditService} 接口，统一记录所有安全相关事件，
 * 包括认证、授权、Token操作、安全威胁等。
 *
 * <h2>审计事件类型</h2>
 * <ul>
 *   <li>认证事件：登录成功/失败、登出、密码修改</li>
 *   <li>Token事件：签发、刷新、撤销、验证失败</li>
 *   <li>授权事件：权限检查通过/失败、角色变更</li>
 *   <li>安全威胁：暴力破解、Token重放、异常访问</li>
 * </ul>
 *
 * <h2>审计日志字段</h2>
 * <ul>
 *   <li>时间戳：精确到毫秒</li>
 *   <li>事件类型：标准化的事件分类</li>
 *   <li>用户信息：userId、username、tenantId</li>
 *   <li>客户端信息：IP、User-Agent、设备ID</li>
 *   <li>操作详情：资源、动作、结果、失败原因</li>
 *   <li>安全上下文：会话ID、请求ID、Token JTI</li>
 * </ul>
 *
 * <h2>实现说明</h2>
 * <p>当前实现将审计事件统一转换为结构化日志输出（{@code SECURITY_AUDIT} 前缀），
 * 便于日志采集系统（如 ELK/SLS）按字段解析和检索。所有响应式方法均使用 {@link Mono#empty()}
 * 作为终态，不产生阻塞 I/O；密钥相关事件方法（{@code void} 返回）内部通过 {@code subscribe()}
 * 触发日志写入，属于“即发即弃”（fire-and-forget）调用，不会向上抛出异常影响主流程。
 *
 * <p>本类原位于 {@code apex-auth-center} 模块，2026-09-30 随 {@link SecurityAuditService}
 * 接口一并下沉至 {@code apex-common}，供多个业务模块共享同一套审计事件记录逻辑。
 *
 * @author Apex Security Team
 * @version 1.0.0
 * @since 1.3.1
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityAuditServiceImpl implements SecurityAuditService {

    /**
     * 记录登录成功事件
     *
     * @param userId 用户ID
     * @param username 用户名
     * @param tenantId 租户ID
     * @param clientIp 客户端IP
     * @param userAgent 浏览器User-Agent
     * @param deviceId 设备ID
     * @return 完成信号
     */
    @Override
    public Mono<Void> logLoginSuccess(
            Long userId, String username, String tenantId, String clientIp, String userAgent, String deviceId) {
        return logAuditEvent(AuditEvent.builder()
                .eventType("AUTH_LOGIN_SUCCESS")
                .userId(userId)
                .username(username)
                .tenantId(tenantId)
                .clientIp(clientIp)
                .userAgent(userAgent)
                .deviceId(deviceId)
                .success(true)
                .message("用户登录成功")
                .build());
    }

    /**
     * 记录登录失败事件
     *
     * @param username 用户名
     * @param tenantId 租户ID
     * @param clientIp 客户端IP
     * @param userAgent 浏览器User-Agent
     * @param reason 失败原因
     * @return 完成信号
     */
    @Override
    public Mono<Void> logLoginFailure(
            String username, String tenantId, String clientIp, String userAgent, String reason) {
        return logAuditEvent(AuditEvent.builder()
                .eventType("AUTH_LOGIN_FAILURE")
                .username(username)
                .tenantId(tenantId)
                .clientIp(clientIp)
                .userAgent(userAgent)
                .success(false)
                .message("登录失败: " + reason)
                .reason(reason)
                .build());
    }

    /**
     * 记录登出事件
     *
     * @param userId 用户ID
     * @param username 用户名
     * @param tenantId 租户ID
     * @param clientIp 客户端IP
     * @return 完成信号
     */
    @Override
    public Mono<Void> logLogout(Long userId, String username, String tenantId, String clientIp) {
        return logAuditEvent(AuditEvent.builder()
                .eventType("AUTH_LOGOUT")
                .userId(userId)
                .username(username)
                .tenantId(tenantId)
                .clientIp(clientIp)
                .success(true)
                .message("用户登出")
                .build());
    }

    /**
     * 记录Token签发事件
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @param clientIp 客户端IP
     * @param deviceId 设备ID
     * @param tokenType Token类型（ACCESS/REFRESH）
     * @return 完成信号
     */
    @Override
    public Mono<Void> logTokenIssued(Long userId, String tenantId, String clientIp, String deviceId, String tokenType) {
        return logAuditEvent(AuditEvent.builder()
                .eventType("TOKEN_ISSUED")
                .userId(userId)
                .tenantId(tenantId)
                .clientIp(clientIp)
                .deviceId(deviceId)
                .success(true)
                .message("Token签发: " + tokenType)
                .metadata(Map.of("tokenType", tokenType))
                .build());
    }

    /**
     * 记录Token刷新事件
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @param clientIp 客户端IP
     * @param jti 旧Token的JTI
     * @return 完成信号
     */
    @Override
    public Mono<Void> logTokenRefreshed(Long userId, String tenantId, String clientIp, String jti) {
        return logAuditEvent(AuditEvent.builder()
                .eventType("TOKEN_REFRESHED")
                .userId(userId)
                .tenantId(tenantId)
                .clientIp(clientIp)
                .success(true)
                .message("Token刷新成功")
                .metadata(Map.of("oldJti", jti))
                .build());
    }

    /**
     * 记录Token撤销事件
     *
     * @param userId 用户ID
     * @param username 用户名
     * @param tenantId 租户ID
     * @param jti 被撤销Token的JTI
     * @param reason 撤销原因
     * @param clientIp 客户端IP
     * @return 完成信号
     */
    @Override
    public Mono<Void> logTokenRevoked(
            Long userId, String username, String tenantId, String jti, String reason, String clientIp) {
        return logAuditEvent(AuditEvent.builder()
                .eventType("TOKEN_REVOKED")
                .userId(userId)
                .username(username)
                .tenantId(tenantId)
                .clientIp(clientIp)
                .success(true)
                .message("Token撤销: " + reason)
                .reason(reason)
                .metadata(Map.of("jti", jti))
                .build());
    }

    /**
     * 记录Token验证失败事件
     *
     * @param clientIp 客户端IP
     * @param reason 失败原因
     * @param tokenHint Token提示信息（脱敏后，用于排查）
     * @return 完成信号
     */
    @Override
    public Mono<Void> logTokenValidationFailure(String clientIp, String reason, String tokenHint) {
        return logAuditEvent(AuditEvent.builder()
                .eventType("TOKEN_VALIDATION_FAILED")
                .clientIp(clientIp)
                .success(false)
                .message("Token验证失败: " + reason)
                .reason(reason)
                .metadata(Map.of("tokenHint", tokenHint))
                .build());
    }

    /**
     * 记录Token重放攻击事件
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @param clientIp 客户端IP
     * @param jti 被重放Token的JTI
     * @return 完成信号
     */
    @Override
    public Mono<Void> logTokenReplayAttack(Long userId, String tenantId, String clientIp, String jti) {
        return logAuditEvent(AuditEvent.builder()
                .eventType("SECURITY_TOKEN_REPLAY_ATTACK")
                .userId(userId)
                .tenantId(tenantId)
                .clientIp(clientIp)
                .success(false)
                .severity("HIGH")
                .message("检测到Token重放攻击")
                .reason("使用已撤销的refresh token")
                .metadata(Map.of("jti", jti))
                .build());
    }

    /**
     * 记录暴力破解攻击事件
     *
     * @param username 用户名
     * @param clientIp 客户端IP
     * @param attemptCount 尝试次数
     * @return 完成信号
     */
    @Override
    public Mono<Void> logBruteForceAttack(String username, String clientIp, int attemptCount) {
        return logAuditEvent(AuditEvent.builder()
                .eventType("SECURITY_BRUTE_FORCE_ATTACK")
                .username(username)
                .clientIp(clientIp)
                .success(false)
                .severity("HIGH")
                .message("检测到暴力破解攻击")
                .reason("登录失败次数超过阈值")
                .metadata(Map.of("attemptCount", String.valueOf(attemptCount)))
                .build());
    }

    /**
     * 记录权限检查失败事件
     *
     * @param userId 用户ID
     * @param username 用户名
     * @param tenantId 租户ID
     * @param resource 被访问资源
     * @param action 执行动作
     * @param clientIp 客户端IP
     * @return 完成信号
     */
    @Override
    public Mono<Void> logPermissionDenied(
            Long userId, String username, String tenantId, String resource, String action, String clientIp) {
        return logAuditEvent(AuditEvent.builder()
                .eventType("AUTHZ_PERMISSION_DENIED")
                .userId(userId)
                .username(username)
                .tenantId(tenantId)
                .clientIp(clientIp)
                .success(false)
                .message("权限检查失败")
                .reason("用户无权限访问资源")
                .metadata(Map.of("resource", resource, "action", action))
                .build());
    }

    /**
     * 记录敏感操作事件
     *
     * @param userId 用户ID
     * @param username 用户名
     * @param tenantId 租户ID
     * @param operation 操作名称
     * @param resource 操作资源
     * @param clientIp 客户端IP
     * @return 完成信号
     */
    @Override
    public Mono<Void> logSensitiveOperation(
            Long userId, String username, String tenantId, String operation, String resource, String clientIp) {
        return logAuditEvent(AuditEvent.builder()
                .eventType("SENSITIVE_OPERATION")
                .userId(userId)
                .username(username)
                .tenantId(tenantId)
                .clientIp(clientIp)
                .success(true)
                .severity("MEDIUM")
                .message("敏感操作执行: " + operation)
                .metadata(Map.of("operation", operation, "resource", resource))
                .build());
    }

    /**
     * 记录密码修改事件
     *
     * @param userId 用户ID
     * @param username 用户名
     * @param tenantId 租户ID
     * @param clientIp 客户端IP
     * @return 完成信号
     */
    @Override
    public Mono<Void> logPasswordChanged(Long userId, String username, String tenantId, String clientIp) {
        return logAuditEvent(AuditEvent.builder()
                .eventType("AUTH_PASSWORD_CHANGED")
                .userId(userId)
                .username(username)
                .tenantId(tenantId)
                .clientIp(clientIp)
                .success(true)
                .severity("MEDIUM")
                .message("用户密码已修改")
                .build());
    }

    /**
     * 记录账户锁定事件
     *
     * @param userId 用户ID
     * @param username 用户名
     * @param tenantId 租户ID
     * @param reason 锁定原因
     * @return 完成信号
     */
    @Override
    public Mono<Void> logAccountLocked(Long userId, String username, String tenantId, String reason) {
        return logAuditEvent(AuditEvent.builder()
                .eventType("AUTH_ACCOUNT_LOCKED")
                .userId(userId)
                .username(username)
                .tenantId(tenantId)
                .success(true)
                .severity("HIGH")
                .message("账户已锁定: " + reason)
                .reason(reason)
                .build());
    }

    /**
     * 记录账户解锁事件
     *
     * @param userId 用户ID
     * @param username 用户名
     * @param tenantId 租户ID
     * @param operator 操作人
     * @return 完成信号
     */
    @Override
    public Mono<Void> logAccountUnlocked(Long userId, String username, String tenantId, String operator) {
        return logAuditEvent(AuditEvent.builder()
                .eventType("AUTH_ACCOUNT_UNLOCKED")
                .userId(userId)
                .username(username)
                .tenantId(tenantId)
                .success(true)
                .message("账户已解锁")
                .metadata(Map.of("operator", operator))
                .build());
    }

    /**
     * 记录密钥吊销事件
     *
     * @param keyVersion 密钥版本
     * @param reason 吊销原因
     * @param operatorUsername 操作人用户名
     */
    @Override
    public void logKeyRevocation(String keyVersion, String reason, String operatorUsername) {
        logAuditEvent(AuditEvent.builder()
                        .eventType("KEY_REVOKED")
                        .username(operatorUsername)
                        .success(true)
                        .severity("HIGH")
                        .message("JWT密钥已吊销: " + keyVersion)
                        .reason(reason)
                        .metadata(Map.of("keyVersion", keyVersion, "operator", operatorUsername))
                        .build())
                .subscribe();
    }

    /**
     * 记录密钥轮换事件
     *
     * @param oldVersion 旧密钥版本
     * @param newVersion 新密钥版本
     * @param reason 轮换原因
     * @param operatorUsername 操作人用户名
     */
    @Override
    public void logKeyRotation(String oldVersion, String newVersion, String reason, String operatorUsername) {
        logAuditEvent(AuditEvent.builder()
                        .eventType("KEY_ROTATED")
                        .username(operatorUsername)
                        .success(true)
                        .severity("MEDIUM")
                        .message("JWT密钥已轮换: " + oldVersion + " -> " + newVersion)
                        .reason(reason)
                        .metadata(Map.of(
                                "oldVersion", oldVersion,
                                "newVersion", newVersion,
                                "operator", operatorUsername))
                        .build())
                .subscribe();
    }

    /**
     * 记录密钥轮换失败事件
     *
     * @param keyVersion 密钥版本
     * @param reason 失败原因
     * @param operatorUsername 操作人用户名
     */
    @Override
    public void logKeyRotationFailure(String keyVersion, String reason, String operatorUsername) {
        logAuditEvent(AuditEvent.builder()
                        .eventType("KEY_ROTATION_FAILED")
                        .username(operatorUsername)
                        .success(false)
                        .severity("HIGH")
                        .message("JWT密钥轮换失败: " + keyVersion)
                        .reason(reason)
                        .metadata(Map.of(
                                "keyVersion", keyVersion,
                                "operator", operatorUsername))
                        .build())
                .subscribe();
    }

    /**
     * 记录密钥生成事件
     *
     * @param keyVersion 密钥版本
     * @param reason 生成原因
     * @param operatorUsername 操作人用户名
     */
    @Override
    public void logKeyGeneration(String keyVersion, String reason, String operatorUsername) {
        logAuditEvent(AuditEvent.builder()
                        .eventType("KEY_GENERATED")
                        .username(operatorUsername)
                        .success(true)
                        .severity("HIGH")
                        .message("JWT密钥已生成: " + keyVersion)
                        .reason(reason)
                        .metadata(Map.of(
                                "keyVersion", keyVersion,
                                "operator", operatorUsername))
                        .build())
                .subscribe();
    }

    /**
     * 记录通用安全事件
     *
     * @param eventType 事件类型
     * @param message 消息
     * @param userId 用户ID
     * @param username 用户名
     * @param tenantId 租户ID
     * @param reason 原因
     * @return 完成信号
     */
    @Override
    public Mono<Void> logSecurityEvent(
            String eventType, String message, Long userId, String username, String tenantId, String reason) {
        return logAuditEvent(AuditEvent.builder()
                .eventType(eventType)
                .userId(userId)
                .username(username)
                .tenantId(tenantId)
                .success(true)
                .severity("MEDIUM")
                .message(message)
                .reason(reason)
                .build());
    }

    /**
     * 记录通用安全事件（带严重级别、成功标记和详情）
     *
     * @param eventType 事件类型
     * @param message 消息
     * @param userId 用户ID（字符串形式）
     * @param username 用户名
     * @param severity 严重级别（LOW/MEDIUM/HIGH/CRITICAL）
     * @param success 是否成功
     * @param details 详情Map
     * @param reason 原因
     * @return 完成信号
     */
    @Override
    public Mono<Void> logSecurityEvent(
            String eventType,
            String message,
            String userId,
            String username,
            String severity,
            boolean success,
            Map<String, Object> details,
            String reason) {
        Long userIdLong = null;
        try {
            if (userId != null) {
                userIdLong = Long.parseLong(userId);
            }
        } catch (NumberFormatException e) {
            // 忽略
        }
        return logAuditEvent(AuditEvent.builder()
                .eventType(eventType)
                .userId(userIdLong)
                .username(username)
                .success(success)
                .severity(severity != null ? severity : "MEDIUM")
                .message(message)
                .reason(reason)
                .metadata(
                        details != null
                                ? details.entrySet().stream()
                                        .collect(java.util.stream.Collectors.toMap(
                                                Map.Entry::getKey, e -> String.valueOf(e.getValue())))
                                : null)
                .build());
    }

    /**
     * 记录客户端密钥轮换事件
     *
     * @param clientId 客户端ID
     * @param operatorUserId 操作人ID
     * @param clientIp 客户端IP
     * @param details 详情Map
     * @return 完成信号
     */
    @Override
    public Mono<Void> logClientSecretRotation(
            String clientId, String operatorUserId, String clientIp, Map<String, Object> details) {
        Long userIdLong = null;
        try {
            if (operatorUserId != null) {
                userIdLong = Long.parseLong(operatorUserId);
            }
        } catch (NumberFormatException e) {
            // 忽略
        }
        return logAuditEvent(AuditEvent.builder()
                .eventType("CLIENT_SECRET_ROTATED")
                .userId(userIdLong)
                .clientIp(clientIp)
                .success(true)
                .severity("MEDIUM")
                .message("客户端密钥已轮换: " + clientId)
                .metadata(
                        details != null
                                ? details.entrySet().stream()
                                        .collect(java.util.stream.Collectors.toMap(
                                                Map.Entry::getKey, e -> String.valueOf(e.getValue())))
                                : null)
                .build());
    }

    /**
     * 记录Token轮换事件
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @param tokenFamily Token家族ID
     * @param oldJti 旧Token的JTI
     * @param newJti 新Token的JTI
     * @param clientIp 客户端IP
     * @return 完成信号
     */
    @Override
    public Mono<Void> logTokenRotation(
            Long userId, String tenantId, String tokenFamily, String oldJti, String newJti, String clientIp) {
        return logAuditEvent(AuditEvent.builder()
                .eventType("TOKEN_ROTATED")
                .userId(userId)
                .tenantId(tenantId)
                .clientIp(clientIp)
                .success(true)
                .severity("MEDIUM")
                .message("Refresh Token轮换成功: " + tokenFamily)
                .metadata(Map.of(
                        "tokenFamily", tokenFamily,
                        "oldJti", oldJti,
                        "newJti", newJti))
                .build());
    }

    /**
     * 统一审计事件记录入口
     *
     * <p>所有公开审计方法最终都收敛到此方法：填充时间戳，将事件字段拍平为
     * 结构化日志上下文（{@code logData}），再以 {@code SECURITY_AUDIT: <eventType> | <message>}
     * 的固定格式写入日志。当前实现不做任何持久化或外部上报，仅作为审计日志落地点，
     * 后续如需接入独立审计存储（如审计库、消息队列），可在此处扩展而不影响各公开方法签名。
     *
     * @param event 已填充完毕的审计事件对象
     * @return 始终返回 {@link Mono#empty()}，代表日志写入已完成（同步操作，不会失败）
     */
    private Mono<Void> logAuditEvent(AuditEvent event) {
        return Mono.deferContextual(context -> {
            event.setTimestamp(LocalDateTime.now());

            Map<String, Object> logData = new HashMap<>(16);
            if (event.getMetadata() != null) {
                logData.putAll(event.getMetadata());
            }
            logData.put("eventType", event.getEventType());
            logData.put("timestamp", event.getTimestamp());
            logData.put("success", event.isSuccess());
            logData.put("severity", event.getSeverity());
            logData.put("traceId", TraceIdUtil.getTraceId(context));

            if (event.getUserId() != null) {
                logData.put("userId", event.getUserId());
            }
            if (event.getUsername() != null) {
                logData.put("username", event.getUsername());
            }
            if (event.getTenantId() != null) {
                logData.put("tenantId", event.getTenantId());
            }
            if (event.getClientIp() != null) {
                logData.put("clientIp", event.getClientIp());
            }
            if (event.getUserAgent() != null) {
                logData.put("userAgent", event.getUserAgent());
            }
            if (event.getDeviceId() != null) {
                logData.put("deviceId", event.getDeviceId());
            }
            if (event.getReason() != null) {
                logData.put("reason", event.getReason());
            }

            log.info("SECURITY_AUDIT: {} | {} | {}", event.getEventType(), event.getMessage(), logData);
            return Mono.empty();
        });
    }

    /**
     * 审计事件内部载体
     *
     * <p>用于在各公开方法与 {@link #logAuditEvent(AuditEvent)} 之间传递统一的事件字段，
     * 仅作为本类内部数据传输对象使用，不对外暴露；手写 Builder（而非 {@code @Builder}）
     * 是为了避免引入额外 Lombok 注解处理器依赖差异，保持与既有代码风格一致。
     */
    @lombok.Data
    private static class AuditEvent {
        private String eventType;
        private LocalDateTime timestamp;
        private Long userId;
        private String username;
        private String tenantId;
        private String clientIp;
        private String userAgent;
        private String deviceId;
        private boolean success;
        private String severity = "LOW";
        private String message;
        private String reason;
        private Map<String, String> metadata;

        public static AuditEventBuilder builder() {
            return new AuditEventBuilder();
        }

        public static class AuditEventBuilder {
            private String eventType;
            private LocalDateTime timestamp;
            private Long userId;
            private String username;
            private String tenantId;
            private String clientIp;
            private String userAgent;
            private String deviceId;
            private boolean success;
            private String severity = "LOW";
            private String message;
            private String reason;
            private Map<String, String> metadata;

            public AuditEventBuilder eventType(String eventType) {
                this.eventType = eventType;
                return this;
            }

            public AuditEventBuilder timestamp(LocalDateTime timestamp) {
                this.timestamp = timestamp;
                return this;
            }

            public AuditEventBuilder userId(Long userId) {
                this.userId = userId;
                return this;
            }

            public AuditEventBuilder username(String username) {
                this.username = username;
                return this;
            }

            public AuditEventBuilder tenantId(String tenantId) {
                this.tenantId = tenantId;
                return this;
            }

            public AuditEventBuilder clientIp(String clientIp) {
                this.clientIp = clientIp;
                return this;
            }

            public AuditEventBuilder userAgent(String userAgent) {
                this.userAgent = userAgent;
                return this;
            }

            public AuditEventBuilder deviceId(String deviceId) {
                this.deviceId = deviceId;
                return this;
            }

            public AuditEventBuilder success(boolean success) {
                this.success = success;
                return this;
            }

            public AuditEventBuilder severity(String severity) {
                this.severity = severity;
                return this;
            }

            public AuditEventBuilder message(String message) {
                this.message = message;
                return this;
            }

            public AuditEventBuilder reason(String reason) {
                this.reason = reason;
                return this;
            }

            public AuditEventBuilder metadata(Map<String, String> metadata) {
                this.metadata = metadata;
                return this;
            }

            public AuditEvent build() {
                AuditEvent event = new AuditEvent();
                event.eventType = this.eventType;
                event.timestamp = this.timestamp;
                event.userId = this.userId;
                event.username = this.username;
                event.tenantId = this.tenantId;
                event.clientIp = this.clientIp;
                event.userAgent = this.userAgent;
                event.deviceId = this.deviceId;
                event.success = this.success;
                event.severity = this.severity;
                event.message = this.message;
                event.reason = this.reason;
                event.metadata = this.metadata;
                return event;
            }
        }
    }
}
