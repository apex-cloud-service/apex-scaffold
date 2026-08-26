package com.apex.scaffold.infrastructure.circuitbreaker;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Resilience4j 熔断器配置属性单元测试。
 *
 * <p>验证 {@link CircuitBreakerProperties} 默认值和三种模式的配置正确性。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("CircuitBreakerProperties 熔断器配置属性测试")
class CircuitBreakerPropertiesTest {

    /**
     * 验证默认配置默认值。
     */
    @Test
    @DisplayName("默认模式参数应为均衡的保护阈值")
    void defaultModeHasCorrectDefaults() {
        CircuitBreakerProperties props = new CircuitBreakerProperties();
        CircuitBreakerProperties.ModeConfig dc = props.getDefaultConfig();

        assertThat(dc.getFailureRateThreshold()).isEqualTo(50f);
        assertThat(dc.getWaitDurationInOpenState()).isEqualTo(Duration.ofSeconds(60));
        assertThat(dc.getSlidingWindowSize()).isEqualTo(100);
        assertThat(dc.getMinimumNumberOfCalls()).isEqualTo(10);
        assertThat(dc.getPermittedCallsInHalfOpen()).isEqualTo(5);
    }

    /**
     * 验证严格模式默认值。
     */
    @Test
    @DisplayName("严格模式参数应为低阈值快熔断")
    void strictModeHasCorrectDefaults() {
        CircuitBreakerProperties props = new CircuitBreakerProperties();
        CircuitBreakerProperties.ModeConfig sc = props.getStrict();

        assertThat(sc.getFailureRateThreshold()).isEqualTo(30f);
        assertThat(sc.getWaitDurationInOpenState()).isEqualTo(Duration.ofSeconds(30));
        assertThat(sc.getSlidingWindowSize()).isEqualTo(50);
        assertThat(sc.getMinimumNumberOfCalls()).isEqualTo(5);
    }

    /**
     * 验证宽松模式默认值。
     */
    @Test
    @DisplayName("宽松模式参数应为高阈值长等待")
    void lenientModeHasCorrectDefaults() {
        CircuitBreakerProperties props = new CircuitBreakerProperties();
        CircuitBreakerProperties.ModeConfig lc = props.getLenient();

        assertThat(lc.getFailureRateThreshold()).isEqualTo(80f);
        assertThat(lc.getWaitDurationInOpenState()).isEqualTo(Duration.ofSeconds(120));
        assertThat(lc.getSlidingWindowSize()).isEqualTo(200);
        assertThat(lc.getMinimumNumberOfCalls()).isEqualTo(20);
        assertThat(lc.getPermittedCallsInHalfOpen()).isEqualTo(15);
    }

    /**
     * 验证 setter 方法。
     */
    @Test
    @DisplayName("setter 方法可以覆盖默认值")
    void settersCanOverrideDefaults() {
        CircuitBreakerProperties props = new CircuitBreakerProperties();
        CircuitBreakerProperties.ModeConfig dc = props.getDefaultConfig();

        dc.setFailureRateThreshold(60f);
        dc.setWaitDurationInOpenState(Duration.ofSeconds(90));
        dc.setSlidingWindowSize(200);

        assertThat(dc.getFailureRateThreshold()).isEqualTo(60f);
        assertThat(dc.getWaitDurationInOpenState()).isEqualTo(Duration.ofSeconds(90));
        assertThat(dc.getSlidingWindowSize()).isEqualTo(200);
        assertThat(dc.getMinimumNumberOfCalls()).isEqualTo(10);
    }
}
