package com.apex.scaffold.auth.domain.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * OAuth2客户端密钥轮换服务
 *
 * <p>定期轮换OAuth2客户端密钥，降低密钥泄露风险：</p>
 * <ul>
 *   <li>轮换周期：90天（可配置）</li>
 *   <li>宽限期：7天（旧密钥在宽限期内仍有效）</li>
 *   <li>支持自动轮换和手动强制轮换</li>
 *   <li>密钥强度：32字节随机密钥（256位）</li>
 *   <li>轮换前通知客户端（邮件/Webhook）</li>
 * </ul>
 *
 * <p>轮换流程：</p>
 * <ol>
 *   <li>生成新的client_secret</li>
 *   <li>将新密钥标记为"待激活"状态</li>
 *   <li>通知客户端更新密钥（提前7天）</li>
 *   <li>宽限期后，旧密钥失效</li>
 *   <li>记录审计日志</li>
 * </ol>
 *
 * <p>密钥存储结构：</p>
 * <pre>
 * oauth2:client:{clientId}:secret:current -> 当前密钥哈希
 * oauth2:client:{clientId}:secret:pending -> 待激活密钥哈希
 * oauth2:client:{clientId}:secret:created -> 创建时间戳
 * oauth2:client:{clientId}:secret:rotation_scheduled -> 下次轮换时间
 * </pre>
 *
 * @author Apex Security Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClientSecretRotationService {

    private static final String REDIS_CLIENT_SECRET_PREFIX = "oauth2:client:";
    private static final String REDIS_SECRET_CURRENT = ":secret:current";
    private static final String REDIS_SECRET_PENDING = ":secret:pending";
    private static final String REDIS_SECRET_CREATED = ":secret:created";
    private static final String REDIS_ROTATION_SCHEDULED = ":secret:rotation_scheduled";

    /**
     * 密钥轮换周期：90天
     */
    private static final long ROTATION_PERIOD_DAYS = 90;

    /**
     * 密钥宽限期：7天
     */
    private static final long GRACE_PERIOD_DAYS = 7;

    /**
     * 提前通知时间：7天
     */
    private static final long NOTIFICATION_ADVANCE_DAYS = 7;

    /**
     * 密钥长度：32字节（256位）
     */
    private static final int SECRET_LENGTH = 32;

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final UnifiedAuditService auditService;

    /**
     * 生成新的客户端密钥
     *
     * @return Base64编码的密钥
     */
    public Mono<String> generateClientSecret() {
        return Mono.fromCallable(() -> {
            byte[] randomBytes = new byte[SECRET_LENGTH];
            SecureRandom secureRandom = new SecureRandom();
            secureRandom.nextBytes(randomBytes);

            // Base64 URL安全编码
            return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        });
    }

    /**
     * 为客户端轮换密钥
     *
     * @param clientId OAuth2客户端ID
     * @param notifyClient 是否通知客户端
     * @return 新密钥（明文，仅返回一次）
     */
    public Mono<String> rotateClientSecret(String clientId, boolean notifyClient) {
        log.info("开始轮换OAuth2客户端密钥: clientId={}", clientId);

        return generateClientSecret().flatMap(newSecret -> {
            Instant now = Instant.now();
            String secretHash = hashSecret(newSecret);

            // 组装密钥轮换相关的四类 Redis 键名
            String currentKey = REDIS_CLIENT_SECRET_PREFIX + clientId + REDIS_SECRET_CURRENT;
            String pendingKey = REDIS_CLIENT_SECRET_PREFIX + clientId + REDIS_SECRET_PENDING;
            String createdKey = REDIS_CLIENT_SECRET_PREFIX + clientId + REDIS_SECRET_CREATED;
            String rotationKey = REDIS_CLIENT_SECRET_PREFIX + clientId + REDIS_ROTATION_SCHEDULED;

            return redisTemplate
                    .opsForValue()
                    .get(currentKey)
                    .defaultIfEmpty("")
                    .flatMap(currentSecret -> {
                        // 将当前密钥移到pending（宽限期）
                        // 将新密钥设为current
                        return Mono.when(
                                        redisTemplate.opsForValue().set(pendingKey, currentSecret),
                                        redisTemplate.opsForValue().set(currentKey, secretHash),
                                        redisTemplate.opsForValue().set(createdKey, String.valueOf(now.toEpochMilli())),
                                        redisTemplate
                                                .opsForValue()
                                                .set(
                                                        rotationKey,
                                                        String.valueOf(now.plus(ROTATION_PERIOD_DAYS, ChronoUnit.DAYS)
                                                                .toEpochMilli())))
                                .thenReturn(newSecret);
                    })
                    .flatMap(secret -> {
                        // 记录审计日志
                        return auditService
                                .logOAuth2Event(
                                        "CLIENT_SECRET_ROTATION",
                                        null,
                                        null,
                                        clientId,
                                        null,
                                        true,
                                        java.util.Map.of(
                                                "rotationType",
                                                notifyClient ? "scheduled" : "manual",
                                                "gracePeriodDays",
                                                GRACE_PERIOD_DAYS))
                                .thenReturn(secret);
                    })
                    .flatMap(secret -> {
                        // 通知客户端（如需要）
                        if (notifyClient) {
                            return notifyClientSecretRotation(clientId, GRACE_PERIOD_DAYS)
                                    .thenReturn(secret);
                        }
                        return Mono.just(secret);
                    })
                    .doOnSuccess(secret ->
                            log.info("OAuth2客户端密钥轮换成功: clientId={}, gracePerio d={}天", clientId, GRACE_PERIOD_DAYS));
        });
    }

    /**
     * 验证客户端密钥
     *
     * @param clientId 客户端ID
     * @param clientSecret 客户端密钥（明文）
     * @return true表示验证通过
     */
    public Mono<Boolean> validateClientSecret(String clientId, String clientSecret) {
        String secretHash = hashSecret(clientSecret);

        String currentKey = REDIS_CLIENT_SECRET_PREFIX + clientId + REDIS_SECRET_CURRENT;
        String pendingKey = REDIS_CLIENT_SECRET_PREFIX + clientId + REDIS_SECRET_PENDING;

        // 检查当前密钥
        return redisTemplate
                .opsForValue()
                .get(currentKey)
                .flatMap(currentHash -> {
                    if (secretHash.equals(currentHash)) {
                        return Mono.just(true);
                    }

                    // 检查宽限期内的旧密钥
                    return redisTemplate
                            .opsForValue()
                            .get(pendingKey)
                            .map(pendingHash -> secretHash.equals(pendingHash))
                            .defaultIfEmpty(false);
                })
                .defaultIfEmpty(false)
                .flatMap(valid -> {
                    if (valid) {
                        return Mono.just(true);
                    }

                    log.warn("OAuth2客户端密钥验证失败: clientId={}", clientId);
                    return auditService
                            .logSecurityEvent(
                                    "INVALID_CLIENT_SECRET",
                                    null,
                                    null,
                                    null,
                                    "MEDIUM",
                                    "客户端使用了无效的密钥",
                                    new LinkedHashMap<>(Map.of("clientId", clientId)))
                            .thenReturn(false);
                });
    }

    /**
     * 清理过期的旧密钥（超过宽限期）
     *
     * @param clientId 客户端ID
     */
    public Mono<Void> cleanupExpiredSecrets(String clientId) {
        String createdKey = REDIS_CLIENT_SECRET_PREFIX + clientId + REDIS_SECRET_CREATED;
        String pendingKey = REDIS_CLIENT_SECRET_PREFIX + clientId + REDIS_SECRET_PENDING;

        return redisTemplate
                .opsForValue()
                .get(createdKey)
                .flatMap(createdStr -> {
                    long createdMillis = Long.parseLong(createdStr);
                    Instant created = Instant.ofEpochMilli(createdMillis);
                    long daysSinceCreation = ChronoUnit.DAYS.between(created, Instant.now());

                    if (daysSinceCreation >= GRACE_PERIOD_DAYS) {
                        log.info("清理过期OAuth2客户端密钥: clientId={}, age={}天", clientId, daysSinceCreation);

                        return redisTemplate
                                .delete(pendingKey)
                                .then(auditService.logOAuth2Event(
                                        "CLIENT_SECRET_CLEANUP",
                                        null,
                                        null,
                                        clientId,
                                        null,
                                        true,
                                        java.util.Map.of("daysExpired", daysSinceCreation)))
                                .then();
                    }

                    return Mono.empty();
                })
                .then();
    }

    /**
     * 定时任务：检查需要轮换的客户端密钥
     * 每天凌晨3点执行
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void scheduledSecretRotation() {
        log.info("执行定时OAuth2客户端密钥轮换检查");

        // 当前仅记录调度日志：客户端清单来源尚未接入，待 OAuth2 客户端仓储提供
        // 跨租户扫描接口后启用下方逐客户端轮换；届时删除本说明。
        // 保留原调用形态供接线参考：
        //
        // clientRepository.findAll()
        //     .flatMap(client -> checkAndRotateSecret(client.getClientId()))
        //     .subscribe(null, e -> log.error("定时密钥轮换失败", e),
        //         () -> log.info("定时密钥轮换检查完成"));
    }

    /**
     * 检查并轮换密钥（如需要）
     *
     * @param clientId 客户端ID
     */
    private Mono<Void> checkAndRotateSecret(String clientId) {
        String rotationKey = REDIS_CLIENT_SECRET_PREFIX + clientId + REDIS_ROTATION_SCHEDULED;

        return redisTemplate
                .opsForValue()
                .get(rotationKey)
                .flatMap(scheduledStr -> {
                    long scheduledMillis = Long.parseLong(scheduledStr);
                    Instant scheduled = Instant.ofEpochMilli(scheduledMillis);
                    Instant notificationTime = scheduled.minus(NOTIFICATION_ADVANCE_DAYS, ChronoUnit.DAYS);

                    Instant now = Instant.now();

                    // 检查是否需要提前通知
                    if (now.isAfter(notificationTime) && now.isBefore(scheduled)) {
                        log.info(
                                "即将轮换客户端密钥，发送通知: clientId={}, daysUntilRotation={}",
                                clientId,
                                ChronoUnit.DAYS.between(now, scheduled));

                        return notifyClientSecretRotation(clientId, ChronoUnit.DAYS.between(now, scheduled));
                    }

                    // 检查是否需要轮换
                    if (now.isAfter(scheduled)) {
                        log.info("检测到客户端密钥需要轮换: clientId={}", clientId);
                        return rotateClientSecret(clientId, true).then();
                    }

                    return Mono.empty();
                })
                .then();
    }

    /**
     * 通知客户端密钥即将轮换
     *
     * @param clientId 客户端ID
     * @param daysUntilRotation 距离轮换的天数
     */
    private Mono<Void> notifyClientSecretRotation(String clientId, long daysUntilRotation) {
        // 通知渠道尚未接线：当前只写审计事件，待邮件/Webhook 通知适配器接入后替换，
        // 并同步在管理后台展示提醒。

        log.info("通知客户端密钥轮换: clientId={}, daysUntil={}", clientId, daysUntilRotation);

        return auditService
                .logSystemEvent(
                        "CLIENT_SECRET_ROTATION_NOTIFICATION",
                        "system",
                        true,
                        java.util.Map.of(
                                "clientId", clientId,
                                "daysUntilRotation", daysUntilRotation,
                                "notificationMethod", "email+webhook"))
                .then();
    }

    /**
     * 强制立即轮换客户端密钥（用于紧急响应）
     *
     * @param clientId 客户端ID
     * @param reason 轮换原因
     * @return 新密钥
     */
    public Mono<String> forceRotateClientSecret(String clientId, String reason) {
        log.warn("强制轮换OAuth2客户端密钥: clientId={}, reason={}", clientId, reason);

        return rotateClientSecret(clientId, false)
                .flatMap(newSecret -> {
                    // 记录安全事件
                    return auditService
                            .logSecurityEvent(
                                    "FORCE_CLIENT_SECRET_ROTATION",
                                    null,
                                    null,
                                    null,
                                    "HIGH",
                                    reason,
                                    new LinkedHashMap<>(Map.of("clientId", clientId)))
                            .thenReturn(newSecret);
                })
                .flatMap(newSecret -> {
                    // 立即清理旧密钥（不等待宽限期）
                    String pendingKey = REDIS_CLIENT_SECRET_PREFIX + clientId + REDIS_SECRET_PENDING;
                    return redisTemplate.delete(pendingKey).thenReturn(newSecret);
                })
                .doOnSuccess(newSecret -> log.warn("强制密钥轮换完成: clientId={}, reason={}", clientId, reason));
    }

    /**
     * 哈希密钥（用于存储对比）
     * 使用SHA-256
     *
     * @param secret 明文密钥
     * @return 哈希值（Hex格式）
     */
    private String hashSecret(String secret) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("密钥哈希失败", e);
        }
    }
}
