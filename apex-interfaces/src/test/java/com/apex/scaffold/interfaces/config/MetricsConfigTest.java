package com.apex.scaffold.interfaces.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

/**
 * MetricsConfig 安全指标配置测试
 *
 * <p>测试安全指标配置的各项 Bean 是否正确创建，包括 JVM 指标、安全计数器
 * 以及 Gauge 指标绑定等。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("MetricsConfig 安全指标配置测试")
class MetricsConfigTest {

    private static final String[] EXPECTED_COUNTER_NAMES = {
        "apex.security.rate.limit.rejected",
        "apex.security.ip.blocked",
        "apex.security.login.failed",
        "apex.security.xss.intercepted",
        "apex.security.anti.replay.rejected"
    };

    private static final String[] EXPECTED_GAUGE_NAMES = {
        "apex.security.ip.blacklist.size", "apex.security.token.blacklist.size", "apex.security.active.sessions"
    };

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    private final ReactiveStringRedisTemplate redisTemplate = mock(ReactiveStringRedisTemplate.class);

    private final MetricsConfig metricsConfig = new MetricsConfig(redisTemplate);

    /**
     * 执行 {@code shouldCreateJvmMemoryMetrics} 操作。
     */
    @Test
    @DisplayName("应该创建JVM内存指标")
    void shouldCreateJvmMemoryMetrics() {
        JvmMemoryMetrics metrics = metricsConfig.jvmMemoryMetrics(meterRegistry);

        assertThat(metrics).isNotNull();
    }

    /**
     * 执行 {@code shouldCreateJvmThreadMetrics} 操作。
     */
    @Test
    @DisplayName("应该创建JVM线程指标")
    void shouldCreateJvmThreadMetrics() {
        JvmThreadMetrics metrics = metricsConfig.jvmThreadMetrics(meterRegistry);

        assertThat(metrics).isNotNull();
    }

    /**
     * 执行 {@code shouldCreateProcessorMetrics} 操作。
     */
    @Test
    @DisplayName("应该创建处理器指标")
    void shouldCreateProcessorMetrics() {
        ProcessorMetrics metrics = metricsConfig.processorMetrics(meterRegistry);

        assertThat(metrics).isNotNull();
    }

    /**
     * 执行 {@code shouldCreateSecurityCounters} 操作。
     */
    @Test
    @DisplayName("应该创建安全计数器并包含正确名称")
    void shouldCreateSecurityCounters() {
        Counter counter1 = metricsConfig.rateLimitRejectedCounter(meterRegistry);
        Counter counter2 = metricsConfig.ipBlockedCounter(meterRegistry);
        Counter counter3 = metricsConfig.loginFailedCounter(meterRegistry);
        Counter counter4 = metricsConfig.xssInterceptedCounter(meterRegistry);
        Counter counter5 = metricsConfig.antiReplayRejectedCounter(meterRegistry);

        assertThat(counter1).isNotNull();
        assertThat(counter2).isNotNull();
        assertThat(counter3).isNotNull();
        assertThat(counter4).isNotNull();
        assertThat(counter5).isNotNull();

        assertThat(counter1.getId().getName()).isEqualTo("apex.security.rate.limit.rejected");
        assertThat(counter2.getId().getName()).isEqualTo("apex.security.ip.blocked");
        assertThat(counter3.getId().getName()).isEqualTo("apex.security.login.failed");
        assertThat(counter4.getId().getName()).isEqualTo("apex.security.xss.intercepted");
        assertThat(counter5.getId().getName()).isEqualTo("apex.security.anti.replay.rejected");
    }

    /**
     * 执行 {@code shouldCreateSecurityMeterBinder} 操作。
     */
    @Test
    @DisplayName("安全指标MeterBinder应绑定gauge指标")
    void shouldCreateSecurityMeterBinder() {
        MeterBinder meterBinder = metricsConfig.securityMetrics();

        assertThat(meterBinder).isNotNull();

        meterBinder.bindTo(meterRegistry);
    }
}
