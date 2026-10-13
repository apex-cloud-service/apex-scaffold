package com.apex.scaffold.interfaces.monitor;

import io.micrometer.core.instrument.Counter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 安全指标记录器。
 *
 * <p>将安全事件记录到 Micrometer 计数器并导出到 Prometheus，用于监控
 * 限流拒绝、IP封禁、登录失败、XSS拦截、防重放拒绝和熔断器开启等安全事件。</p>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 */
@Component
public class SecurityMetricsRecorder {

    private final Counter rateLimitRejectedCounter;
    private final Counter ipBlockedCounter;
    private final Counter loginFailedCounter;
    private final Counter xssInterceptedCounter;
    private final Counter antiReplayRejectedCounter;
    private final Counter repeatSubmitRejectedCounter;
    private final Counter circuitBreakerOpenedCounter;

    /**
     * Creates a recorder backed by the counters registered in {@code MetricsConfig}.
     *
     * @param rateLimitRejectedCounter 限流拒绝计数器
     * @param ipBlockedCounter IP 封禁计数器
     * @param loginFailedCounter 登录失败计数器
     * @param xssInterceptedCounter XSS 拦截计数器
     * @param antiReplayRejectedCounter 防重放拒绝计数器
     * @param repeatSubmitRejectedCounter 幂等重复提交拒绝计数器
     * @param circuitBreakerOpenedCounter 熔断器开启计数器
     */
    public SecurityMetricsRecorder(
            @Qualifier("rateLimitRejectedCounter") Counter rateLimitRejectedCounter,
            @Qualifier("ipBlockedCounter") Counter ipBlockedCounter,
            @Qualifier("loginFailedCounter") Counter loginFailedCounter,
            @Qualifier("xssInterceptedCounter") Counter xssInterceptedCounter,
            @Qualifier("antiReplayRejectedCounter") Counter antiReplayRejectedCounter,
            @Qualifier("repeatSubmitRejectedCounter") Counter repeatSubmitRejectedCounter,
            @Qualifier("circuitBreakerOpenedCounter") Counter circuitBreakerOpenedCounter) {
        this.rateLimitRejectedCounter = rateLimitRejectedCounter;
        this.ipBlockedCounter = ipBlockedCounter;
        this.loginFailedCounter = loginFailedCounter;
        this.xssInterceptedCounter = xssInterceptedCounter;
        this.antiReplayRejectedCounter = antiReplayRejectedCounter;
        this.repeatSubmitRejectedCounter = repeatSubmitRejectedCounter;
        this.circuitBreakerOpenedCounter = circuitBreakerOpenedCounter;
    }

    /** Records a request rejected by rate limiting. */
    public void recordRateLimitRejected() {
        rateLimitRejectedCounter.increment();
    }

    /** Records an IP added to the blacklist. */
    public void recordIpBlocked() {
        ipBlockedCounter.increment();
    }

    /** Records a failed login attempt. */
    public void recordLoginFailed() {
        loginFailedCounter.increment();
    }

    /** Records an intercepted XSS request. */
    public void recordXssIntercepted() {
        xssInterceptedCounter.increment();
    }

    /** Records a request rejected by replay protection. */
    public void recordAntiReplayRejected() {
        antiReplayRejectedCounter.increment();
    }

    /** Records a request rejected by HTTP idempotency deduplication. */
    public void recordRepeatSubmitRejected() {
        repeatSubmitRejectedCounter.increment();
    }

    /** Records a circuit breaker opened (degradation triggered). */
    public void recordCircuitBreakerOpened() {
        circuitBreakerOpenedCounter.increment();
    }
}
