package com.apex.scaffold.auth.domain.service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Mono;

/**
 * 暴力破解防护服务
 *
 * <p>防止暴力破解攻击的多层防护机制：</p>
 * <ul>
 *   <li>用户级别防护：单个用户5分钟内失败5次锁定30分钟</li>
 *   <li>IP级别防护：单个IP 5分钟内失败10次锁定1小时</li>
 *   <li>全局限流：总体登录失败率监控</li>
 *   <li>分布式攻击检测：多IP针对同一账户</li>
 * </ul>
 *
 * <p>Redis存储结构：</p>
 * <pre>
 * login:fail:user:{username} -> 失败次数 (TTL: 5分钟或锁定时长)
 * login:fail:ip:{ip} -> 失败次数 (TTL: 5分钟或锁定时长)
 * login:locked:user:{username} -> 锁定时间戳 (TTL: 30分钟)
 * login:locked:ip:{ip} -> 锁定时间戳 (TTL: 1小时)
 * login:fail:global:counter -> 全局失败计数器 (TTL: 1分钟)
 * login:distributed:attack:{username} -> IP列表 (TTL: 10分钟)
 * </pre>
 *
 * @author Apex Security Team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class BruteForceProtectionService {

    private static final String REDIS_PREFIX_USER_FAIL = "login:fail:user:";
    private static final String REDIS_PREFIX_IP_FAIL = "login:fail:ip:";
    private static final String REDIS_PREFIX_USER_LOCKED = "login:locked:user:";
    private static final String REDIS_PREFIX_IP_LOCKED = "login:locked:ip:";
    private static final String REDIS_GLOBAL_FAIL_COUNTER = "login:fail:global:counter";
    private static final String REDIS_DISTRIBUTED_ATTACK = "login:distributed:attack:";

    /**
     * 用户级别：5次失败，锁定30分钟
     */
    private static final int USER_MAX_ATTEMPTS = 5;

    private static final Duration USER_LOCK_DURATION = Duration.ofMinutes(30);
    private static final Duration USER_ATTEMPT_WINDOW = Duration.ofMinutes(5);

    /**
     * IP级别：10次失败，锁定1小时
     */
    private static final int IP_MAX_ATTEMPTS = 10;

    private static final Duration IP_LOCK_DURATION = Duration.ofHours(1);
    private static final Duration IP_ATTEMPT_WINDOW = Duration.ofMinutes(5);

    /**
     * 全局限流：每分钟失败100次触发告警
     */
    private static final int GLOBAL_FAIL_THRESHOLD = 100;

    /**
     * 分布式攻击：5个不同IP针对同一账户
     */
    private static final int DISTRIBUTED_ATTACK_IP_COUNT = 5;

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final UnifiedAuditService auditService;

    /**
     * 检查用户是否允许登录
     *
     * @param username 用户名
     * @param clientIp 客户端IP
     */
    public Mono<Void> checkLoginAllowed(String username, String clientIp) {
        return Mono.zip(isUserLocked(username), isIpLocked(clientIp)).flatMap(tuple -> {
            boolean userLocked = tuple.getT1();
            boolean ipLocked = tuple.getT2();

            if (userLocked) {
                log.warn("用户账户已锁定: username={}", username);

                // 记录安全事件
                return auditService
                        .logSecurityEvent(
                                "LOGIN_BLOCKED_USER_LOCKED",
                                null,
                                null,
                                clientIp,
                                "MEDIUM",
                                "用户尝试登录已锁定的账户",
                                new LinkedHashMap<>(Map.of("username", username, "reason", "account_locked")))
                        .then(Mono.error(new RuntimeException("账户已被锁定，请30分钟后重试或联系管理员")));
            }

            if (ipLocked) {
                log.warn("IP地址已锁定: ip={}", clientIp);

                return auditService
                        .logSecurityEvent(
                                "LOGIN_BLOCKED_IP_LOCKED",
                                null,
                                null,
                                clientIp,
                                "HIGH",
                                "被锁定的IP尝试登录",
                                new LinkedHashMap<>(Map.of("ip", clientIp, "reason", "ip_blocked")))
                        .then(Mono.error(new RuntimeException("您的IP地址已被锁定，请1小时后重试")));
            }

            return Mono.empty();
        });
    }

    /**
     * 记录登录失败
     *
     * @param username 用户名
     * @param clientIp 客户端IP
     */
    public Mono<Void> recordLoginFailure(String username, String clientIp) {
        log.info("记录登录失败: username={}, ip={}", username, clientIp);

        return Mono.when(
                        incrementUserFailures(username),
                        incrementIpFailures(clientIp),
                        incrementGlobalFailures(),
                        checkDistributedAttack(username, clientIp))
                .then(auditService.logAuthenticationEvent(
                        "LOGIN_FAILED", null, null, clientIp, false, "密码错误", Map.of("username", username)))
                .then();
    }

    /**
     * 记录登录成功（清除失败计数）
     *
     * @param username 用户名
     * @param clientIp 客户端IP
     */
    public Mono<Void> recordLoginSuccess(String username, String clientIp) {
        log.info("记录登录成功，清除失败计数: username={}, ip={}", username, clientIp);

        return Mono.when(
                        redisTemplate.delete(REDIS_PREFIX_USER_FAIL + username),
                        redisTemplate.delete(REDIS_PREFIX_IP_FAIL + clientIp),
                        redisTemplate.delete(REDIS_DISTRIBUTED_ATTACK + username))
                .then();
    }

    /**
     * 手动解锁用户
     *
     * @param username 用户名
     * @param operatorId 操作员ID
     */
    public Mono<Void> unlockUser(String username, String operatorId) {
        log.info("管理员解锁用户: username={}, operator={}", username, operatorId);

        return Mono.when(
                        redisTemplate.delete(REDIS_PREFIX_USER_LOCKED + username),
                        redisTemplate.delete(REDIS_PREFIX_USER_FAIL + username))
                .then(auditService.logSystemEvent(
                        "USER_UNLOCKED", operatorId, true, Map.of("username", username, "operator", operatorId)))
                .then();
    }

    /**
     * 手动解锁IP
     *
     * @param clientIp IP地址
     * @param operatorId 操作员ID
     */
    public Mono<Void> unlockIp(String clientIp, String operatorId) {
        log.info("管理员解锁IP: ip={}, operator={}", clientIp, operatorId);

        return Mono.when(
                        redisTemplate.delete(REDIS_PREFIX_IP_LOCKED + clientIp),
                        redisTemplate.delete(REDIS_PREFIX_IP_FAIL + clientIp))
                .then(auditService.logSystemEvent(
                        "IP_UNLOCKED", operatorId, true, Map.of("ip", clientIp, "operator", operatorId)))
                .then();
    }

    /**
     * 获取用户剩余失败次数
     *
     * @param username 用户名
     * @return 剩余尝试次数
     */
    public Mono<Integer> getRemainingAttempts(String username) {
        return redisTemplate
                .opsForValue()
                .get(REDIS_PREFIX_USER_FAIL + username)
                .map(Integer::parseInt)
                .map(attempts -> Math.max(0, USER_MAX_ATTEMPTS - attempts))
                .defaultIfEmpty(USER_MAX_ATTEMPTS);
    }

    /**
     * 检查用户是否已锁定
     *
     * @param username 用户名
     * @return true表示已锁定
     */
    private Mono<Boolean> isUserLocked(String username) {
        return redisTemplate.hasKey(REDIS_PREFIX_USER_LOCKED + username);
    }

    /**
     * 检查IP是否已锁定
     *
     * @param clientIp IP地址
     * @return true表示已锁定
     */
    private Mono<Boolean> isIpLocked(String clientIp) {
        return redisTemplate.hasKey(REDIS_PREFIX_IP_LOCKED + clientIp);
    }

    /**
     * 增加用户失败计数
     *
     * @param username 用户名
     */
    private Mono<Void> incrementUserFailures(String username) {
        String key = REDIS_PREFIX_USER_FAIL + username;

        return redisTemplate.opsForValue().increment(key).flatMap(count -> {
            if (count == 1) {
                // 首次失败，设置过期时间
                return redisTemplate.expire(key, USER_ATTEMPT_WINDOW).then();
            }

            if (count >= USER_MAX_ATTEMPTS) {
                // 达到阈值，锁定账户
                log.warn("用户登录失败次数过多，锁定账户: username={}, attempts={}", username, count);

                return lockUser(username);
            }

            return Mono.empty();
        });
    }

    /**
     * 增加IP失败计数
     *
     * @param clientIp IP地址
     */
    private Mono<Void> incrementIpFailures(String clientIp) {
        String key = REDIS_PREFIX_IP_FAIL + clientIp;

        return redisTemplate.opsForValue().increment(key).flatMap(count -> {
            if (count == 1) {
                return redisTemplate.expire(key, IP_ATTEMPT_WINDOW).then();
            }

            if (count >= IP_MAX_ATTEMPTS) {
                log.warn("IP登录失败次数过多，锁定IP: ip={}, attempts={}", clientIp, count);

                return lockIp(clientIp);
            }

            return Mono.empty();
        });
    }

    /**
     * 锁定用户
     *
     * @param username 用户名
     */
    private Mono<Void> lockUser(String username) {
        String key = REDIS_PREFIX_USER_LOCKED + username;
        String lockTime = Instant.now().toString();

        return redisTemplate
                .opsForValue()
                .set(key, lockTime, USER_LOCK_DURATION)
                .then(auditService.logSecurityEvent(
                        "USER_LOCKED",
                        null,
                        null,
                        null,
                        "HIGH",
                        "用户因登录失败次数过多被自动锁定",
                        new LinkedHashMap<>(Map.of(
                                "username", username,
                                "lockDuration", USER_LOCK_DURATION.toMinutes() + "分钟",
                                "maxAttempts", USER_MAX_ATTEMPTS))))
                .then();
    }

    /**
     * 锁定IP
     *
     * @param clientIp IP地址
     */
    private Mono<Void> lockIp(String clientIp) {
        String key = REDIS_PREFIX_IP_LOCKED + clientIp;
        String lockTime = Instant.now().toString();

        return redisTemplate
                .opsForValue()
                .set(key, lockTime, IP_LOCK_DURATION)
                .then(auditService.logSecurityEvent(
                        "IP_LOCKED",
                        null,
                        null,
                        clientIp,
                        "HIGH",
                        "IP因登录失败次数过多被自动锁定",
                        new LinkedHashMap<>(Map.of(
                                "ip", clientIp,
                                "lockDuration", IP_LOCK_DURATION.toMinutes() + "分钟",
                                "maxAttempts", IP_MAX_ATTEMPTS))))
                .then();
    }

    /**
     * 增加全局失败计数
     */
    private Mono<Void> incrementGlobalFailures() {
        return redisTemplate.opsForValue().increment(REDIS_GLOBAL_FAIL_COUNTER).flatMap(count -> {
            if (count == 1) {
                return redisTemplate
                        .expire(REDIS_GLOBAL_FAIL_COUNTER, Duration.ofMinutes(1))
                        .then();
            }

            if (count >= GLOBAL_FAIL_THRESHOLD) {
                log.error("全局登录失败率异常: count={}/min", count);

                return auditService
                        .logSecurityEvent(
                                "GLOBAL_BRUTE_FORCE_DETECTED",
                                null,
                                null,
                                null,
                                "CRITICAL",
                                "检测到大规模暴力破解攻击",
                                Map.of(
                                        "failureCount", count,
                                        "threshold", GLOBAL_FAIL_THRESHOLD,
                                        "window", "1分钟"))
                        .then();
            }

            return Mono.empty();
        });
    }

    /**
     * 检测分布式攻击（多IP针对同一账户）
     *
     * @param username 用户名
     * @param clientIp IP地址
     */
    private Mono<Void> checkDistributedAttack(String username, String clientIp) {
        String key = REDIS_DISTRIBUTED_ATTACK + username;

        return redisTemplate
                .opsForSet()
                .add(key, clientIp)
                .flatMap(added -> {
                    if (added == 1) {
                        // 首次添加，设置过期时间
                        return redisTemplate.expire(key, Duration.ofMinutes(10)).then();
                    }
                    return Mono.empty();
                })
                .then(redisTemplate.opsForSet().size(key))
                .flatMap(ipCount -> {
                    if (ipCount >= DISTRIBUTED_ATTACK_IP_COUNT) {
                        log.error("检测到分布式暴力破解攻击: username={}, ipCount={}", username, ipCount);

                        return redisTemplate
                                .opsForSet()
                                .members(key)
                                .collectList()
                                .flatMap(ipList -> auditService.logSecurityEvent(
                                        "DISTRIBUTED_BRUTE_FORCE_DETECTED",
                                        null,
                                        null,
                                        null,
                                        "CRITICAL",
                                        "检测到分布式暴力破解攻击",
                                        Map.of(
                                                "username", username,
                                                "ipCount", ipCount,
                                                "ipList", String.join(",", ipList))))
                                .then();
                    }

                    return Mono.empty();
                });
    }
}
