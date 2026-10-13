package com.apex.scaffold.interfaces.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 网关监控指标增强
 *
 * <p>扩展 ApiGatewayFilter 的监控能力，提供更多维度的网关运行状态指标。
 *
 * <h2>新增指标</h2>
 * <ul>
 *   <li>apex.gateway.connections.active - 当前活跃连接数</li>
 *   <li>apex.gateway.queue.length - 请求队列长度</li>
 *   <li>apex.gateway.health.success - 健康检查成功次数</li>
 *   <li>apex.gateway.slow.requests - 慢请求次数（> 3s）</li>
 *   <li>apex.gateway.ratelimit.rejected - 限流拒绝次数</li>
 *   <li>apex.gateway.circuitbreaker.triggered - 熔断触发次数</li>
 * </ul>
 *
 * <h2>Prometheus 查询示例</h2>
 * <pre>
 * # 当前活跃连接数
 * apex_gateway_connections_active
 *
 * # 限流拒绝率
 * rate(apex_gateway_ratelimit_rejected_total[5m])
 *
 * # 慢请求比例
 * 100 * rate(apex_gateway_slow_requests_total[5m])
 *   / rate(apex_gateway_request_count_total[5m])
 * </pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.1.0
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "apex.gateway.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GatewayMetrics {

    private final AtomicLong activeConnections = new AtomicLong(0);
    private final AtomicLong queueLength = new AtomicLong(0);

    private final Counter healthSuccessCounter;
    private final Counter slowRequestsCounter;
    private final Counter rateLimitRejectedCounter;
    private final Counter circuitBreakerTriggeredCounter;

    /** 创建网关监控指标组件，向注册表注册网关相关 Gauge 与 Counter。 */
    public GatewayMetrics(MeterRegistry meterRegistry) {
        // 活跃连接数（Gauge）
        Gauge.builder("apex.gateway.connections.active", activeConnections, AtomicLong::get)
                .tag("component", "gateway")
                .description("当前活跃连接数")
                .register(meterRegistry);

        // 请求队列长度（Gauge）
        Gauge.builder("apex.gateway.queue.length", queueLength, AtomicLong::get)
                .tag("component", "gateway")
                .description("请求队列长度")
                .register(meterRegistry);

        // 健康检查成功次数（Counter）
        this.healthSuccessCounter = Counter.builder("apex.gateway.health.success")
                .tag("component", "gateway")
                .description("健康检查成功次数")
                .register(meterRegistry);

        // 慢请求次数（Counter）
        this.slowRequestsCounter = Counter.builder("apex.gateway.slow.requests")
                .tag("component", "gateway")
                .tag("threshold", "3s")
                .description("慢请求次数（> 3s）")
                .register(meterRegistry);

        // 限流拒绝次数（Counter）
        this.rateLimitRejectedCounter = Counter.builder("apex.gateway.ratelimit.rejected")
                .tag("component", "gateway")
                .description("限流拒绝次数")
                .register(meterRegistry);

        // 熔断触发次数（Counter）
        this.circuitBreakerTriggeredCounter = Counter.builder("apex.gateway.circuitbreaker.triggered")
                .tag("component", "gateway")
                .description("熔断触发次数")
                .register(meterRegistry);
    }

    /**
     * 增加活跃连接数
     */
    public void incrementActiveConnections() {
        activeConnections.incrementAndGet();
    }

    /**
     * 减少活跃连接数
     */
    public void decrementActiveConnections() {
        activeConnections.decrementAndGet();
    }

    /**
     * 更新请求队列长度
     *
     * @param length 队列长度
     */
    public void updateQueueLength(long length) {
        queueLength.set(length);
    }

    /**
     * 记录健康检查成功
     */
    public void recordHealthSuccess() {
        healthSuccessCounter.increment();
    }

    /**
     * 记录慢请求
     */
    public void recordSlowRequest() {
        slowRequestsCounter.increment();
    }

    /**
     * 记录限流拒绝
     */
    public void recordRateLimitRejected() {
        rateLimitRejectedCounter.increment();
    }

    /**
     * 记录熔断触发
     */
    public void recordCircuitBreakerTriggered() {
        circuitBreakerTriggeredCounter.increment();
    }

    /**
     * 获取当前活跃连接数
     *
     * @return 活跃连接数
     */
    public long getActiveConnections() {
        return activeConnections.get();
    }

    /**
     * 获取当前队列长度
     *
     * @return 队列长度
     */
    public long getQueueLength() {
        return queueLength.get();
    }
}
