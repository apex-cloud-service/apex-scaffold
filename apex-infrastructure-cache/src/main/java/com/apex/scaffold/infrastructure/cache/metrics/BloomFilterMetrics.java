package com.apex.scaffold.infrastructure.cache.metrics;

import com.apex.scaffold.infrastructure.cache.filter.BloomFilterService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * BloomFilter 监控指标采集
 *
 * <p>通过 AOP 拦截 BloomFilter 的关键方法调用，采集以下指标：
 * <ul>
 *   <li><b>命中次数</b>：BloomFilter 判定 key 不存在，成功过滤请求</li>
 *   <li><b>穿透次数</b>：BloomFilter 判定 key 可能存在，但实际不存在（误判）</li>
 *   <li><b>查询延迟</b>：BloomFilter mightContain 方法执行时间</li>
 *   <li><b>命名空间数量</b>：当前已初始化的命名空间总数</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <table>
 *   <caption>导出的指标列表</caption>
 *   <tr><th>指标名</th><th>类型</th><th>说明</th></tr>
 *   <tr><td>cache_bloom_filter_hits_total</td><td>Counter</td><td>过滤掉的请求次数（判定不存在）</td></tr>
 *   <tr><td>cache_bloom_filter_pass_total</td><td>Counter</td><td>通过的请求次数（判定可能存在）</td></tr>
 *   <tr><td>cache_bloom_filter_false_positives_total</td><td>Counter</td><td>误判次数（需业务层配合上报）</td></tr>
 *   <tr><td>cache_bloom_filter_query_duration</td><td>Timer</td><td>查询延迟分布</td></tr>
 *   <tr><td>cache_bloom_filter_namespaces</td><td>Gauge</td><td>命名空间数量</td></tr>
 * </table>
 *
 * <h2>设计说明</h2>
 * <pre>
 * 误判率 = false_positives_total / (hits_total + pass_total)
 * </pre>
 *
 * <h2>设计说明</h2>
 * <p>通过以下配置启用：
 * <pre>
 * apex:
 *   cache:
 *     bloom-filter:
 *       enabled: true
 *       metrics:
 *         enabled: true
 * </pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.1.0
 */
@Slf4j
@Aspect
@Component
@ConditionalOnBean(BloomFilterService.class)
@ConditionalOnProperty(
        prefix = "apex.cache.bloom-filter.metrics",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class BloomFilterMetrics {

    private final Counter hitsCounter;
    private final Counter passCounter;
    private final Counter falsePositivesCounter;
    private final Timer queryTimer;

    /**
     * 构造监控指标采集器
     *
     * @param meterRegistry Micrometer 指标注册表
     * @param bloomFilterService BloomFilter 服务（用于采集命名空间数量）
     */
    public BloomFilterMetrics(MeterRegistry meterRegistry, BloomFilterService bloomFilterService) {
        // 命中次数（过滤掉的请求）
        this.hitsCounter = Counter.builder("cache_bloom_filter_hits_total")
                .description("BloomFilter 命中次数（判定 key 不存在，成功过滤）")
                .tag("component", "cache")
                .register(meterRegistry);

        // 穿透次数（判定可能存在）
        this.passCounter = Counter.builder("cache_bloom_filter_pass_total")
                .description("BloomFilter 穿透次数（判定 key 可能存在）")
                .tag("component", "cache")
                .register(meterRegistry);

        // 误判次数（需要业务层通过 recordFalsePositive 方法上报）
        this.falsePositivesCounter = Counter.builder("cache_bloom_filter_false_positives_total")
                .description("BloomFilter 误判次数（判定存在但实际不存在）")
                .tag("component", "cache")
                .register(meterRegistry);

        // 查询延迟（P50/P90/P95/P99 分位）
        this.queryTimer = Timer.builder("cache_bloom_filter_query_duration")
                .description("BloomFilter 查询延迟")
                .tag("component", "cache")
                .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                .register(meterRegistry);

        // 命名空间数量
        Gauge.builder("cache_bloom_filter_namespaces", bloomFilterService, BloomFilterService::getNamespaceCount)
                .description("BloomFilter 命名空间数量")
                .tag("component", "cache")
                .register(meterRegistry);
    }

    /**
     * 拦截 BloomFilter.mightContain 方法，采集指标
     *
     * @param joinPoint 切点
     * @return 方法返回值
     * @throws Throwable 方法执行异常
     */
    @Around("execution(* com.apex.scaffold.infrastructure.cache.filter.BloomFilterService.mightContain(..))")
    public Object aroundMightContain(ProceedingJoinPoint joinPoint) throws Throwable {
        return queryTimer.record(() -> {
            try {
                Boolean result = (Boolean) joinPoint.proceed();
                if (result != null) {
                    if (result) {
                        // BloomFilter 判定可能存在
                        passCounter.increment();
                    } else {
                        // BloomFilter 判定不存在（命中，过滤掉请求）
                        hitsCounter.increment();
                    }
                }
                return result;
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * 记录误判（由业务层调用）
     *
     * <p>当 BloomFilter 判定 key 可能存在，但实际查询缓存和数据库都不存在时，
     * 业务层应该调用此方法记录误判。
     *
     * <p>使用示例：
     * <pre>{@code
     * if (bloomFilter.mightContain("user", key)) {
     *     User user = cache.get(key);
     *     if (user == null) {
     *         user = db.findById(key);
     *         if (user == null) {
     *             // BloomFilter 误判
     *             bloomFilterMetrics.recordFalsePositive("user");
     *         }
     *     }
     * }
     * }</pre>
     *
     * @param namespace 命名空间
     */
    public void recordFalsePositive(String namespace) {
        falsePositivesCounter.increment();
        log.debug("记录 BloomFilter 误判, namespace: {}", namespace);
    }

    /**
     * 获取命中率
     *
     * @return 命中率（0-1）
     */
    public double getHitRate() {
        double hits = hitsCounter.count();
        double total = hits + passCounter.count();
        return total > 0 ? hits / total : 0.0;
    }

    /**
     * 获取误判率
     *
     * @return 误判率（0-1）
     */
    public double getFalsePositiveRate() {
        double falsePositives = falsePositivesCounter.count();
        double total = hitsCounter.count() + passCounter.count();
        return total > 0 ? falsePositives / total : 0.0;
    }
}
