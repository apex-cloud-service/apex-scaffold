package com.apex.scaffold.infrastructure.cache.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * BloomFilter 重建任务监控指标
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.1.0
 */
@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "apex.cache.bloom-filter.metrics",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class BloomFilterRebuildMetrics {

    private final Counter rebuildSuccessCounter;
    private final Counter rebuildFailureCounter;

    /**
     * 构造 BloomFilter 重建指标并注册成功/失败计数器。
     *
     * @param meterRegistry Micrometer 指标注册表
     */
    public BloomFilterRebuildMetrics(MeterRegistry meterRegistry) {
        this.rebuildSuccessCounter = Counter.builder("bloom_filter_rebuild_success_total")
                .description("BloomFilter 重建成功次数")
                .tag("component", "cache")
                .register(meterRegistry);

        this.rebuildFailureCounter = Counter.builder("bloom_filter_rebuild_failures_total")
                .description("BloomFilter 重建失败次数")
                .tag("component", "cache")
                .register(meterRegistry);
    }

    /**
     * 记录重建成功
     */
    public void recordSuccess() {
        rebuildSuccessCounter.increment();
    }

    /**
     * 记录重建失败
     */
    public void recordFailure() {
        rebuildFailureCounter.increment();
    }
}
