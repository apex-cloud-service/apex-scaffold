package com.apex.scaffold.auth.infrastructure.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 安全监控指标服务
 *
 * 集成Prometheus暴露以下监控指标：
 * - 认证成功/失败次数
 * - 登录尝试次数
 * - Token颁发/刷新/撤销次数
 * - 暴力破解攻击检测
 * - 权限检查次数
 * - API调用延迟
 * @author Apex Team
 * @since 1.2.0
 */
@Slf4j
@Service
public class SecurityMetricsService {

    private static final int MAX_FAILED_LOGIN_IPS_CACHE_SIZE = 100;

    private final MeterRegistry meterRegistry;

    /**
     * 计数器
     */
    private final Counter authSuccessCounter;

    private final Counter authFailureCounter;
    private final Counter tokenIssuedCounter;
    private final Counter tokenRefreshedCounter;
    private final Counter tokenRevokedCounter;
    private final Counter bruteForceDetectedCounter;
    private final Counter permissionDeniedCounter;

    /**
     * 计时器
     */
    private final Timer authenticationTimer;

    private final Timer tokenValidationTimer;
    private final Timer permissionCheckTimer;

    /**
     * 实时统计
     */
    private final ConcurrentHashMap<String, AtomicInteger> activeSessionsByUser;

    private final ConcurrentHashMap<String, AtomicInteger> failedLoginsByIp;

    /**
     * 构造安全指标服务并注册监控计数器与计时器。
     *
     * @param meterRegistry Micrometer指标注册表
     */
    public SecurityMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.activeSessionsByUser = new ConcurrentHashMap<>();
        this.failedLoginsByIp = new ConcurrentHashMap<>();

        this.authSuccessCounter =
                Counter.builder("auth.success.total").description("认证成功总次数").register(meterRegistry);

        this.authFailureCounter = Counter.builder("auth.failure.total")
                .description("认证失败总次数")
                .tag("reason", "invalid_credentials")
                .register(meterRegistry);

        this.tokenIssuedCounter =
                Counter.builder("token.issued.total").description("Token颁发总次数").register(meterRegistry);

        this.tokenRefreshedCounter = Counter.builder("token.refreshed.total")
                .description("Token刷新总次数")
                .register(meterRegistry);

        this.tokenRevokedCounter =
                Counter.builder("token.revoked.total").description("Token撤销总次数").register(meterRegistry);

        this.bruteForceDetectedCounter = Counter.builder("security.bruteforce.detected.total")
                .description("检测到的暴力破解攻击次数")
                .register(meterRegistry);

        this.permissionDeniedCounter = Counter.builder("permission.denied.total")
                .description("权限拒绝总次数")
                .register(meterRegistry);

        this.authenticationTimer =
                Timer.builder("auth.duration").description("认证耗时").register(meterRegistry);

        this.tokenValidationTimer = Timer.builder("token.validation.duration")
                .description("Token验证耗时")
                .register(meterRegistry);

        this.permissionCheckTimer =
                Timer.builder("permission.check.duration").description("权限检查耗时").register(meterRegistry);

