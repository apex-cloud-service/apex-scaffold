package com.apex.scaffold.interfaces.monitor.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.apex.scaffold.interfaces.dto.monitor.HealthReportResponse;
import com.apex.scaffold.interfaces.dto.monitor.MonitorBusinessResponse;
import com.apex.scaffold.interfaces.dto.monitor.MonitorGovernanceResponse;
import com.apex.scaffold.interfaces.dto.monitor.MonitorMiddlewareResponse;
import com.apex.scaffold.interfaces.dto.monitor.MonitorOverviewResponse;
import com.apex.scaffold.interfaces.dto.monitor.MonitorSystemResponse;
import com.apex.scaffold.interfaces.monitor.dashboard.MonitorMetricsCollector;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 验证体检报告的状态判定、分组与总体结论规则。 */
class HealthReportAssemblerTest {

    private final MonitorMetricsCollector collector = mock(MonitorMetricsCollector.class);

    private HealthReportAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new HealthReportAssembler(collector);
    }

    @Test
    void allHealthyRowsProduceUpOverallStatus() {
        stubOverview(1_000, 100.0, 1_000.0, 0.10, 5_000.0, 0.0);
        stubMiddleware(healthyMysql(), healthyRedis(), runningMq(), enabledTracing());
        stubSystem(
                List.of(new MonitorSystemResponse.DiskUsage("ignored", "ext4", 100L, 50L, 50L, 0.10)),
                List.of(new MonitorSystemResponse.BlockingIoPool(4, 100, 1, 0, 10, 0.10, true)));
        stubCircuitBreakers(
                List.of(new MonitorGovernanceResponse.CircuitBreakerState("svc", "CLOSED", 0.0, 0.0, 1, 0, 0)));

        HealthReportResponse report = assembler.report();

        assertThat(report.overallStatus()).isEqualTo(HealthReportResponse.STATUS_UP);
        assertThat(report.scope()).isEqualTo(HealthReportAssembler.SCOPE_INSTANCE);
        assertThat(report.ruleVersion()).isEqualTo(HealthReportAssembler.RULE_VERSION);
        assertThat(report.summary()).isEqualTo("当前实例检查项均正常");
        assertThat(report.groups())
                .extracting(HealthReportResponse.HealthGroup::category)
                .containsExactly("LIVENESS", "DEPENDENCY", "BUSINESS_RISK", "CAPACITY");
        assertThat(report.counts().notConfigured()).isZero();
        assertThat(report.counts().unknown()).isZero();
        assertThat(report.counts().down()).isZero();
        assertThat(report.counts().degraded()).isZero();
        assertThat(itemStatus(report, "capacity.blocking_pool.jdbc")).isEqualTo(HealthReportResponse.STATUS_UP);
    }

    @Test
    void missingGaugeIsUnknownNotHealthy() {
        stubOverview(1_000, 100.0, 1_000.0, 0.10, 5_000.0, 0.0);
        stubMiddleware(
                new MonitorMiddlewareResponse.MysqlStats(
                        false, null, Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0, 0, 0),
                new MonitorMiddlewareResponse.RedisStats(false, false, Double.NaN, false, Double.NaN, 0, 0, Double.NaN),
                runningMq(),
                enabledTracing());
        stubSystem(List.of(), List.of());
        stubCircuitBreakers(List.of());

        HealthReportResponse report = assembler.report();

        String mysqlStatus = itemStatus(report, "dependency.mysql");
        String redisStatus = itemStatus(report, "dependency.redis");
        assertThat(mysqlStatus).isEqualTo(HealthReportResponse.STATUS_UNKNOWN);
        assertThat(redisStatus).isEqualTo(HealthReportResponse.STATUS_UNKNOWN);
        assertThat(itemStatus(report, "capacity.disk")).isEqualTo(HealthReportResponse.STATUS_UNKNOWN);
        assertThat(report.overallStatus()).isEqualTo(HealthReportResponse.STATUS_UNKNOWN);
        assertThat(report.summary()).contains("不能判定为健康");
    }

    @Test
    void disabledComponentsAreNotConfiguredAndExcludedFromOverall() {
        stubOverview(1_000, 100.0, 1_000.0, 0.10, 5_000.0, 0.0);
        stubMiddleware(
                healthyMysql(),
                healthyRedis(),
                new MonitorMiddlewareResponse.MqStats(false, "RocketMQ", "", null, "未接入", 0),
                new MonitorMiddlewareResponse.TracingStats(false, "SkyWalking", null, 1.0, 0, 0));
        stubSystem(
                List.of(new MonitorSystemResponse.DiskUsage("ignored", "ext4", 100L, 50L, 50L, 0.10)),
                List.of(new MonitorSystemResponse.BlockingIoPool(4, 100, 1, 0, 10, 0.10, true)));
        stubCircuitBreakers(List.of());

        HealthReportResponse report = assembler.report();

        assertThat(itemStatus(report, "dependency.mq")).isEqualTo(HealthReportResponse.STATUS_NOT_CONFIGURED);
        assertThat(itemStatus(report, "dependency.tracing")).isEqualTo(HealthReportResponse.STATUS_NOT_CONFIGURED);
        assertThat(itemStatus(report, "business.circuit_breaker"))
                .isEqualTo(HealthReportResponse.STATUS_NOT_CONFIGURED);
        assertThat(report.overallStatus()).isEqualTo(HealthReportResponse.STATUS_UP);
    }

    @Test
    void resourcesAtThresholdAreDownAndTakePrecedenceOverDegraded() {
        stubOverview(1_000, 100.0, 1_000.0, 0.99, 5_000.0, 0.0);
        stubMiddleware(
                new MonitorMiddlewareResponse.MysqlStats(true, "Druid", 9, 10, 1, 0.90, 0, 0, 0),
                healthyRedis(),
                runningMq(),
                enabledTracing());
        stubSystem(
                List.of(new MonitorSystemResponse.DiskUsage("ignored", "ext4", 100L, 1L, 1L, 0.97)),
                List.of(new MonitorSystemResponse.BlockingIoPool(4, 100, 4, 0, 10, 0.95, true)));
        stubCircuitBreakers(
                List.of(new MonitorGovernanceResponse.CircuitBreakerState("svc", "OPEN", 1.0, 0.0, 1, 1, 1)));

        HealthReportResponse report = assembler.report();

        assertThat(itemStatus(report, "capacity.heap")).isEqualTo(HealthReportResponse.STATUS_DOWN);
        assertThat(itemStatus(report, "capacity.disk")).isEqualTo(HealthReportResponse.STATUS_DOWN);
        assertThat(itemStatus(report, "dependency.mysql")).isEqualTo(HealthReportResponse.STATUS_DEGRADED);
        assertThat(itemStatus(report, "business.circuit_breaker")).isEqualTo(HealthReportResponse.STATUS_DEGRADED);
        assertThat(itemStatus(report, "capacity.blocking_pool.jdbc")).isEqualTo(HealthReportResponse.STATUS_DEGRADED);
        assertThat(report.overallStatus()).isEqualTo(HealthReportResponse.STATUS_DOWN);
    }

    @Test
    void withoutTrafficErrorRateIsUnknownRatherThanHardcodedUp() {
        stubOverview(1_000, 100.0, 1_000.0, 0.10, 0.0, 0.0);
        stubMiddleware(healthyMysql(), healthyRedis(), runningMq(), enabledTracing());
        stubSystem(List.of(), List.of());
        stubCircuitBreakers(List.of());

        HealthReportResponse report = assembler.report();

        HealthReportResponse.HealthCheckItem item = item(report, "business.api_error_rate");
        assertThat(item.status()).isEqualTo(HealthReportResponse.STATUS_UNKNOWN);
        assertThat(item.recommendation()).contains("尚无 API 流量");
    }

    @Test
    void itemsCarrySourceScopeAndTimestampAndHideMountPath() {
        stubOverview(1_000, 100.0, 1_000.0, 0.10, 5_000.0, 0.0);
        stubMiddleware(healthyMysql(), healthyRedis(), runningMq(), enabledTracing());
        stubSystem(
                List.of(new MonitorSystemResponse.DiskUsage("/private/host/mount", "ext4", 100L, 60L, 60L, 0.40)),
                List.of());
        stubCircuitBreakers(List.of());

        HealthReportResponse report = assembler.report();

        assertThat(report.generatedAt()).isNotBlank();
        assertThat(report.instance()).isNotBlank();
        assertThat(report.scopeNotice()).contains("不代表集群其他实例");
        report.groups().forEach(group -> group.items().forEach(item -> {
            assertThat(item.scope()).isEqualTo(HealthReportAssembler.SCOPE_INSTANCE);
            assertThat(item.service()).isEqualTo(report.service());
            assertThat(item.instance()).isEqualTo(report.instance());
            assertThat(item.sampledAt()).isNotBlank();
            assertThat(item.source()).isNotBlank();
            assertThat(item.evidence()).isNotBlank();
            assertThat(item.ruleVersion()).isEqualTo(HealthReportAssembler.RULE_VERSION);
        }));
        assertThat(item(report, "capacity.disk").evidence()).doesNotContain("/private/host/mount");
    }

    /* ==================== 构造与断言辅助 ==================== */

    private void stubOverview(
            long uptimeSeconds,
            double threadCount,
            double heapUsed,
            double heapUsedPercent,
            double totalRequests,
            double error5xx) {
        when(collector.overview())
                .thenReturn(new MonitorOverviewResponse(
                        "apex-system-service",
                        "1.3.0",
                        "UP",
                        uptimeSeconds,
                        "2026-10-28T00:00:00Z",
                        1_760_000_000_000L,
                        heapUsed,
                        1_000.0,
                        heapUsedPercent,
                        0.0,
                        0.1,
                        0.1,
                        threadCount,
                        0.0,
                        totalRequests,
                        0.0,
                        error5xx,
                        0.01,
                        0.01,
                        0.01,
                        0.0,
                        0.0,
                        0.0,
                        0.0,
                        0.0,
                        0.0,
                        0.0));
    }

    private void stubMiddleware(
            MonitorMiddlewareResponse.MysqlStats mysql,
            MonitorMiddlewareResponse.RedisStats redis,
            MonitorMiddlewareResponse.MqStats mq,
            MonitorMiddlewareResponse.TracingStats tracing) {
        when(collector.middleware())
                .thenReturn(new MonitorMiddlewareResponse(1_760_000_000_000L, mysql, redis, mq, tracing));
    }

    private void stubSystem(
            List<MonitorSystemResponse.DiskUsage> disk, List<MonitorSystemResponse.BlockingIoPool> pools) {
        MonitorSystemResponse.JvmStats jvm =
                new MonitorSystemResponse.JvmStats(1.0, 1.0, 1.0, 0.1, 1.0, 1.0, 0.0, 0, 1, 0, 0.0);
        MonitorSystemResponse.CpuStats cpu = new MonitorSystemResponse.CpuStats(0.1, 0.1, 4.0, -1);
        MonitorSystemResponse.ProcessStats process =
                new MonitorSystemResponse.ProcessStats(1_000, "2026-10-28T00:00:00Z", 1.0, 1.0, 20.0, 100.0);
        MonitorSystemResponse.ThreadStats threads = new MonitorSystemResponse.ThreadStats(10, 20, 5);
        Map<String, MonitorSystemResponse.BlockingIoPool> poolMap = new LinkedHashMap<>();
        if (!pools.isEmpty()) {
            poolMap.put("jdbc", pools.get(0));
        }
        when(collector.system())
                .thenReturn(new MonitorSystemResponse(
                        1_760_000_000_000L, jvm, cpu, process, threads, disk, List.of(), poolMap));
    }

    private void stubCircuitBreakers(List<MonitorGovernanceResponse.CircuitBreakerState> states) {
        when(collector.governance())
                .thenReturn(new MonitorGovernanceResponse(
                        1_760_000_000_000L,
                        new MonitorGovernanceResponse.SentinelStats(false, List.of(), List.of(), List.of(), List.of()),
                        new MonitorGovernanceResponse.SecurityCounters(0, 0, 0, 0, 0, 0, 0),
                        states,
                        new MonitorGovernanceResponse.GatewayStats(0, 0, 0, 0, 0, List.of())));
        when(collector.business()).thenReturn(mock(MonitorBusinessResponse.class));
    }

    private static MonitorMiddlewareResponse.MysqlStats healthyMysql() {
        return new MonitorMiddlewareResponse.MysqlStats(true, "Druid", 1, 10, 9, 0.10, 5, 0, 0);
    }

    private static MonitorMiddlewareResponse.RedisStats healthyRedis() {
        return new MonitorMiddlewareResponse.RedisStats(true, true, 1, true, 3, 0, 0, 1_760_000_000.0);
    }

    private static MonitorMiddlewareResponse.MqStats runningMq() {
        return new MonitorMiddlewareResponse.MqStats(true, "RocketMQ", "127.0.0.1:9876", "producer", "RUNNING", 1);
    }

    private static MonitorMiddlewareResponse.TracingStats enabledTracing() {
        return new MonitorMiddlewareResponse.TracingStats(true, "SkyWalking", "http://oap:11800", 1.0, 0, 0);
    }

    private static HealthReportResponse.HealthCheckItem item(HealthReportResponse report, String id) {
        return report.groups().stream()
                .flatMap(group -> group.items().stream())
                .filter(item -> item.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少检查项: " + id));
    }

    private static String itemStatus(HealthReportResponse report, String id) {
        return item(report, id).status();
    }
}
