package com.apex.scaffold.authcenter.domain.service;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * JWT密钥轮换服务
 *
 * <p>实现JWT签名密钥的自动轮换机制，提高系统安全性：</p>
 * <ul>
 *   <li>支持RSA-2048密钥对生成和轮换</li>
 *   <li>密钥轮换周期：30天（可配置）</li>
 *   <li>密钥宽限期：7天（旧密钥在宽限期内仍可验证JWT）</li>
 *   <li>使用Redis存储密钥对，支持分布式环境</li>
 *   <li>支持立即轮换（紧急响应密钥泄露）</li>
 * </ul>
 *
 * <p>密钥存储结构：</p>
 * <pre>
 * jwt:keys:current -> keyId (当前活跃密钥ID)
 * jwt:key:{keyId}:private -> Base64编码的私钥
 * jwt:key:{keyId}:public -> Base64编码的公钥
 * jwt:key:{keyId}:created -> 创建时间戳
 * jwt:keys:all -> Set(所有可用密钥ID，包括宽限期内的旧密钥)
 * </pre>
 *
 * @author Apex Security Team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class JwtKeyRotationService {

    private static final String REDIS_KEY_CURRENT = "jwt:keys:current";
    private static final String REDIS_KEY_ALL = "jwt:keys:all";
    private static final String REDIS_KEY_PREFIX = "jwt:key:";

    /**
     * 密钥轮换周期：30天
     */
    private static final long ROTATION_PERIOD_DAYS = 30;

    /**
     * 密钥宽限期：7天（旧密钥在宽限期内仍可用于验证）
     */
    private static final long GRACE_PERIOD_DAYS = 7;

    /**
     * RSA密钥长度
     */
    private static final int KEY_SIZE = 2048;

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    /**
     * 初始化密钥对（系统启动时调用）
     * 如果Redis中没有密钥，则生成初始密钥对
     *
     * @return 初始化结果
     */
    public Mono<String> initializeKeys() {
        return getCurrentKeyId()
                .switchIfEmpty(Mono.defer(() -> {
                    log.info("检测到系统中没有JWT密钥，开始生成初始密钥对");
                    return rotateKeys().thenReturn("初始密钥对生成成功");
                }))
                .map(keyId -> "JWT密钥已就绪: keyId=" + keyId);
    }

    /**
     * 获取当前活跃的密钥ID
     *
     * @return 当前密钥ID，如果不存在则返回empty
     */
    public Mono<String> getCurrentKeyId() {
        return redisTemplate.opsForValue().get(REDIS_KEY_CURRENT);
    }

    /**
     * 获取当前活跃的私钥（用于签名JWT）
     *
     * @return 私钥对象
     */
    public Mono<PrivateKey> getCurrentPrivateKey() {
        return getCurrentKeyId()
                .flatMap(this::getPrivateKey)
                .switchIfEmpty(Mono.error(new IllegalStateException("JWT密钥未初始化")));
    }

    /**
     * 获取当前活跃的公钥（用于验证JWT）
     *
     * @return 公钥对象
     */
    public Mono<PublicKey> getCurrentPublicKey() {
        return getCurrentKeyId()
                .flatMap(this::getPublicKey)
                .switchIfEmpty(Mono.error(new IllegalStateException("JWT密钥未初始化")));
    }

    /**
     * 根据密钥ID获取公钥（用于验证旧JWT）
     *
     * @param keyId 密钥ID
     * @return 公钥对象
     */
    public Mono<PublicKey> getPublicKey(String keyId) {
        String redisKey = REDIS_KEY_PREFIX + keyId + ":public";
        return redisTemplate.opsForValue().get(redisKey).flatMap(encodedKey -> {
            try {
                byte[] keyBytes = Base64.getDecoder().decode(encodedKey);
                java.security.spec.X509EncodedKeySpec spec = new java.security.spec.X509EncodedKeySpec(keyBytes);
                java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance("RSA");
                return Mono.just(keyFactory.generatePublic(spec));
            } catch (Exception e) {
                return Mono.error(new RuntimeException("解析公钥失败: keyId=" + keyId, e));
            }
        });
    }

    /**
     * 根据密钥ID获取私钥
     *
     * @param keyId 密钥ID
     * @return 私钥对象
     */
    private Mono<PrivateKey> getPrivateKey(String keyId) {
        String redisKey = REDIS_KEY_PREFIX + keyId + ":private";
        return redisTemplate.opsForValue().get(redisKey).flatMap(encodedKey -> {
            try {
                byte[] keyBytes = Base64.getDecoder().decode(encodedKey);
                java.security.spec.PKCS8EncodedKeySpec spec = new java.security.spec.PKCS8EncodedKeySpec(keyBytes);
                java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance("RSA");
                return Mono.just(keyFactory.generatePrivate(spec));
            } catch (Exception e) {
                return Mono.error(new RuntimeException("解析私钥失败: keyId=" + keyId, e));
            }
        });
    }

    /**
     * 执行密钥轮换
     *
     * <p>轮换步骤：</p>
     * <ol>
     *   <li>生成新的RSA-2048密钥对</li>
     *   <li>将新密钥存储到Redis</li>
     *   <li>更新当前密钥ID</li>
     *   <li>将新密钥ID添加到可用密钥集合</li>
     *   <li>清理过期的旧密钥（超过宽限期）</li>
     * </ol>
     *
     * @return 新密钥ID
     */
    public Mono<String> rotateKeys() {
        return Mono.fromCallable(() -> {
                    log.info("开始JWT密钥轮换");

                    // 1. 生成新密钥对
                    KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
                    keyGen.initialize(KEY_SIZE, new SecureRandom());
                    KeyPair keyPair = keyGen.generateKeyPair();

                    String newKeyId = "key-" + UUID.randomUUID().toString();
                    Instant now = Instant.now();

                    log.info("生成新密钥对成功: keyId={}, algorithm=RSA, keySize={}", newKeyId, KEY_SIZE);

                    return new Object[] {newKeyId, keyPair, now};
                })
                .flatMap(data -> {
                    String newKeyId = (String) data[0];
                    KeyPair keyPair = (KeyPair) data[1];
                    Instant now = (Instant) data[2];

                    // 2. 存储新密钥对到Redis
                    String privateKeyEncoded = Base64.getEncoder()
                            .encodeToString(keyPair.getPrivate().getEncoded());
                    String publicKeyEncoded = Base64.getEncoder()
                            .encodeToString(keyPair.getPublic().getEncoded());

                    return Mono.when(
                                    redisTemplate
                                            .opsForValue()
                                            .set(REDIS_KEY_PREFIX + newKeyId + ":private", privateKeyEncoded),
                                    redisTemplate
                                            .opsForValue()
                                            .set(REDIS_KEY_PREFIX + newKeyId + ":public", publicKeyEncoded),
                                    redisTemplate
                                            .opsForValue()
                                            .set(
                                                    REDIS_KEY_PREFIX + newKeyId + ":created",
                                                    String.valueOf(now.toEpochMilli())))
                            .then(Mono.just(newKeyId));
                })
                .flatMap(newKeyId -> {
                    // 3. 更新当前密钥ID
                    return redisTemplate
                            .opsForValue()
                            .set(REDIS_KEY_CURRENT, newKeyId)
                            .then(redisTemplate.opsForSet().add(REDIS_KEY_ALL, newKeyId))
                            .thenReturn(newKeyId);
                })
                .flatMap(newKeyId -> {
                    // 4. 清理过期的旧密钥
                    return cleanupExpiredKeys().thenReturn(newKeyId);
                })
                .doOnSuccess(newKeyId -> log.info("JWT密钥轮换成功: newKeyId={}", newKeyId))
                .doOnError(e -> log.error("JWT密钥轮换失败", e));
    }

    /**
     * 清理过期的旧密钥（超过宽限期）
     *
     * @return 清理结果
     */
    private Mono<Void> cleanupExpiredKeys() {
        Instant expirationTime = Instant.now().minus(ROTATION_PERIOD_DAYS + GRACE_PERIOD_DAYS, ChronoUnit.DAYS);

        return redisTemplate
                .opsForSet()
                .members(REDIS_KEY_ALL)
                .collectList()
                .flatMapMany(Flux::fromIterable)
                .flatMap(keyId -> {
                    String createdKey = REDIS_KEY_PREFIX + keyId + ":created";
                    return redisTemplate.opsForValue().get(createdKey).map(createdStr -> {
                        long createdMillis = Long.parseLong(createdStr);
                        Instant created = Instant.ofEpochMilli(createdMillis);
                        return new Object[] {keyId, created};
                    });
                })
                .filter(data -> {
                    Instant created = (Instant) data[1];
                    return created.isBefore(expirationTime);
                })
                .flatMap(data -> {
                    String keyId = (String) data[0];
                    log.info("删除过期JWT密钥: keyId={}", keyId);

                    return Mono.when(
                            redisTemplate.delete(REDIS_KEY_PREFIX + keyId + ":private"),
                            redisTemplate.delete(REDIS_KEY_PREFIX + keyId + ":public"),
                            redisTemplate.delete(REDIS_KEY_PREFIX + keyId + ":created"),
                            redisTemplate.opsForSet().remove(REDIS_KEY_ALL, keyId));
                })
                .then();
    }

    /**
     * 定时任务：每天凌晨2点检查是否需要轮换密钥
     *
     * <p>轮换条件：当前密钥的创建时间超过30天
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduledKeyRotation() {
        log.info("执行定时JWT密钥轮换检查");

        getCurrentKeyId()
                .flatMap(keyId -> {
                    String createdKey = REDIS_KEY_PREFIX + keyId + ":created";
                    return redisTemplate.opsForValue().get(createdKey).map(createdStr -> {
                        long createdMillis = Long.parseLong(createdStr);
                        Instant created = Instant.ofEpochMilli(createdMillis);
                        long daysSinceCreation = ChronoUnit.DAYS.between(created, Instant.now());
                        return daysSinceCreation >= ROTATION_PERIOD_DAYS;
                    });
                })
                .defaultIfEmpty(false)
                .flatMap(needsRotation -> {
                    if (needsRotation) {
                        log.info("检测到密钥已超过轮换周期，开始自动轮换");
                        return rotateKeys().then();
                    } else {
                        log.debug("当前密钥仍在有效期内，无需轮换");
                        return Mono.empty();
                    }
                })
                .subscribe(null, e -> log.error("定时密钥轮换失败", e));
    }

    /**
     * 获取所有可用的密钥ID（包括宽限期内的旧密钥）
     *
     * @return 密钥ID列表
     */
    public Mono<List<String>> getAllAvailableKeyIds() {
        return redisTemplate.opsForSet().members(REDIS_KEY_ALL).collectList();
    }

    /**
     * 立即强制轮换密钥（用于紧急响应密钥泄露）
     *
     * <p>与定时轮换的区别：</p>
     * <ul>
     *   <li>忽略轮换周期检查，立即执行</li>
     *   <li>立即清理所有旧密钥（不等待宽限期）</li>
     *   <li>记录安全事件日志</li>
     * </ul>
     *
     * @param reason 轮换原因（用于审计）
     * @return 新密钥ID
     */
    public Mono<String> forceRotateKeys(String reason) {
        log.warn("执行紧急密钥轮换: reason={}", reason);

        return rotateKeys()
                .flatMap(newKeyId -> {
                    // 立即清理所有旧密钥（不等待宽限期）
                    return redisTemplate
                            .opsForSet()
                            .members(REDIS_KEY_ALL)
                            .filter(keyId -> !keyId.equals(newKeyId))
                            .flatMap(oldKeyId -> {
                                log.warn("紧急删除旧密钥: keyId={}, reason={}", oldKeyId, reason);
                                return Mono.when(
                                        redisTemplate.delete(REDIS_KEY_PREFIX + oldKeyId + ":private"),
                                        redisTemplate.delete(REDIS_KEY_PREFIX + oldKeyId + ":public"),
                                        redisTemplate.delete(REDIS_KEY_PREFIX + oldKeyId + ":created"),
                                        redisTemplate.opsForSet().remove(REDIS_KEY_ALL, oldKeyId));
                            })
                            .then(Mono.just(newKeyId));
                })
                .doOnSuccess(newKeyId -> log.warn("紧急密钥轮换完成: newKeyId={}, reason={}", newKeyId, reason));
    }
}
