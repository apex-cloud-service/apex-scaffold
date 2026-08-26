package com.apex.scaffold.infrastructure.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Outbox 积压指标上报器单元测试
 *
 * @author Apex Team
 */
class OutboxMetricsReporterTest {

    /** 指标注册表。 */
    private MeterRegistry registry;

    /** Outbox 存储 mock。 */
    private DomainEventOutboxStore outboxStore;

    /** 被测上报器。 */
    private OutboxMetricsReporter reporter;

    /** 初始化测试环境。 */
    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        outboxStore = mock(DomainEventOutboxStore.class);
        reporter = new OutboxMetricsReporter(outboxStore, registry);
    }

    /** 刷新后应注册各状态 Gauge 并写入数量。 */
    @Test
    void refreshRegistersGaugesWithStatusCounts() {
        Map<String, Long> counts = new HashMap<>();
        counts.put("PENDING", 5L);
        counts.put("DEAD", 2L);
        when(outboxStore.countByStatus()).thenReturn(counts);

        reporter.refresh();

        assertEquals(
                5.0,
                registry.get("apex.outbox.records")
                        .tag("status", "PENDING")
                        .gauge()
                        .value());
        assertEquals(
                2.0,
                registry.get("apex.outbox.records")
                        .tag("status", "DEAD")
                        .gauge()
                        .value());
        assertEquals(1.0, registry.get("apex.outbox.metrics.up").gauge().value());
        assertEquals(3, registry.getMeters().size(), "应注册 2 个状态 Gauge 和 1 个查询可用性 Gauge");
    }

    /** 重复刷新应更新 Gauge 值且不重复注册指标。 */
    @Test
    void refreshUpdatesValuesIdempotently() {
        Map<String, Long> first = Map.of("PENDING", 5L);
        Map<String, Long> second = Map.of("PENDING", 8L);
        when(outboxStore.countByStatus()).thenReturn(first).thenReturn(second);

        reporter.refresh();
        reporter.refresh();

        assertEquals(
                8.0,
                registry.get("apex.outbox.records")
                        .tag("status", "PENDING")
                        .gauge()
                        .value());
        assertEquals(2, registry.getMeters().size(), "重复刷新不应重复注册指标");
    }

    /** 统计为空时应明确上报成功，并保持状态指标无数据。 */
    @Test
    void refreshWithEmptyCountsRegistersNothing() {
        when(outboxStore.countByStatus()).thenReturn(Map.of());

        reporter.refresh();

        assertEquals(1.0, registry.get("apex.outbox.metrics.up").gauge().value());
        assertEquals(1, registry.getMeters().size());
    }
}
