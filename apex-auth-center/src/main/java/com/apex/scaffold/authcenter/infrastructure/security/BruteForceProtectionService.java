package com.apex.scaffold.authcenter.infrastructure.security;

import com.apex.scaffold.authcenter.domain.service.SecurityAuditService;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

/**
 * 暴力破解防护服务
 *
 * <p>防止恶意用户通过暴力尝试破解账户密码。
 *
 * <h2>防护策略</h2>
 * <ul>
 *   <li>基于IP地址的失败次数限制</li>
 *   <li>基于用户名的失败次数限制</li>
 *   <li>渐进式锁定时间：失败次数越多，锁定时间越长</li>
 *   <li>自动解锁：锁定时间到期后自动解除</li>
 * </ul>
 *
 * <h2>限制规则</h2>
 * <ul>
 *   <li>5次失败：锁定5分钟</li>
 *   <li>10次失败：锁定30分钟</li>
 *   <li>15次失败：锁定2小时</li>
 *   <li>20次以上：锁定24小时</li>
 * </ul>
 *
 * @author Apex Security Team
 * @since 2026-09-25
 */
@Slf4j
@RequiredArgsConstructor
public class BruteForceProtectionService {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final SecurityAuditService auditService;

    private static final String IP_ATTEMPT_PREFIX = "auth:bruteforce:ip:";
    private static final String USER_ATTEMPT_PREFIX = "auth:bruteforce:user:";
    private static final String IP_LOCK_PREFIX = "auth:lock:ip:";
    private static final String USER_LOCK_PREFIX = "auth:lock:user:";

    private static final int THRESHOLD_LEVEL_1 = 5; // 5次失败
    private static final int THRESHOLD_LEVEL_2 = 10; // 10次失败
    private static final int THRESHOLD_LEVEL_3 = 15; // 15次失败
    private static final int THRESHOLD_LEVEL_4 = 20; // 20次失败

    private static final Duration LOCKOUT_LEVEL_1 = Duration.ofMinutes(5); // 5分钟
    private static final Duration LOCKOUT_LEVEL_2 = Duration.ofMinutes(30); // 30分钟
    private static final Duration LOCKOUT_LEVEL_3 = Duration.ofHours(2); // 2小时
    private static final Duration LOCKOUT_LEVEL_4 = Duration.ofHours(24); // 24小时

    private static final Duration ATTEMPT_WINDOW = Duration.ofHours(1); // 尝试次数统计窗口

    /**
     * 检查IP是否被锁定
     *
     * @param clientIp 客户端IP
     * @return 如果被锁定则抛出异常
     */
    public Mono<Void> checkIpLock(String clientIp) {
        String lockKey = IP_LOCK_PREFIX + clientIp;

        return redisTemplate.hasKey(lockKey).flatMap(isLocked -> {
            if (Boolean.TRUE.equals(isLocked)) {
                return redisTemplate.getExpire(lockKey).flatMap(ttl -> {
                    long minutes = ttl != null ? ttl.toMinutes() : 0;
                    return Mono.error(new BusinessException(
                            ResultCode.FORBIDDEN, String.format("由于多次登录失败，该IP已被锁定，请%d分钟后重试", minutes)));
                });
            }
            return Mono.empty();
        });
    }

    /**
     * 检查用户是否被锁定
     *
     * @param username 用户名
     * @param tenantId 租户ID
     * @return 如果被锁定则抛出异常
     */
    public Mono<Void> checkUserLock(String username, String tenantId) {
        String lockKey = USER_LOCK_PREFIX + tenantId + ":" + username;

        return redisTemplate.hasKey(lockKey).flatMap(isLocked -> {
            if (Boolean.TRUE.equals(isLocked)) {
                return redisTemplate.getExpire(lockKey).flatMap(ttl -> {
                    long minutes = ttl != null ? ttl.toMinutes() : 0;
                    return Mono.error(new BusinessException(
                            ResultCode.FORBIDDEN, String.format("由于多次登录失败，该账户已被临时锁定，请%d分钟后重试", minutes)));
                });
            }
            return Mono.empty();
        });
    }

    /**
     * 记录登录失败并检查是否需要锁定
     *
     * @param username 用户名
     * @param tenantId 租户ID
     * @param clientIp 客户端IP
     * @return Mono<Void>
     */
    public Mono<Void> recordFailedAttempt(String username, String tenantId, String clientIp) {
        return Mono.zip(incrementAndCheckIp(clientIp), incrementAndCheckUser(username, tenantId))
                .then();
    }

    /**
     * 清除登录失败记录（登录成功时调用）
     *
     * @param username 用户名
     * @param tenantId 租户ID
     * @param clientIp 客户端IP
     * @return Mono<Void>
     */
    public Mono<Void> clearFailedAttempts(String username, String tenantId, String clientIp) {
        String ipAttemptKey = IP_ATTEMPT_PREFIX + clientIp;
        String userAttemptKey = USER_ATTEMPT_PREFIX + tenantId + ":" + username;

        return redisTemplate
                .delete(ipAttemptKey)
                .then(redisTemplate.delete(userAttemptKey))
                .then();
    }

