package com.apex.scaffold.authcenter.infrastructure.security;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 账户锁定机制服务
 *
 * 功能：
 * 1. 支持手动锁定/解锁账户
 * 2. 支持临时锁定（自动过期）
 * 3. 支持永久锁定（需手动解锁）
 * 4. 记录锁定原因和时间
 * @author Apex Team
 * @since 1.2.0
 */
@Slf4j
@Service
public class AccountLockService {

    private final ReactiveStringRedisTemplate redisTemplate;

    /**
     * Redis Key前缀
     */
    private static final String LOCK_PREFIX = "account:lock:";

    private static final String LOCK_REASON_PREFIX = "account:lock:reason:";

    /**
     * 默认临时锁定时长
     */
    private static final Duration DEFAULT_LOCK_DURATION = Duration.ofMinutes(15);

    public AccountLockService(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 检查账户是否被锁定
     *
     * @param userId 用户ID
     * @return 是否被锁定
     */
    public Mono<Boolean> isLocked(String userId) {
        return redisTemplate.hasKey(LOCK_PREFIX + userId);
    }

    /**
     * 临时锁定账户（自动过期）
     *
     * @param userId 用户ID
     * @param reason 锁定原因
     * @param duration 锁定时长
     * @return 锁定是否成功
     */
    public Mono<Boolean> lockTemporarily(String userId, String reason, Duration duration) {
        String lockKey = LOCK_PREFIX + userId;
        String reasonKey = LOCK_REASON_PREFIX + userId;
        long lockTimestamp = System.currentTimeMillis();

        return redisTemplate
                .opsForValue()
                .set(lockKey, String.valueOf(lockTimestamp), duration)
                .flatMap(success -> {
                    if (success) {
                        return redisTemplate
                                .opsForValue()
                                .set(reasonKey, reason, duration)
                                .doOnSuccess(v -> log.warn(
                                        "账户临时锁定: userId={}, reason={}, duration={}分钟",
                                        userId,
                                        reason,
                                        duration.toMinutes()));
                    }
                    return Mono.just(false);
                });
    }

    /**
     * 永久锁定账户（需手动解锁）
     *
     * @param userId 用户ID
     * @param reason 锁定原因
     * @return 锁定是否成功
     */
    public Mono<Boolean> lockPermanently(String userId, String reason) {
        String lockKey = LOCK_PREFIX + userId;
        String reasonKey = LOCK_REASON_PREFIX + userId;
        long lockTimestamp = System.currentTimeMillis();

        return redisTemplate
                .opsForValue()
                .set(lockKey, String.valueOf(lockTimestamp))
                .flatMap(success -> {
                    if (success) {
                        return redisTemplate
                                .opsForValue()
                                .set(reasonKey, reason)
                                .doOnSuccess(v -> log.warn("账户永久锁定: userId={}, reason={}", userId, reason));
                    }
                    return Mono.just(false);
                });
    }

    /**
     * 解锁账户
     *
     * @param userId 用户ID
     * @param operator 操作人
     * @return 解锁是否成功
     */
    public Mono<Boolean> unlock(String userId, String operator) {
        return Mono.zip(redisTemplate.delete(LOCK_PREFIX + userId), redisTemplate.delete(LOCK_REASON_PREFIX + userId))
                .map(tuple -> {
                    boolean success = tuple.getT1() > 0;
                    if (success) {
                        log.info("账户已解锁: userId={}, operator={}", userId, operator);
                    } else {
                        log.warn("账户解锁失败（账户未被锁定）: userId={}", userId);
                    }
                    return success;
                });
    }

    /**
     * 获取锁定原因
     *
     * @param userId 用户ID
     * @return 锁定原因，如果未锁定返回null
     */
    public Mono<String> getLockReason(String userId) {
        return redisTemplate.opsForValue().get(LOCK_REASON_PREFIX + userId);
    }

    /**
     * 获取锁定时间戳
     *
     * @param userId 用户ID
     * @return 锁定时间戳（毫秒），如果未锁定返回null
     */
    public Mono<Long> getLockTimestamp(String userId) {
        return redisTemplate.opsForValue().get(LOCK_PREFIX + userId).map(Long::parseLong);
    }

    /**
     * 获取剩余锁定时间（秒）
     *
     * @param userId 用户ID
     * @return 剩余锁定时间，如果未锁定返回0，如果永久锁定返回-1
     */
    public Mono<Long> getRemainingLockTime(String userId) {
        return redisTemplate
                .getExpire(LOCK_PREFIX + userId)
                .map(duration -> {
                    if (duration.isNegative()) {
                        // 永久锁定（没有TTL）
                        return -1L;
                    }
                    return duration.getSeconds();
                })
                .defaultIfEmpty(0L);
    }

    /**
     * 获取锁定信息
     *
     * @param userId 用户ID
     * @return 锁定信息
     */
    public Mono<LockInfo> getLockInfo(String userId) {
        return isLocked(userId).flatMap(locked -> {
            if (!locked) {
                return Mono.just(LockInfo.notLocked());
            }

            return Mono.zip(getLockReason(userId), getLockTimestamp(userId), getRemainingLockTime(userId))
                    .map(tuple -> LockInfo.builder()
                            .locked(true)
                            .reason(tuple.getT1())
                            .lockTimestamp(tuple.getT2())
                            .remainingSeconds(tuple.getT3())
                            .permanent(tuple.getT3() == -1)
                            .build());
        });
    }

    /**
     * 延长锁定时间
     *
     * @param userId 用户ID
     * @param additionalDuration 额外锁定时长
     * @return 是否成功
     */
    public Mono<Boolean> extendLockTime(String userId, Duration additionalDuration) {
        return isLocked(userId).flatMap(locked -> {
            if (!locked) {
                return Mono.just(false);
            }

            return getRemainingLockTime(userId).flatMap(remainingSeconds -> {
                if (remainingSeconds == -1) {
                    // 永久锁定，无需延长
                    return Mono.just(true);
                }

                Duration newDuration = Duration.ofSeconds(remainingSeconds).plus(additionalDuration);

                return redisTemplate.expire(LOCK_PREFIX + userId, newDuration).flatMap(success -> {
                    if (success) {
                        return redisTemplate
                                .expire(LOCK_REASON_PREFIX + userId, newDuration)
                                .doOnSuccess(v -> log.info(
                                        "账户锁定时间已延长: userId={}, 新增时长={}分钟", userId, additionalDuration.toMinutes()));
                    }
                    return Mono.just(false);
                });
            });
        });
    }

    /**
     * 锁定信息
     */
    @lombok.Data
    @lombok.Builder
    public static class LockInfo {
        private boolean locked; // 是否被锁定
        private String reason; // 锁定原因
        private Long lockTimestamp; // 锁定时间戳
        private Long remainingSeconds; // 剩余锁定时间（秒），-1表示永久
        private boolean permanent; // 是否永久锁定

        public static LockInfo notLocked() {
            return LockInfo.builder().locked(false).build();
        }
    }

    /**
     * 锁定原因枚举
     */
    public enum LockReason {
        /** 暴力破解攻击 */
        BRUTE_FORCE("暴力破解攻击"),
        /** 可疑活动 */
        SUSPICIOUS_ACTIVITY("可疑活动"),
        /** 管理员手动锁定 */
        MANUAL_LOCK("管理员手动锁定"),
        /** 违反安全策略 */
        POLICY_VIOLATION("违反安全策略"),
        /** 密码过期 */
        PASSWORD_EXPIRED("密码过期"),
        /** 多设备异常登录 */
        MULTIPLE_DEVICES("多设备异常登录");

        private final String description;

        LockReason(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
