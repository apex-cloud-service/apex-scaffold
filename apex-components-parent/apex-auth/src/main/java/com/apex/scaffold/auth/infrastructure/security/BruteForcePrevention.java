package com.apex.scaffold.auth.infrastructure.security;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 暴力破解防护服务
 *
 * 功能：
 * 1. 记录登录失败次数
 * 2. 超过阈值时触发临时锁定
 * 3. 支持基于用户名和IP的双重防护
 * 4. 自动解锁机制
 * @author Apex Team
 * @since 1.2.0
 */
@Slf4j
@Service
public class BruteForcePrevention {

    private final ReactiveStringRedisTemplate redisTemplate;

    /**
     * 最大失败尝试次数
     */
    private static final int MAX_ATTEMPTS = 5;

    /**
     * 账号锁定时长
     */
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(30);

    /**
     * 失败计数时间窗口
     */
    private static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(15);

    /**
     * 剩余次数告警阈值
     */
    private static final int WARNING_REMAINING_ATTEMPTS_THRESHOLD = 2;

    private static final String ATTEMPTS_BY_USERNAME_PREFIX = "brute_force:attempts:username:";
    private static final String ATTEMPTS_BY_IP_PREFIX = "brute_force:attempts:ip:";
    private static final String LOCKOUT_BY_USERNAME_PREFIX = "brute_force:lockout:username:";
    private static final String LOCKOUT_BY_IP_PREFIX = "brute_force:lockout:ip:";

