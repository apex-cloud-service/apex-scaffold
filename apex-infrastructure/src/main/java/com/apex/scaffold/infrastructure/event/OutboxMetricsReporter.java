package com.apex.scaffold.infrastructure.event;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Outbox 积压指标上报器
 *
 * <p>定时统计 {@code sys_domain_event_outbox} 表各状态记录数，刷新为 Micrometer Gauge
 * 指标 {@code apex.outbox.records}（带 {@code status} 标签：PENDING/PROCESSING/DEAD 等），
 * 供 Prometheus 抓取并在面板上展示积压趋势。</p>
 *
 * <p>统计查询为单表分组聚合，默认每 10s 刷新一次。查询失败时状态值明确置为 NaN，
 * 并将 {@code apex.outbox.metrics.up} 置为 0，避免把上一次成功结果误当成当前积压。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see DomainEventOutboxStore#countByStatus()
 * @see MeterRegistry
 */
@Component
public class OutboxMetricsReporter {

    private static final Logger log = LoggerFactory.getLogger(OutboxMetricsReporter.class);

    /** 指标名：Outbox 各状态记录数 */
    private static final String METRIC_NAME = "apex.outbox.records";

    /** 状态标签名 */
    private static final String STATUS_TAG = "status";

    /** Outbox 存储。 */
    private final DomainEventOutboxStore outboxStore;

    /** 指标注册表。 */
    private final MeterRegistry meterRegistry;

    /** 各状态对应的 Gauge 值引用。 */
    private final Map<String, AtomicReference<Double>> statusGauges = new ConcurrentHashMap<>();

    /** 最近一次统计查询是否成功。 */
    private final AtomicLong metricsUp = new AtomicLong();

    /**
     * 创建 Outbox 指标上报器并注册查询可用性指标。
     *
     * @param outboxStore Outbox 存储
     * @param meterRegistry 指标注册表
     */
    public OutboxMetricsReporter(DomainEventOutboxStore outboxStore, MeterRegistry meterRegistry) {
        this.outboxStore = outboxStore;
        this.meterRegistry = meterRegistry;
        Gauge.builder("apex.outbox.metrics.up", metricsUp, AtomicLong::get)
                .description("Outbox 状态统计查询是否成功（1=成功，0=失败）")
                .register(meterRegistry);
    }

    /**
     * 定时统计 Outbox 各状态数量并刷新指标。
     *
     * <p>本次查询成功时，当前结果中的状态被写入 Gauge，已经消失的历史状态被置为零；
     * 空表表示查询成功但没有状态序列。查询异常时已有状态置为 NaN，并将可用性指标置为 0，
     * 避免把过期快照误报为当前积压。</p>
     */
    @Scheduled(fixedDelayString = "${apex.events.outbox.metrics-interval-ms:10000}")
    public void refresh() {
        try {
            Map<String, Long> counts = outboxStore.countByStatus();
            Set<String> statuses = new HashSet<>(statusGauges.keySet());
            if (counts != null) {
                counts.keySet().stream()
                        .filter(status -> status != null && !status.isBlank())
                        .forEach(statuses::add);
            }
            statuses.forEach(status -> gaugeFor(status)
                    .set(counts == null ? 0.0 : counts.getOrDefault(status, 0L).doubleValue()));
            metricsUp.set(1L);
            if (log.isDebugEnabled()) {
                log.debug("Outbox 状态统计完成: {}", counts);
            }
        } catch (RuntimeException exception) {
            statusGauges.values().forEach(value -> value.set(Double.NaN));
            metricsUp.set(0L);
            log.warn("Outbox 状态统计失败，当前状态指标置为无数据: {}", exception.getMessage());
        }
    }

    /**
     * 获取指定状态的 Gauge 值引用，首次访问时注册指标。
     *
     * @param status 状态（PENDING/PROCESSING/DEAD 等）
     * @return 对应 Gauge 的数值引用
     */
    private AtomicReference<Double> gaugeFor(String status) {
        return statusGauges.computeIfAbsent(status, key -> {
            AtomicReference<Double> value = new AtomicReference<>(Double.NaN);
            Gauge.builder(METRIC_NAME, value, AtomicReference::get)
                    .tag(STATUS_TAG, key)
                    .description("Outbox 事件记录数（按状态）")
                    .register(meterRegistry);
            return value;
        });
    }
}
