package com.apex.scaffold.auth.domain.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * SSO会话清理服务
 *
 * <p>负责清理过期的SSO会话和相关数据，包括：</p>
 * <ul>
 *   <li>SAML断言缓存（防重放）</li>
 *   <li>OIDC State参数（防CSRF）</li>
 *   <li>OIDC Nonce参数（防重放）</li>
 *   <li>授权码（OAuth2/OIDC）</li>
 *   <li>已撤销的Token黑名单（过期后可清理）</li>
 *   <li>登录失败记录（暴力破解防护）</li>
 *   <li>临时权限授予记录</li>
 * </ul>
 *
 * <p>清理策略：</p>
 * <ul>
 *   <li>定时任务：每小时执行一次全量清理</li>
 *   <li>惰性清理：访问时检查是否过期</li>
 *   <li>主动清理：用户登出时清理相关会话</li>
 * </ul>
 *
 * @author Apex Security Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("PMD.ClassNamingShouldBeCamelRule")
public class SSOSessionCleanupService {

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    /**
     * Redis键前缀
     */
    private static final String SAML_ASSERTION_PREFIX = "sso:saml:assertion:";

    private static final String OIDC_STATE_PREFIX = "sso:oidc:state:";
    private static final String OIDC_NONCE_PREFIX = "sso:oidc:nonce:";
    private static final String AUTH_CODE_PREFIX = "oauth2:auth_code:";
    private static final String TOKEN_BLACKLIST_PREFIX = "auth:token:blacklist:";
    private static final String LOGIN_FAILURE_PREFIX = "auth:login:failure:";
    private static final String TEMP_PERMISSION_PREFIX = "permission:temp:";

    /**
     * 定时清理任务 - 每小时执行一次
     *
     * cron表达式: 0 0 * * * ? (每小时整点执行)
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void scheduledCleanup() {
        log.info("开始执行SSO会话定时清理任务");

        Instant startTime = Instant.now();

        cleanupExpiredSessions()
                .doOnSuccess(count -> {
                    Duration elapsed = Duration.between(startTime, Instant.now());
                    log.info("SSO会话清理完成: 清理数量={}, 耗时={}ms", count, elapsed.toMillis());
                })
                .doOnError(error -> {
                    log.error("SSO会话清理失败: error={}", error.getMessage(), error);
                })
                .subscribe();
    }

    /**
     * 清理过期会话
     *
     * @return 清理的会话数量
     */
    public Mono<Long> cleanupExpiredSessions() {
        return Flux.merge(
                        cleanupSamlAssertions(),
                        cleanupOidcStates(),
                        cleanupOidcNonces(),
                        cleanupAuthorizationCodes(),
                        cleanupTokenBlacklist(),
                        cleanupLoginFailures(),
                        cleanupTemporaryPermissions())
                .reduce(0L, Long::sum)
                .doOnSuccess(total -> log.info("总共清理过期会话: {}", total));
    }

    /**
     * 清理SAML断言缓存
     *
     * SAML断言用于防重放攻击，通常有效期5-10分钟
     */
    private Mono<Long> cleanupSamlAssertions() {
        return cleanupByKeyPattern(SAML_ASSERTION_PREFIX + "*").doOnSuccess(count -> {
            if (count > 0) {
                log.info("清理过期SAML断言: {}", count);
            }
        });
    }

    /**
     * 清理OIDC State参数
     *
     * State用于防CSRF攻击，通常有效期5-10分钟
     */
    private Mono<Long> cleanupOidcStates() {
        return cleanupByKeyPattern(OIDC_STATE_PREFIX + "*").doOnSuccess(count -> {
            if (count > 0) {
                log.info("清理过期OIDC State: {}", count);
            }
        });
    }

