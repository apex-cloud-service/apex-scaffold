package com.apex.scaffold.authcenter.domain.service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 实时安全监控服务
 *
 * <p>实时监控认证授权系统的安全指标：</p>
 * <ul>
 *   <li>登录失败率监控</li>
 *   <li>Token验证失败率监控</li>
 *   <li>异常IP活动监控</li>
 *   <li>暴力破解攻击检测</li>
 *   <li>账户锁定统计</li>
 *   <li>Token泄露风险评估</li>
 * </ul>
 *
 * <p>告警触发条件：</p>
 * <ul>
 *   <li>1分钟内登录失败率 > 30%</li>
 *   <li>1分钟内Token验证失败 > 100次</li>
 *   <li>单IP 1分钟内失败 > 20次</li>
 *   <li>检测到分布式暴力破解</li>
 *   <li>检测到Token重放攻击</li>
 * </ul>
 *
 * @author Apex Security Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityMonitoringService {

    /**
     * Redis键前缀
     */
    private static final String REDIS_METRIC_LOGIN_SUCCESS = "metric:login:success";

    private static final String REDIS_METRIC_LOGIN_FAIL = "metric:login:fail";
    private static final String REDIS_METRIC_TOKEN_VALIDATE_SUCCESS = "metric:token:validate:success";
    private static final String REDIS_METRIC_TOKEN_VALIDATE_FAIL = "metric:token:validate:fail";
    private static final String REDIS_METRIC_IP_ACTIVITY = "metric:ip:activity:";
    private static final String REDIS_METRIC_USER_LOCKED = "metric:user:locked";
    private static final String REDIS_METRIC_TOKEN_REPLAY = "metric:token:replay";

    /**
     * 登录失败率告警阈值(30%)
     */
    private static final double LOGIN_FAILURE_RATE_THRESHOLD = 0.30;

    private static final long TOKEN_VALIDATE_FAIL_THRESHOLD = 100;
    private static final long IP_ACTIVITY_THRESHOLD = 20;
    private static final long USER_LOCKED_THRESHOLD = 10;

    /**
     * 指标窗口期
     */
    private static final Duration METRIC_WINDOW = Duration.ofMinutes(1);

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final UnifiedAuditService auditService;

    /**
     * 内存计数器（用于快速统计，定期同步到Redis）
     */
    private final AtomicLong loginSuccessCounter = new AtomicLong(0);

    private final AtomicLong loginFailCounter = new AtomicLong(0);
    private final AtomicLong tokenValidateSuccessCounter = new AtomicLong(0);
    private final AtomicLong tokenValidateFailCounter = new AtomicLong(0);

    /**
     * 记录登录成功
     *
     * @param userId 登录成功的用户 ID
     * @param clientIp 客户端 IP 地址
     * @return 记录完成后的空 Mono 信号
     */
    public Mono<Void> recordLoginSuccess(String userId, String clientIp) {
        loginSuccessCounter.incrementAndGet();

        return redisTemplate
                .opsForValue()
                .increment(REDIS_METRIC_LOGIN_SUCCESS)
                .flatMap(count -> {
                    if (count == 1) {
                        return redisTemplate.expire(REDIS_METRIC_LOGIN_SUCCESS, METRIC_WINDOW);
                    }
                    return Mono.just(true);
                })
                .then(recordIpActivity(clientIp));
    }

    /**
     * 记录登录失败
     *
     * @param username 登录失败的用户名
     * @param clientIp 客户端 IP 地址
     * @return 记录并完成登录失败率检查后的空 Mono 信号
     */
    public Mono<Void> recordLoginFailure(String username, String clientIp) {
        loginFailCounter.incrementAndGet();

        return redisTemplate
                .opsForValue()
                .increment(REDIS_METRIC_LOGIN_FAIL)
                .flatMap(count -> {
                    if (count == 1) {
                        return redisTemplate.expire(REDIS_METRIC_LOGIN_FAIL, METRIC_WINDOW);
                    }
                    return Mono.just(true);
                })
                .then(recordIpActivity(clientIp))
                .then(checkLoginFailureRate());
    }

    /**
     * 记录Token验证成功
     *
     * @param tokenId 验证成功的 Token ID
     * @return 记录完成后的空 Mono 信号
     */
    public Mono<Void> recordTokenValidateSuccess(String tokenId) {
        tokenValidateSuccessCounter.incrementAndGet();

        return redisTemplate
                .opsForValue()
                .increment(REDIS_METRIC_TOKEN_VALIDATE_SUCCESS)
                .flatMap(count -> {
                    if (count == 1) {
                        return redisTemplate.expire(REDIS_METRIC_TOKEN_VALIDATE_SUCCESS, METRIC_WINDOW);
                    }
                    return Mono.just(true);
                })
                .then();
    }

    /**
     * 记录Token验证失败
     *
     * @param tokenId 验证失败的 Token ID
     * @param reason 验证失败原因
     * @return 记录完成后的空 Mono 信号
     */
    public Mono<Void> recordTokenValidateFail(String tokenId, String reason) {
        tokenValidateFailCounter.incrementAndGet();

        return redisTemplate
                .opsForValue()
                .increment(REDIS_METRIC_TOKEN_VALIDATE_FAIL)
                .flatMap(count -> {
                    if (count == 1) {
                        return redisTemplate
                                .expire(REDIS_METRIC_TOKEN_VALIDATE_FAIL, METRIC_WINDOW)
                                .then();
                    }

                    // 检查是否超过阈值
                    if (count >= TOKEN_VALIDATE_FAIL_THRESHOLD) {
                        log.error("Token验证失败率异常: count={}/min", count);

                        return auditService
                                .logSecurityEvent(
                                        "TOKEN_VALIDATE_FAIL_SPIKE",
                                        null,
                                        null,
                                        null,
                                        "HIGH",
                                        "Token验证失败率异常，可能存在Token泄露或攻击",
                                        Map.of(
                                                "failCount", count,
                                                "threshold", TOKEN_VALIDATE_FAIL_THRESHOLD,
                                                "window", "1分钟"))
                                .then();
                    }

                    return Mono.empty();
                });
    }

    /**
     * 记录用户锁定事件
     *
     * @param userId 被锁定的用户 ID
     * @param reason 锁定原因
     * @return 记录完成后的空 Mono 信号
     */
    public Mono<Void> recordUserLocked(String userId, String reason) {
        return redisTemplate.opsForValue().increment(REDIS_METRIC_USER_LOCKED).flatMap(count -> {
            if (count == 1) {
                return redisTemplate
                        .expire(REDIS_METRIC_USER_LOCKED, METRIC_WINDOW)
                        .then();
            }

            // 检查锁定数量是否异常
            if (count >= USER_LOCKED_THRESHOLD) {
                log.error("用户锁定数量异常: count={}/min", count);

                return auditService
                        .logSecurityEvent(
                                "USER_LOCKED_SPIKE",
                                null,
                                null,
                                null,
                                "MEDIUM",
                                "用户锁定数量异常，可能存在大规模暴力破解",
                                Map.of(
                                        "lockedCount", count,
                                        "threshold", USER_LOCKED_THRESHOLD,
                                        "window", "1分钟"))
                        .then();
            }

            return Mono.empty();
        });
    }

    /**
     * 记录Token重放攻击
     *
     * @param tokenId 被重放的 Token ID
     * @param userId Token 关联的用户 ID
     * @return 记录并完成安全审计事件记录后的空 Mono 信号
     */
    public Mono<Void> recordTokenReplay(String tokenId, String userId) {
        return redisTemplate
                .opsForValue()
                .increment(REDIS_METRIC_TOKEN_REPLAY)
                .flatMap(count -> {
                    if (count == 1) {
                        return redisTemplate.expire(REDIS_METRIC_TOKEN_REPLAY, METRIC_WINDOW);
                    }
                    return Mono.just(true);
                })
                .then(auditService.logSecurityEvent(
                        "TOKEN_REPLAY_DETECTED",
                        userId,
                        null,
                        null,
                        "CRITICAL",
                        "检测到Token重放攻击",
                        Map.of(
                                "tokenId", tokenId,
                                "userId", userId)))
                .then();
    }

    /**
     * 记录IP活动
     */
    private Mono<Void> recordIpActivity(String clientIp) {
        String key = REDIS_METRIC_IP_ACTIVITY + clientIp;

        return redisTemplate.opsForValue().increment(key).flatMap(count -> {
            if (count == 1) {
                return redisTemplate.expire(key, METRIC_WINDOW).then();
            }

            // 检查IP活动是否异常
            if (count >= IP_ACTIVITY_THRESHOLD) {
                log.warn("IP活动异常: ip={}, count={}/min", clientIp, count);

                return auditService
                        .logSecurityEvent(
                                "IP_ACTIVITY_SPIKE",
                                null,
                                null,
                                clientIp,
                                "MEDIUM",
                                "单个IP活动频率异常",
                                Map.of(
                                        "ip", clientIp,
                                        "activityCount", count,
                                        "threshold", IP_ACTIVITY_THRESHOLD,
                                        "window", "1分钟"))
                        .then();
            }

            return Mono.empty();
        });
    }

    /**
     * 检查登录失败率
     */
    private Mono<Void> checkLoginFailureRate() {
        return Mono.zip(
                        redisTemplate
                                .opsForValue()
                                .get(REDIS_METRIC_LOGIN_SUCCESS)
                                .map(Long::parseLong)
                                .defaultIfEmpty(0L),
                        redisTemplate
                                .opsForValue()
                                .get(REDIS_METRIC_LOGIN_FAIL)
                                .map(Long::parseLong)
                                .defaultIfEmpty(0L))
                .flatMap(tuple -> {
                    long successCount = tuple.getT1();
                    long failCount = tuple.getT2();
                    long totalCount = successCount + failCount;

                    if (totalCount == 0) {
                        return Mono.empty();
                    }

                    double failureRate = (double) failCount / totalCount;

                    if (failureRate >= LOGIN_FAILURE_RATE_THRESHOLD) {
                        log.error(
                                "登录失败率异常: rate={}%, success={}, fail={}",
                                String.format("%.2f", failureRate * 100), successCount, failCount);

                        return auditService
                                .logSecurityEvent(
                                        "LOGIN_FAILURE_RATE_HIGH",
                                        null,
                                        null,
                                        null,
                                        "HIGH",
                                        "登录失败率异常，可能存在大规模攻击",
                                        Map.of(
                                                "failureRate",
                                                String.format("%.2f%%", failureRate * 100),
                                                "successCount",
                                                successCount,
                                                "failCount",
                                                failCount,
                                                "threshold",
                                                String.format("%.0f%%", LOGIN_FAILURE_RATE_THRESHOLD * 100)))
                                .then();
                    }

                    return Mono.empty();
                });
    }

    /**
     * 获取实时安全指标
     *
     * @return 包含1分钟窗口内各安全指标（登录成功/失败、Token验证成功/失败、用户锁定、Token重放）及采集时间的 Mono
     */
    public Mono<SecurityMetrics> getSecurityMetrics() {
        return Mono.zip(
                        redisTemplate
                                .opsForValue()
                                .get(REDIS_METRIC_LOGIN_SUCCESS)
                                .map(Long::parseLong)
                                .defaultIfEmpty(0L),
                        redisTemplate
                                .opsForValue()
                                .get(REDIS_METRIC_LOGIN_FAIL)
                                .map(Long::parseLong)
                                .defaultIfEmpty(0L),
                        redisTemplate
                                .opsForValue()
                                .get(REDIS_METRIC_TOKEN_VALIDATE_SUCCESS)
                                .map(Long::parseLong)
                                .defaultIfEmpty(0L),
                        redisTemplate
                                .opsForValue()
                                .get(REDIS_METRIC_TOKEN_VALIDATE_FAIL)
                                .map(Long::parseLong)
                                .defaultIfEmpty(0L),
                        redisTemplate
                                .opsForValue()
                                .get(REDIS_METRIC_USER_LOCKED)
                                .map(Long::parseLong)
                                .defaultIfEmpty(0L),
                        redisTemplate
                                .opsForValue()
                                .get(REDIS_METRIC_TOKEN_REPLAY)
                                .map(Long::parseLong)
                                .defaultIfEmpty(0L))
                .map(tuple -> {
                    long loginSuccess = tuple.getT1();
                    long loginFail = tuple.getT2();
                    long tokenValidateSuccess = tuple.getT3();
                    long tokenValidateFail = tuple.getT4();
                    long userLocked = tuple.getT5();
                    long tokenReplay = tuple.getT6();

                    return new SecurityMetrics(
                            loginSuccess,
                            loginFail,
                            tokenValidateSuccess,
                            tokenValidateFail,
                            userLocked,
                            tokenReplay,
                            Instant.now());
                });
    }

    /**
     * 定期同步内存计数器到日志（每分钟一次）
     */
    @Scheduled(fixedRate = 60000)
    public void syncMetricsToLog() {
        long loginSuccess = loginSuccessCounter.getAndSet(0);
        long loginFail = loginFailCounter.getAndSet(0);
        long tokenValidateSuccess = tokenValidateSuccessCounter.getAndSet(0);
        long tokenValidateFail = tokenValidateFailCounter.getAndSet(0);

        if (loginSuccess + loginFail + tokenValidateSuccess + tokenValidateFail > 0) {
            log.info(
                    "【安全指标】登录成功:{}, 登录失败:{}, Token验证成功:{}, Token验证失败:{}",
                    loginSuccess,
                    loginFail,
                    tokenValidateSuccess,
                    tokenValidateFail);
        }
    }

    /**
     * 获取最活跃的异常IP列表
     *
     * @param limit 返回的最大条数
     * @return 按活动次数降序排列、活动次数达到异常活动阈值的 IP 活动指标流
     */
    public Flux<IpActivityMetric> getTopAbnormalIps(int limit) {
        return redisTemplate
                .keys(REDIS_METRIC_IP_ACTIVITY + "*")
                .flatMap(key -> redisTemplate
                        .opsForValue()
                        .get(key)
                        .map(count ->
                                new IpActivityMetric(key.replace(REDIS_METRIC_IP_ACTIVITY, ""), Long.parseLong(count))))
                .filter(metric -> metric.count >= IP_ACTIVITY_THRESHOLD)
                .sort((a, b) -> Long.compare(b.count, a.count))
                .take(limit);
    }

    /**
     * 安全指标数据结构
     */
    public static class SecurityMetrics {
        private final long loginSuccess;
        private final long loginFail;
        private final long tokenValidateSuccess;
        private final long tokenValidateFail;
        private final long userLocked;
        private final long tokenReplay;
        private final Instant timestamp;

        public SecurityMetrics(
                long loginSuccess,
                long loginFail,
                long tokenValidateSuccess,
                long tokenValidateFail,
                long userLocked,
                long tokenReplay,
                Instant timestamp) {
            this.loginSuccess = loginSuccess;
            this.loginFail = loginFail;
            this.tokenValidateSuccess = tokenValidateSuccess;
            this.tokenValidateFail = tokenValidateFail;
            this.userLocked = userLocked;
            this.tokenReplay = tokenReplay;
            this.timestamp = timestamp;
        }

        /**
         * 计算登录失败率
         *
         * @return 登录失败次数占总登录次数的比例，窗口期内无登录记录时返回 0
         */
        public double getLoginFailureRate() {
            long total = loginSuccess + loginFail;
            return total == 0 ? 0 : (double) loginFail / total;
        }

        /**
         * 计算 Token 验证失败率
         *
         * @return Token 验证失败次数占总验证次数的比例，窗口期内无验证记录时返回 0
         */
        public double getTokenValidateFailureRate() {
            long total = tokenValidateSuccess + tokenValidateFail;
            return total == 0 ? 0 : (double) tokenValidateFail / total;
        }

        /**
         * 转换为 Map 结构
         *
         * @return 包含各安全指标、失败率和采集时间的 Map
         */
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>(16);
            map.put("loginSuccess", loginSuccess);
            map.put("loginFail", loginFail);
            map.put("loginFailureRate", String.format("%.2f%%", getLoginFailureRate() * 100));
            map.put("tokenValidateSuccess", tokenValidateSuccess);
            map.put("tokenValidateFail", tokenValidateFail);
            map.put("tokenValidateFailureRate", String.format("%.2f%%", getTokenValidateFailureRate() * 100));
            map.put("userLocked", userLocked);
            map.put("tokenReplay", tokenReplay);
            map.put("timestamp", timestamp.toString());
            return map;
        }

        // Getters
        /**
         * 获取登录成功次数
         *
         * @return 窗口期内登录成功次数
         */
        public long getLoginSuccess() {
            return loginSuccess;
        }

        /**
         * 获取登录失败次数
         *
         * @return 窗口期内登录失败次数
         */
        public long getLoginFail() {
            return loginFail;
        }

        /**
         * 获取 Token 验证成功次数
         *
         * @return 窗口期内 Token 验证成功次数
         */
        public long getTokenValidateSuccess() {
            return tokenValidateSuccess;
        }

        /**
         * 获取 Token 验证失败次数
         *
         * @return 窗口期内 Token 验证失败次数
         */
        public long getTokenValidateFail() {
            return tokenValidateFail;
        }

        /**
         * 获取用户锁定次数
         *
         * @return 窗口期内用户锁定次数
         */
        public long getUserLocked() {
            return userLocked;
        }

        /**
         * 获取 Token 重放攻击次数
         *
         * @return 窗口期内 Token 重放攻击次数
         */
        public long getTokenReplay() {
            return tokenReplay;
        }

        /**
         * 获取指标采集时间
         *
         * @return 指标采集时间戳
         */
        public Instant getTimestamp() {
            return timestamp;
        }
    }

    /**
     * IP活动指标
     */
    public static class IpActivityMetric {
        private final String ip;
        private final long count;

        public IpActivityMetric(String ip, long count) {
            this.ip = ip;
            this.count = count;
        }

        /**
         * 获取 IP 地址
         *
         * @return IP 地址
         */
        public String getIp() {
            return ip;
        }

        /**
         * 获取活动次数
         *
         * @return 窗口期内该 IP 的活动次数
         */
        public long getCount() {
            return count;
        }
    }
}