    /**
     * 构造暴力破解防护服务
     *
     * @param redisTemplate Redis模板
     */
    public BruteForcePrevention(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 检查用户是否被锁定
     *
     * @param username 用户名
     * @param clientIp 客户端IP
     * @return 如果被锁定返回true，否则返回false
     */
    public Mono<Boolean> isBlocked(String username, String clientIp) {
        return Mono.zip(isUsernameLocked(username), isIpLocked(clientIp)).map(tuple -> tuple.getT1() || tuple.getT2());
    }

    /**
     * 记录登录失败
     *
     * @param username 用户名
     * @param clientIp 客户端IP
     * @return 剩余尝试次数，如果被锁定返回0
     */
    public Mono<Integer> recordLoginFailure(String username, String clientIp) {
        return Mono.zip(incrementAttempts(username, true), incrementAttempts(clientIp, false))
                .flatMap(tuple -> {
                    int usernameAttempts = tuple.getT1();
                    int ipAttempts = tuple.getT2();

                    if (usernameAttempts >= MAX_ATTEMPTS) {
                        return lockUsername(username)
                                .doOnSuccess(v -> log.warn(
                                        "用户 {} 因连续失败{}次被锁定{}分钟", username, MAX_ATTEMPTS, LOCKOUT_DURATION.toMinutes()))
                                .thenReturn(0);
                    }

                    if (ipAttempts >= MAX_ATTEMPTS) {
                        return lockIp(clientIp)
                                .doOnSuccess(v -> log.warn(
                                        "IP {} 因连续失败{}次被锁定{}分钟", clientIp, MAX_ATTEMPTS, LOCKOUT_DURATION.toMinutes()))
                                .thenReturn(0);
                    }

                    int remainingAttempts = MAX_ATTEMPTS - Math.max(usernameAttempts, ipAttempts);

                    if (remainingAttempts <= WARNING_REMAINING_ATTEMPTS_THRESHOLD) {
                        log.warn("用户 {} (IP: {}) 剩余登录尝试次数: {}", username, clientIp, remainingAttempts);
                    }

                    return Mono.just(remainingAttempts);
                });
    }

    /**
     * 记录登录成功，清除失败记录
     *
     * @param username 用户名
     * @param clientIp 客户端IP
     */
    public Mono<Void> recordLoginSuccess(String username, String clientIp) {
        return Mono.when(clearAttempts(username, true), clearAttempts(clientIp, false))
                .doOnSuccess(v -> log.debug("已清除用户 {} (IP: {}) 的登录失败记录", username, clientIp));
    }

    /**
     * 手动解锁用户
     *
     * @param username 用户名
     */
    public Mono<Void> unlockUsername(String username) {
        return Mono.when(redisTemplate.delete(LOCKOUT_BY_USERNAME_PREFIX + username), clearAttempts(username, true))
                .doOnSuccess(v -> log.info("已手动解锁用户: {}", username));
    }

    /**
     * 手动解锁IP
     *
     * @param clientIp 客户端IP
     */
    public Mono<Void> unlockIp(String clientIp) {
        return Mono.when(redisTemplate.delete(LOCKOUT_BY_IP_PREFIX + clientIp), clearAttempts(clientIp, false))
                .doOnSuccess(v -> log.info("已手动解锁IP: {}", clientIp));
    }

    /**
     * 获取剩余锁定时间（秒）
     *
     * @param username 用户名
     * @param clientIp 客户端IP
     * @return 剩余锁定时间，如果未锁定返回0
     */
    public Mono<Long> getRemainingLockoutTime(String username, String clientIp) {
        return Mono.zip(getUsernameLockoutTtl(username), getIpLockoutTtl(clientIp))
                .map(tuple -> Math.max(tuple.getT1(), tuple.getT2()));
    }

    // ========== 私有方法 ==========

    /**
     * 检查用户名是否被锁定
     */
    private Mono<Boolean> isUsernameLocked(String username) {
        return redisTemplate.hasKey(LOCKOUT_BY_USERNAME_PREFIX + username);
    }

    /**
     * 检查IP是否被锁定
     */
    private Mono<Boolean> isIpLocked(String clientIp) {
        return redisTemplate.hasKey(LOCKOUT_BY_IP_PREFIX + clientIp);
    }

    /**
     * 增加失败次数
     *
     * @param identifier 用户名或IP
     * @param isUsername 是否为用户名
     * @return 当前失败次数
     */
    private Mono<Integer> incrementAttempts(String identifier, boolean isUsername) {
        String key = (isUsername ? ATTEMPTS_BY_USERNAME_PREFIX : ATTEMPTS_BY_IP_PREFIX) + identifier;

        return redisTemplate.opsForValue().increment(key).flatMap(count -> {
            // 首次失败时设置过期时间
            if (count == 1) {
                return redisTemplate.expire(key, ATTEMPT_WINDOW).thenReturn(count.intValue());
            }
            return Mono.just(count.intValue());
        });
    }

    /**
     * 清除失败次数
     */
    private Mono<Boolean> clearAttempts(String identifier, boolean isUsername) {
        String key = (isUsername ? ATTEMPTS_BY_USERNAME_PREFIX : ATTEMPTS_BY_IP_PREFIX) + identifier;
        return redisTemplate.delete(key).map(count -> count > 0);
    }

    /**
     * 锁定用户名
     */
    private Mono<Boolean> lockUsername(String username) {
        String key = LOCKOUT_BY_USERNAME_PREFIX + username;
        return redisTemplate.opsForValue().set(key, String.valueOf(System.currentTimeMillis()), LOCKOUT_DURATION);
    }

    /**
     * 锁定IP
     */
    private Mono<Boolean> lockIp(String clientIp) {
        String key = LOCKOUT_BY_IP_PREFIX + clientIp;
        return redisTemplate.opsForValue().set(key, String.valueOf(System.currentTimeMillis()), LOCKOUT_DURATION);
    }

    /**
     * 获取用户名锁定剩余时间（秒）
     */
    private Mono<Long> getUsernameLockoutTtl(String username) {
        return redisTemplate
                .getExpire(LOCKOUT_BY_USERNAME_PREFIX + username)
                .map(duration -> Math.max(0, duration.getSeconds()));
    }

    /**
     * 获取IP锁定剩余时间（秒）
     */
    private Mono<Long> getIpLockoutTtl(String clientIp) {
        return redisTemplate
                .getExpire(LOCKOUT_BY_IP_PREFIX + clientIp)
                .map(duration -> Math.max(0, duration.getSeconds()));
    }

    /**
     * 获取当前失败次数
     *
     * @param username 用户名
     * @param clientIp 客户端IP
     * @return 失败次数
     */
    public Mono<Integer> getFailedAttempts(String username, String clientIp) {
        return Mono.zip(getAttempts(username, true), getAttempts(clientIp, false))
                .map(tuple -> Math.max(tuple.getT1(), tuple.getT2()));
    }

    /**
     * 获取失败次数
     */
    private Mono<Integer> getAttempts(String identifier, boolean isUsername) {
        String key = (isUsername ? ATTEMPTS_BY_USERNAME_PREFIX : ATTEMPTS_BY_IP_PREFIX) + identifier;
        return redisTemplate.opsForValue().get(key).map(Integer::parseInt).defaultIfEmpty(0);
    }
}
