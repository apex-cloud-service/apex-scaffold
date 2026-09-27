package com.apex.scaffold.application.service.impl;

import com.apex.scaffold.application.service.OAuth2ClientSecretRotationService;
import com.apex.scaffold.authcenter.infrastructure.audit.SecurityAuditService;
import com.apex.scaffold.domain.model.entity.OAuth2RegisteredClientEntity;
import com.apex.scaffold.domain.repository.OAuth2RegisteredClientRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * OAuth2 客户端密钥轮换应用服务实现。
 *
 * <p>基于 {@code OAuth2RegisteredClientRepository} 实现密钥轮换与校验：使用
 * {@code SecureRandom} 生成 32 字节 Base64Url 随机密钥，经 {@code PasswordEncoder}
 * 加密后落库，默认有效期 90 天、临期阈值 30 天；校验时支持旧密钥宽限期匹配，
 * 并通过 {@code SecurityAuditService} 记录密钥轮换、过期密钥使用及认证失败等安全审计事件。
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2ClientSecretRotationServiceImpl implements OAuth2ClientSecretRotationService {

    private final OAuth2RegisteredClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityAuditService auditService;
    private final SecureRandom secureRandom = new SecureRandom();

    private static final int DEFAULT_SECRET_LENGTH = 32;
    private static final long DEFAULT_EXPIRY_DAYS = 90;
    private static final long EXPIRING_SOON_THRESHOLD_DAYS = 30;

    /**
     * 轮换客户端密钥（默认有效期）
     *
     * <p>生成新的随机密钥并加密落库，有效期默认 90 天，同时记录密钥轮换审计日志。
     *
     * @param clientId 客户端 ID
     * @param operatorUserId 执行轮换的操作人用户 ID，可为 null 表示系统操作
     * @return 新生成的明文密钥（仅本次返回可见，落库为加密后密钥）；客户端不存在或为 public 客户端时 Mono 以 IllegalArgumentException 错误终止
     */
    @Override
    public Mono<String> rotateClientSecret(String clientId, Long operatorUserId) {
        Instant expiresAt = Instant.now().plusSeconds(DEFAULT_EXPIRY_DAYS * 24 * 60 * 60);
        return rotateClientSecretWithCustomExpiry(clientId, expiresAt, operatorUserId);
    }

    /**
     * 以自定义过期时间轮换客户端密钥
     *
     * <p>生成新的随机密钥并加密落库，过期时间由调用方指定，同时记录密钥轮换审计日志。
     *
     * @param clientId 客户端 ID
     * @param expiresAt 新密钥的过期时间
     * @param operatorUserId 执行轮换的操作人用户 ID，可为 null 表示系统操作
     * @return 新生成的明文密钥（仅本次返回可见，落库为加密后密钥）；客户端不存在或为 public 客户端时 Mono 以 IllegalArgumentException 错误终止
     */
    @Override
    public Mono<String> rotateClientSecretWithCustomExpiry(String clientId, Instant expiresAt, Long operatorUserId) {
        return clientRepository
                .findByClientId(clientId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Client not found: " + clientId)))
                .flatMap(client -> {
                    if (client.isPublicClient()) {
                        return Mono.error(
                                new IllegalArgumentException("Cannot rotate secret for public client: " + clientId));
                    }

                    String rawSecret = generateSecureSecret();
                    String encodedSecret = passwordEncoder.encode(rawSecret);

                    client.rotateSecret(encodedSecret, expiresAt);

                    return clientRepository
                            .save(client)
                            .flatMap(saved -> {
                                // 记录审计日志
                                Map<String, Object> details = new HashMap<>();
                                details.put("clientId", clientId);
                                details.put("clientName", saved.getClientName());
                                details.put("expiresAt", expiresAt);
                                details.put("rotationCount", saved.getSecretRotationCount());
                                details.put("operatorUserId", operatorUserId);
                                details.put("previousSecretRotatedAt", saved.getSecretRotatedAt());

                                return auditService
                                        .logClientSecretRotation(
                                                clientId,
                                                operatorUserId != null ? String.valueOf(operatorUserId) : null,
                                                null, // IP will be captured by request context
                                                details)
                                        .thenReturn(saved);
                            })
                            .doOnSuccess(saved -> log.info(
                                    "Rotated client secret for clientId={}, expiresAt={}, operatorUserId={}, rotationCount={}",
                                    clientId,
                                    expiresAt,
                                    operatorUserId,
                                    saved.getSecretRotationCount()))
                            .thenReturn(rawSecret);
                });
    }

    /**
     * 校验客户端密钥
     *
     * <p>校验规则：public 客户端无密钥，直接视为有效；当前密钥匹配返回 true；
     * 密钥已过期返回 false 并记录过期密钥使用审计事件；当前密钥不匹配但旧密钥
     * （轮换宽限期内）匹配返回 true 并记录旧密钥使用审计事件；均不匹配返回 false
     * 并记录认证失败审计事件；客户端不存在时返回 false。
     *
     * @param clientId 客户端 ID
     * @param clientSecret 待校验的明文密钥
     * @return 密钥是否有效
     */
    @Override
    public Mono<Boolean> validateClientSecret(String clientId, String clientSecret) {
        return clientRepository
                .findByClientId(clientId)
                .flatMap(client -> {
                    if (client.isPublicClient()) {
                        return Mono.just(true);
                    }

                    if (client.isSecretExpired()) {
                        log.warn("Client secret expired for clientId={}", clientId);

                        // 记录过期密钥使用尝试
                        Map<String, Object> details = new HashMap<>();
                        details.put("clientId", clientId);
                        details.put("reason", "SECRET_EXPIRED");
                        details.put("expiresAt", client.getClientSecretExpiresAt());

                        return auditService
                                .logSecurityEvent(
                                        "OAUTH2_CLIENT_SECRET_EXPIRED",
                                        null,
                                        clientId,
                                        null,
                                        "MEDIUM",
                                        false,
                                        details,
                                        "Client secret has expired")
                                .thenReturn(false);
                    }

                    boolean currentMatch = passwordEncoder.matches(clientSecret, client.getClientSecret());
                    if (currentMatch) {
                        return Mono.just(true);
                    }

                    if (client.getPreviousClientSecret() != null) {
                        boolean previousMatch = passwordEncoder.matches(clientSecret, client.getPreviousClientSecret());
                        if (previousMatch) {
                            log.info("Client authenticated with previous secret: clientId={}", clientId);

                            // 记录使用旧密钥认证
                            Map<String, Object> details = new HashMap<>();
                            details.put("clientId", clientId);
                            details.put("message", "Client used previous secret (within grace period)");
                            details.put("rotatedAt", client.getSecretRotatedAt());

                            return auditService
                                    .logSecurityEvent(
                                            "OAUTH2_CLIENT_OLD_SECRET_USED",
                                            null,
                                            clientId,
                                            null,
                                            "LOW",
                                            true,
                                            details,
                                            null)
                                    .thenReturn(true);
                        }
                    }

                    log.warn("Client secret validation failed for clientId={}", clientId);

                    // 记录认证失败
                    Map<String, Object> details = new HashMap<>();
                    details.put("clientId", clientId);
                    details.put("reason", "INVALID_SECRET");

                    return auditService
                            .logSecurityEvent(
                                    "OAUTH2_CLIENT_AUTH_FAILED",
                                    null,
                                    clientId,
                                    null,
                                    "HIGH",
                                    false,
                                    details,
                                    "Invalid client secret")
                            .thenReturn(false);
                })
                .defaultIfEmpty(false);
    }

    /**
     * 筛查密钥已过期的客户端
     *
     * <p>遍历全部客户端，过滤出密钥已过期的非 public 客户端。
     *
     * @return 密钥已过期的客户端 ID 流
     */
    @Override
    public Flux<String> findClientsWithExpiredSecrets() {
        return clientRepository
                .findAll(0, Integer.MAX_VALUE)
                .filter(client -> !client.isPublicClient())
                .filter(OAuth2RegisteredClientEntity::isSecretExpired)
                .map(OAuth2RegisteredClientEntity::getClientId)
                .doOnNext(clientId -> log.warn("Found client with expired secret: ", clientId));
    }

    /**
     * 筛查密钥即将过期的客户端
     *
     * <p>遍历全部客户端，过滤出密钥在临期阈值（默认 30 天）内即将过期的非 public 客户端。
     *
     * @return 密钥即将过期的客户端 ID 流
     */
    @Override
    public Flux<String> findClientsWithExpiringSoonSecrets() {
        return clientRepository
                .findAll(0, Integer.MAX_VALUE)
                .filter(client -> !client.isPublicClient())
                .filter(OAuth2RegisteredClientEntity::isSecretExpiringSoon)
                .map(OAuth2RegisteredClientEntity::getClientId)
                .doOnNext(clientId -> log.info("Found client with expiring soon secret: {}", clientId));
    }

    /**
     * 安排客户端密钥轮换计划
     *
     * <p>当前实现仅记录调度日志，不进行持久化调度，立即返回完成信号。
     *
     * @param clientId 客户端 ID
     * @param scheduledTime 计划执行轮换的时间
     * @return 完成信号
     */
    @Override
    public Mono<Void> scheduleSecretRotation(String clientId, Instant scheduledTime) {
        log.info("Scheduling secret rotation for clientId={} at {}", clientId, scheduledTime);
        return Mono.empty();
    }

    /**
     * 通知客户端密钥即将过期
     *
     * <p>查询指定客户端，当密钥处于临期状态时记录警告日志。
     * 当前实现仅记录日志，未对接外部通知渠道。
     *
     * @param clientId 客户端 ID
     * @return 完成信号
     */
    @Override
    public Mono<Void> notifyClientSecretExpiring(String clientId) {
        return clientRepository.findByClientId(clientId).flatMap(client -> {
            if (client.isSecretExpiringSoon()) {
                long daysUntilExpiry = (client.getClientSecretExpiresAt().getEpochSecond()
                                - Instant.now().getEpochSecond())
                        / (24 * 60 * 60);

                log.warn(
                        "Client secret expiring soon: clientId={}, daysUntilExpiry={}, expiresAt={}",
                        clientId,
                        daysUntilExpiry,
                        client.getClientSecretExpiresAt());
            }
            return Mono.empty();
        });
    }

    private String generateSecureSecret() {
        byte[] randomBytes = new byte[DEFAULT_SECRET_LENGTH];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
