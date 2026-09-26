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

    @Override
    public Mono<String> rotateClientSecret(String clientId, Long operatorUserId) {
        Instant expiresAt = Instant.now().plusSeconds(DEFAULT_EXPIRY_DAYS * 24 * 60 * 60);
        return rotateClientSecretWithCustomExpiry(clientId, expiresAt, operatorUserId);
    }

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

    @Override
    public Flux<String> findClientsWithExpiredSecrets() {
        return clientRepository
                .findAll(0, Integer.MAX_VALUE)
                .filter(client -> !client.isPublicClient())
                .filter(OAuth2RegisteredClientEntity::isSecretExpired)
                .map(OAuth2RegisteredClientEntity::getClientId)
                .doOnNext(clientId -> log.warn("Found client with expired secret: ", clientId));
    }

    @Override
    public Flux<String> findClientsWithExpiringSoonSecrets() {
        return clientRepository
                .findAll(0, Integer.MAX_VALUE)
                .filter(client -> !client.isPublicClient())
                .filter(OAuth2RegisteredClientEntity::isSecretExpiringSoon)
                .map(OAuth2RegisteredClientEntity::getClientId)
                .doOnNext(clientId -> log.info("Found client with expiring soon secret: {}", clientId));
    }

    @Override
    public Mono<Void> scheduleSecretRotation(String clientId, Instant scheduledTime) {
        log.info("Scheduling secret rotation for clientId={} at {}", clientId, scheduledTime);
        return Mono.empty();
    }

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