    /**
     * 清理OIDC Nonce参数
     *
     * Nonce用于防重放攻击，通常有效期5-10分钟
     */
    private Mono<Long> cleanupOidcNonces() {
        return cleanupByKeyPattern(OIDC_NONCE_PREFIX + "*").doOnSuccess(count -> {
            if (count > 0) {
                log.info("清理过期OIDC Nonce: {}", count);
            }
        });
    }

    /**
     * 清理OAuth2授权码
     *
     * 授权码通常有效期5分钟，使用后立即失效
     */
    private Mono<Long> cleanupAuthorizationCodes() {
        return cleanupByKeyPattern(AUTH_CODE_PREFIX + "*").doOnSuccess(count -> {
            if (count > 0) {
                log.info("清理过期授权码: {}", count);
            }
        });
    }

    /**
     * 清理Token黑名单
     *
     * Token撤销后会加入黑名单，过期后可以清理
     */
    private Mono<Long> cleanupTokenBlacklist() {
        return cleanupByKeyPattern(TOKEN_BLACKLIST_PREFIX + "*").doOnSuccess(count -> {
            if (count > 0) {
                log.info("清理过期Token黑名单: {}", count);
            }
        });
    }

    /**
     * 清理登录失败记录
     *
     * 登录失败记录用于暴力破解防护，通常保留1小时
     */
    private Mono<Long> cleanupLoginFailures() {
        return cleanupByKeyPattern(LOGIN_FAILURE_PREFIX + "*").doOnSuccess(count -> {
            if (count > 0) {
                log.info("清理过期登录失败记录: {}", count);
            }
        });
    }

    /**
     * 清理临时权限
     *
     * 临时权限有明确的过期时间，过期后自动清理
     */
    private Mono<Long> cleanupTemporaryPermissions() {
        return cleanupByKeyPattern(TEMP_PERMISSION_PREFIX + "*").doOnSuccess(count -> {
            if (count > 0) {
                log.info("清理过期临时权限: {}", count);
            }
        });
    }

    /**
     * 根据键模式清理过期数据
     *
     * <p>注意：此方法依赖Redis的TTL机制，只删除已过期的键</p>
     */
    private Mono<Long> cleanupByKeyPattern(String pattern) {
        return redisTemplate
                .keys(pattern)
                .flatMap(key -> redisTemplate
                        .getExpire(key)
                        .flatMap(ttl -> {
                            // TTL为-2表示键已过期但未被清理
                            // TTL为-1表示键没有设置过期时间（永久）
                            if (ttl != null && ttl.getSeconds() < 0 && ttl.getSeconds() != -1) {
                                return redisTemplate.delete(key);
                            }
                            return Mono.just(0L);
                        })
                        .onErrorResume(error -> {
                            log.warn("检查键过期时间失败: key={}, error={}", key, error.getMessage());
                            return Mono.just(0L);
                        }))
                .reduce(0L, Long::sum);
    }

    /**
     * 清理用户的所有会话
     *
     * <p>用于用户主动登出或强制下线场景</p>
     *
     * @param userId 用户ID
     * @return 清理的会话数量
     */
    public Mono<Long> cleanupUserSessions(String userId) {
        log.info("清理用户所有会话: userId={}", userId);

        // 构建用户相关的键模式
        String userTokenPattern = "auth:token:*:" + userId + ":*";
        String userSessionPattern = "sso:session:" + userId + ":*";

        return Flux.merge(cleanupByKeyPattern(userTokenPattern), cleanupByKeyPattern(userSessionPattern))
                .reduce(0L, Long::sum)
                .doOnSuccess(count -> {
                    log.info("用户会话清理完成: userId={}, count={}", userId, count);
                });
    }

    /**
     * 清理特定设备的会话
     *
     * <p>用于设备解绑或强制下线场景</p>
     *
     * @param userId 用户ID
     * @param deviceId 设备ID
     * @return 清理的会话数量
     */
    public Mono<Long> cleanupDeviceSession(String userId, String deviceId) {
        log.info("清理设备会话: userId=, deviceId={}", userId, deviceId);

        String deviceTokenPattern = "auth:token:*:" + userId + ":" + deviceId;

        return cleanupByKeyPattern(deviceTokenPattern).doOnSuccess(count -> {
            log.info("设备会话清理完成: userId={}, deviceId={}, count={}", userId, deviceId, count);
        });
    }