        // 注册Gauge指标
        meterRegistry.gauge("auth.active.sessions", activeSessionsByUser, map -> map.size());
        meterRegistry.gauge("auth.failed.logins.ips", failedLoginsByIp, map -> map.size());
    }

    /**
     * 记录认证成功
     *
     * @param userId 认证成功的用户ID
     * @param tenantId 用户所属租户ID
     * @param method 认证方式（如密码、短信、生物识别）
     */
    public void recordAuthSuccess(String userId, String tenantId, String method) {
        authSuccessCounter.increment();

        Counter.builder("auth.success.by.method")
                .tag("method", method)
                .tag("tenant", tenantId)
                .register(meterRegistry)
                .increment();

        activeSessionsByUser
                .computeIfAbsent(userId, k -> meterRegistry.gauge("auth.active.sessions.by.user", new AtomicInteger(0)))
                .incrementAndGet();

        log.debug("认证成功");
    }

    /**
     * 记录认证失败
     *
     * @param username 登录失败的用户名
     * @param reason 失败原因（如无效凭证）
     * @param ipAddress 发起登录的客户端IP地址
     */
    public void recordAuthFailure(String username, String reason, String ipAddress) {
        authFailureCounter.increment();

        Counter.builder("auth.failure.by.reason")
                .tag("reason", reason)
                .register(meterRegistry)
                .increment();

        AtomicInteger failCount = failedLoginsByIp.computeIfAbsent(ipAddress, k -> new AtomicInteger(0));
        failCount.incrementAndGet();

        if (failedLoginsByIp.size() > MAX_FAILED_LOGIN_IPS_CACHE_SIZE) {
            failedLoginsByIp.entrySet().removeIf(entry -> entry.getValue().get() == 0);
        }

        log.debug("认证失败");
    }

    /**
     * 记录Token颁发
     *
     * @param userId 获颁Token的用户ID
     * @param tokenType Token类型（如access、refresh）
     */
    public void recordTokenIssued(String userId, String tokenType) {
        tokenIssuedCounter.increment();

        Counter.builder("token.issued.by.type")
                .tag("type", tokenType)
                .register(meterRegistry)
                .increment();
    }

    /**
     * 记录Token刷新
     *
     * @param userId 刷新Token的用户ID
     */
    public void recordTokenRefreshed(String userId) {
        tokenRefreshedCounter.increment();
    }

    /**
     * 记录Token撤销
     *
     * @param userId Token被撤销的用户ID
     * @param reason 撤销原因（如登出、管理员强制下线）
     */
    public void recordTokenRevoked(String userId, String reason) {
        tokenRevokedCounter.increment();

        Counter.builder("token.revoked.by.reason")
                .tag("reason", reason)
                .register(meterRegistry)
                .increment();
    }

    /**
     * 记录暴力破解检测
     *
     * @param identifier 攻击来源标识（用户名或IP地址）
     * @param type 暴力破解攻击类型
     */
    public void recordBruteForceDetected(String identifier, String type) {
        bruteForceDetectedCounter.increment();

        Counter.builder("security.bruteforce.by.type")
                .tag("type", type)
                .register(meterRegistry)
                .increment();

        log.warn("检测到暴力破解攻击");
    }

    /**
     * 记录权限拒绝
     *
     * @param userId 被拒绝的用户ID
     * @param resource 被访问的资源标识
     * @param action 被拒绝的操作类型（如read、write）
     */
    public void recordPermissionDenied(String userId, String resource, String action) {
        permissionDeniedCounter.increment();

        Counter.builder("permission.denied.by.resource")
                .tag("resource", resource)
                .tag("action", action)
                .register(meterRegistry)
                .increment();

        log.debug("权限拒绝");
    }

    /**
     * 记录认证耗时
     *
     * @param duration 本次认证耗时
     * @param success 认证是否成功
     */
    public void recordAuthDuration(Duration duration, boolean success) {
        authenticationTimer.record(duration);

        Timer.builder("auth.duration.by.result")
                .tag("success", String.valueOf(success))
                .register(meterRegistry)
                .record(duration);
    }

    /**
     * 记录Token验证耗时
     *
     * @param duration 本次Token验证耗时
     * @param valid Token验证是否通过
     */
    public void recordTokenValidationDuration(Duration duration, boolean valid) {
        tokenValidationTimer.record(duration);

        Timer.builder("token.validation.duration.by.result")
                .tag("valid", String.valueOf(valid))
                .register(meterRegistry)
                .record(duration);
    }

    /**
     * 记录权限检查耗时
     *
     * @param duration 本次权限检查耗时
     * @param allowed 权限检查是否通过
     */
    public void recordPermissionCheckDuration(Duration duration, boolean allowed) {
        permissionCheckTimer.record(duration);

        Timer.builder("permission.check.duration.by.result")
                .tag("allowed", String.valueOf(allowed))
                .register(meterRegistry)
                .record(duration);
    }

    /**
     * 用户注销 - 减少活跃会话数
     *
     * @param userId 注销的用户ID
     */
    public void recordUserLogout(String userId) {
        AtomicInteger sessions = activeSessionsByUser.get(userId);
        if (sessions != null && sessions.get() > 0) {
            sessions.decrementAndGet();
        }
    }

    /**
     * 清除IP失败记录
     *
     * @param ipAddress 要清除失败记录的IP地址
     */
    public void clearFailedLoginAttempts(String ipAddress) {
        AtomicInteger failCount = failedLoginsByIp.get(ipAddress);
        if (failCount != null) {
            failCount.set(0);
        }
    }

    /**
     * 获取IP失败登录次数
     *
     * @param ipAddress IP地址
     * @return 该IP累计的失败登录次数，无记录时返回0
     */
    public int getFailedLoginAttempts(String ipAddress) {
        AtomicInteger failCount = failedLoginsByIp.get(ipAddress);
        return failCount != null ? failCount.get() : 0;
    }

    /**
     * 获取用户活跃会话数
     *
     * @param userId 用户ID
     * @return 该用户的活跃会话数，无记录时返回0
     */
    public int getActiveSessionCount(String userId) {
        AtomicInteger sessions = activeSessionsByUser.get(userId);
        return sessions != null ? sessions.get() : 0;
    }
}
