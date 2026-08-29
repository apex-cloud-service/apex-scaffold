package com.apex.scaffold.infrastructure.mq.aspect;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * MQ 消息发布指标切面。
 *
 * <p>拦截 {@link com.apex.scaffold.infrastructure.mq.service.MqProducerService} 全部
 * {@code send*} 方法，统计每次 RocketMQ 发送的结果与耗时，上报 Micrometer 指标，
 * 支撑 Grafana 大盘「消息中间件（RocketMQ / Outbox）」分组：</p>
 * <ul>
 *   <li>{@code apex.mq.publish.count}：发送次数 Counter，按 {@code mode}（sync/async/oneway/delay/headers）
 *       与 {@code result}（success/failed）标签区分</li>
 *   <li>{@code apex.mq.publish.latency}：发送耗时 Timer，按 {@code mode} 标签区分</li>
 * </ul>
 *
 * <p>切面以环绕通知包裹，发送异常正常上抛，不影响原有语义；计时在 finally 中完成。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.3.0
 */
@Aspect
@Component
@ConditionalOnProperty(prefix = "apex.features", name = "message-queue", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass(RocketMQTemplate.class)
@ConditionalOnBean(RocketMQTemplate.class)
public class MqPublishMetricsAspect {

    /** 发送次数指标名。 */
    static final String METRIC_COUNT = "apex.mq.publish.count";

    /** 发送耗时指标名。 */
    static final String METRIC_LATENCY = "apex.mq.publish.latency";

    /** 指标注册表。 */
    private final MeterRegistry meterRegistry;

    /** 按 mode 缓存的发送次数 Counter。 */
    private final Map<String, Counter> countCounters = new ConcurrentHashMap<>();

    /** 按 mode 缓存的发送耗时 Timer。 */
    private final Map<String, Timer> latencyTimers = new ConcurrentHashMap<>();

    /**
     * 构造切面。
     *
     * @param meterRegistry Micrometer 指标注册表
     */
    public MqPublishMetricsAspect(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * 环绕通知：统计 MQ 发送结果与耗时。
     *
     * @param joinPoint 连接点（MqProducerService.send* 方法）
     * @return 目标方法执行结果
     * @throws Throwable 目标方法异常原样上抛
     */
    @Around("execution(* com.apex.scaffold.infrastructure.mq.service.MqProducerService.send*(..))")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        String mode = resolveMode(joinPoint.getSignature().getName());
        long start = System.nanoTime();
        try {
            Object result = joinPoint.proceed();
            countCounter(mode, "success").increment();
            return result;
        } catch (Throwable t) {
            countCounter(mode, "failed").increment();
            throw t;
        } finally {
            latencyTimer(mode).record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        }
    }

    /**
     * 将发送方法名映射为发送模式。
     *
     * @param methodName 方法名
     * @return 发送模式（sync/async/oneway/delay/headers），无法识别时为 unknown
     */
    private String resolveMode(String methodName) {
        if (methodName == null) {
            return "unknown";
        }
        return switch (methodName) {
            case "sendSync" -> "sync";
            case "sendAsync" -> "async";
            case "sendOneWay" -> "oneway";
            case "sendDelay" -> "delay";
            case "sendWithHeaders" -> "headers";
            default -> "unknown";
        };
    }

    /**
     * 获取（并按需创建）指定模式与结果的发送次数 Counter。
     *
     * @param mode   发送模式
     * @param result 发送结果（success/failed）
     * @return Counter 实例
     */
    private Counter countCounter(String mode, String result) {
        return countCounters.computeIfAbsent(
                mode + ":" + result,
                key -> Counter.builder(METRIC_COUNT)
                        .tag("mode", mode)
                        .tag("result", result)
                        .register(meterRegistry));
    }

    /**
     * 获取（并按需创建）指定模式的发送耗时 Timer。
     *
     * @param mode 发送模式
     * @return Timer 实例
     */
    private Timer latencyTimer(String mode) {
        return latencyTimers.computeIfAbsent(
                mode, key -> Timer.builder(METRIC_LATENCY).tag("mode", mode).register(meterRegistry));
    }
}