    /**
     * 批量清理用户会话
     *
     * <p>用于批量下线用户场景（如安全事件响应）</p>
     *
     * @param userIds 用户ID集合
     * @return 清理的会话数量
     */
    public Mono<Long> cleanupUserSessionsBatch(Set<String> userIds) {
        log.info("批量清理用户会话: userCount={}", userIds.size());

        return Flux.fromIterable(userIds)
                .flatMap(this::cleanupUserSessions)
                .reduce(0L, Long::sum)
                .doOnSuccess(total -> {
                    log.info("批量会话清理完成: userCount={}, totalSessions={}", userIds.size(), total);
                });
    }

    /**
     * 清理租户的所有会话
     *
     * <p>用于租户下线或数据清理场景</p>
     *
     * @param tenantId 租户ID
     * @return 清理的会话数量
     */
    public Mono<Long> cleanupTenantSessions(String tenantId) {
        log.info("清理租户所有会话: tenantId={}", tenantId);

        String tenantTokenPattern = "auth:token:*:*:" + tenantId + ":*";

        return cleanupByKeyPattern(tenantTokenPattern).doOnSuccess(count -> {
            log.info("租户会话清理完成: tenantId={}, count={}", tenantId, count);
        });
    }

    /**
     * 获取会话清理统计信息
     *
     * @return 各类会话存储待清理数量的统计
     */
    public Mono<CleanupStatistics> getCleanupStatistics() {
        return Mono.zip(
                        countKeysByPattern(SAML_ASSERTION_PREFIX + "*"),
                        countKeysByPattern(OIDC_STATE_PREFIX + "*"),
                        countKeysByPattern(OIDC_NONCE_PREFIX + "*"),
                        countKeysByPattern(AUTH_CODE_PREFIX + "*"),
                        countKeysByPattern(TOKEN_BLACKLIST_PREFIX + "*"),
                        countKeysByPattern(LOGIN_FAILURE_PREFIX + "*"),
                        countKeysByPattern(TEMP_PERMISSION_PREFIX + "*"))
                .map(tuple -> new CleanupStatistics(
                        tuple.getT1(),
                        tuple.getT2(),
                        tuple.getT3(),
                        tuple.getT4(),
                        tuple.getT5(),
                        tuple.getT6(),
                        tuple.getT7()));
    }

    /**
     * 统计匹配的键数量
     */
    private Mono<Long> countKeysByPattern(String pattern) {
        return redisTemplate.keys(pattern).count();
    }

    /**
     * 清理统计信息
     */
    @Getter
    @RequiredArgsConstructor
    public static class CleanupStatistics {
        private final Long samlAssertions;
        private final Long oidcStates;
        private final Long oidcNonces;
        private final Long authCodes;
        private final Long tokenBlacklist;
        private final Long loginFailures;
        private final Long tempPermissions;

        /**
         * 汇总各会话存储的待清理数量。
         *
         * @return 各类待清理项之和
         */
        public Long getTotal() {
            return samlAssertions
                    + oidcStates
                    + oidcNonces
                    + authCodes
                    + tokenBlacklist
                    + loginFailures
                    + tempPermissions;
        }

        @Override
        public String toString() {
            return String.format(
                    "CleanupStatistics{total=%d, saml=%d, oidcState=%d, oidcNonce=%d, authCode=%d, blacklist=%d, failures=%d, tempPerm=%d}",
                    getTotal(),
                    samlAssertions,
                    oidcStates,
                    oidcNonces,
                    authCodes,
                    tokenBlacklist,
                    loginFailures,
                    tempPermissions);
        }
    }
}
