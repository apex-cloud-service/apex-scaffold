package com.apex.scaffold.interfaces.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 进程内存指标绑定器单元测试。
 *
 * <p>验证 {@link ProcessMemoryMetrics} 对 statm 的解析、缺失文件时的静默跳过，
 * 以及 Prometheus 导出指标名与 Grafana 面板查询一致。
 *
 * @author Apex Team
 */
@DisplayName("ProcessMemoryMetrics 进程内存指标测试")
class ProcessMemoryMetricsTest {

    /** 测试用临时目录，存放模拟的 statm 文件 */
    @TempDir
    Path tempDir;

    /**
     * 验证 {@code shouldRegisterBothGaugesWithByteValues} 场景下的预期行为。
     *
     * @throws IOException 当该操作无法按约定完成时
     */
    @Test
    @DisplayName("读取 statm 后应注册常驻与虚拟内存 gauge，且值换算为字节")
    void shouldRegisterBothGaugesWithByteValues() throws IOException {
        Path statm = tempDir.resolve("statm");
        // 虚拟 1000 页、常驻 500 页
        Files.writeString(statm, "1000 500 0 0 0 0 0\n", StandardCharsets.UTF_8);

        MeterRegistry registry = new SimpleMeterRegistry();
        new ProcessMemoryMetrics(statm).bindTo(registry);

        assertThat(registry.find("process.resident.memory.bytes").gauge()).isNotNull();
        assertThat(registry.find("process.virtual.memory.bytes").gauge()).isNotNull();
        assertThat(registry.find("process.resident.memory.bytes").gauge().value())
                .isEqualTo(500 * 4096.0);
        assertThat(registry.find("process.virtual.memory.bytes").gauge().value())
                .isEqualTo(1000 * 4096.0);
    }

    /**
     * 验证 {@code shouldSkipWhenStatmMissing} 场景下的预期行为。
     *
     */
    @Test
    @DisplayName("statm 文件不存在时应静默跳过，不注册任何指标")
    void shouldSkipWhenStatmMissing() {
        Path missing = tempDir.resolve("not-exist");
        MeterRegistry registry = new SimpleMeterRegistry();

        new ProcessMemoryMetrics(missing).bindTo(registry);

        assertThat(registry.find("process.resident.memory.bytes").gauge()).isNull();
        assertThat(registry.find("process.virtual.memory.bytes").gauge()).isNull();
    }

    /**
     * 验证 {@code shouldReturnZeroOnMalformedStatm} 场景下的预期行为。
     *
     * @throws IOException 当该操作无法按约定完成时
     */
    @Test
    @DisplayName("statm 内容异常时应返回 0 且不抛错")
    void shouldReturnZeroOnMalformedStatm() throws IOException {
        Path statm = tempDir.resolve("statm");
        Files.writeString(statm, "not-a-number\n", StandardCharsets.UTF_8);

        MeterRegistry registry = new SimpleMeterRegistry();
        new ProcessMemoryMetrics(statm).bindTo(registry);

        assertThat(registry.find("process.resident.memory.bytes").gauge().value())
                .isZero();
        assertThat(registry.find("process.virtual.memory.bytes").gauge().value())
                .isZero();
    }

    /**
     * 验证 {@code shouldExportPrometheusMetricNames} 场景下的预期行为。
     *
     * @throws IOException 当该操作无法按约定完成时
     */
    @Test
    @DisplayName("Prometheus 导出指标名应与 Grafana 面板查询一致")
    void shouldExportPrometheusMetricNames() throws IOException {
        Path statm = tempDir.resolve("statm");
        Files.writeString(statm, "1000 500 0 0 0 0 0\n", StandardCharsets.UTF_8);

        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        new ProcessMemoryMetrics(statm).bindTo(registry);

        String scrape = registry.scrape();
        assertThat(scrape).contains("process_resident_memory_bytes");
        assertThat(scrape).contains("process_virtual_memory_bytes");
    }
}
