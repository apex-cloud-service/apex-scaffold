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

    /**
     * 记录一次被限流拒绝的请求
     *
     * <p>安全过滤器在速率限制命中时调用，递增对应的限流拒绝计数器。
     */
    public void recordRateLimitRejected() {
        rateLimitRejectedCounter.increment();
    }

    /**
     * 记录一次 IP 加入黑名单的操作
     *
     * <p>在 IP 被拉入黑名单时调用，递增 IP 封禁计数器。
     */
    public void recordIpBlocked() {
        ipBlockedCounter.increment();
    }

    /**
     * 记录一次登录失败
     *
     * <p>认证失败时调用，递增登录失败计数器，供安全告警与风控统计。
     */
    public void recordLoginFailed() {
        loginFailedCounter.increment();
    }

    /**
     * 记录一次被拦截的 XSS 请求
     *
     * <p>XSS 过滤拦截到可疑输入时调用，递增 XSS 拦截计数器。
     */
    public void recordXssIntercepted() {
        xssInterceptedCounter.increment();
    }

    /**
     * 记录一次被防重放拒绝的请求
     *
     * <p>防重放校验判定请求非法时调用，递增防重放拒绝计数器。
     */
    public void recordAntiReplayRejected() {
        antiReplayRejectedCounter.increment();
    }

    /**
     * 记录一次被 HTTP 幂等去重拒绝的请求
     *
     * <p>重复提交被幂等去重拦截时调用，递增重复提交拒绝计数器。
     */
    public void recordRepeatSubmitRejected() {
        repeatSubmitRejectedCounter.increment();
    }

    /**
     * 记录一次熔断器打开（触发降级）
     *
     * <p>熔断器由关闭转为打开时调用，递增熔断开启计数器，便于观察降级频率。
     */
    public void recordCircuitBreakerOpened() {
        circuitBreakerOpenedCounter.increment();
    }
}