    /**
     * 增加IP失败次数并检查是否需要锁定
     */
    private Mono<Void> incrementAndCheckIp(String clientIp) {
        String attemptKey = IP_ATTEMPT_PREFIX + clientIp;
        String lockKey = IP_LOCK_PREFIX + clientIp;

        return redisTemplate
                .opsForValue()
                .increment(attemptKey)
                .flatMap(count -> {
                    // 设置过期时间（首次创建时）
                    if (count == 1) {
                        return redisTemplate.expire(attemptKey, ATTEMPT_WINDOW).thenReturn(count);
                    }
                    return Mono.just(count);
                })
                .flatMap(count -> {
                    Duration lockoutDuration = getLockoutDuration(count.intValue());

                    if (lockoutDuration != null) {
                        // 锁定IP
                        return redisTemplate
                                .opsForValue()
                                .set(lockKey, String.valueOf(count), lockoutDuration)
                                .then(auditService.logBruteForceAttack(null, clientIp, count.intValue()))
                                .then(Mono.error(new BusinessException(
                                        ResultCode.FORBIDDEN,
                                        String.format("由于多次登录失败，该IP已被锁定%d分钟", lockoutDuration.toMinutes()))));
                    } else if (count >= THRESHOLD_LEVEL_1) {
                        // 记录警告但不锁定
                        return auditService.logBruteForceAttack(null, clientIp, count.intValue());
                    }

                    return Mono.empty();
                });
    }

    /**
     * 增加用户失败次数并检查是否需要锁定
     */
    private Mono<Void> incrementAndCheckUser(String username, String tenantId) {
        String attemptKey = USER_ATTEMPT_PREFIX + tenantId + ":" + username;
        String lockKey = USER_LOCK_PREFIX + tenantId + ":" + username;

        return redisTemplate
                .opsForValue()
                .increment(attemptKey)
                .flatMap(count -> {
                    // 设置过期时间（首次创建时）
                    if (count == 1) {
                        return redisTemplate.expire(attemptKey, ATTEMPT_WINDOW).thenReturn(count);
                    }
                    return Mono.just(count);
                })
                .flatMap(count -> {
                    Duration lockoutDuration = getLockoutDuration(count.intValue());

                    if (lockoutDuration != null) {
                        // 锁定用户
                        return redisTemplate
                                .opsForValue()
                                .set(lockKey, String.valueOf(count), lockoutDuration)
                                .then(auditService.logBruteForceAttack(username, null, count.intValue()))
                                .then(Mono.error(new BusinessException(
                                        ResultCode.FORBIDDEN,
                                        String.format("由于多次登录失败，该账户已被临时锁定%d分钟", lockoutDuration.toMinutes()))));
                    } else if (count >= THRESHOLD_LEVEL_1) {
                        // 记录警告但不锁定
                        return auditService.logBruteForceAttack(username, null, count.intValue());
                    }

                    return Mono.empty();
                });
    }

    /**
     * 根据失败次数获取锁定时长
     *
     * @param failureCount 失败次数
     * @return 锁定时长，如果不需要锁定则返回null
     */
    private Duration getLockoutDuration(int failureCount) {
        if (failureCount >= THRESHOLD_LEVEL_4) {
            return LOCKOUT_LEVEL_4;
        } else if (failureCount >= THRESHOLD_LEVEL_3) {
            return LOCKOUT_LEVEL_3;
        } else if (failureCount >= THRESHOLD_LEVEL_2) {
            return LOCKOUT_LEVEL_2;
        } else if (failureCount >= THRESHOLD_LEVEL_1) {
            return LOCKOUT_LEVEL_1;
        }
        return null;
    }

    /**
     * 获取IP失败次数（用于监控）
     *
     * @param clientIp 客户端IP
     * @return 失败次数
     */
    public Mono<Integer> getIpFailureCount(String clientIp) {
        String attemptKey = IP_ATTEMPT_PREFIX + clientIp;
        return redisTemplate
                .opsForValue()
                .get(attemptKey)
                .map(Integer::parseInt)
                .defaultIfEmpty(0);
    }

    /**
     * 获取用户失败次数（用于监控）
     *
     * @param username 用户名
     * @param tenantId 租户ID
     * @return 失败次数
     */
    public Mono<Integer> getUserFailureCount(String username, String tenantId) {
        String attemptKey = USER_ATTEMPT_PREFIX + tenantId + ":" + username;
        return redisTemplate
                .opsForValue()
                .get(attemptKey)
                .map(Integer::parseInt)
                .defaultIfEmpty(0);
    }
}
