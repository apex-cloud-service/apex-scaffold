package com.apex.scaffold.interfaces.filter;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * {@link MetricsWebFilter} 的单元测试。
 *
 * <p>验证指标采集、路径归一化以及排除路径跳过逻辑。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("MetricsWebFilter 单元测试")
class MetricsWebFilterTest {

    private SimpleMeterRegistry meterRegistry;
    private MetricsWebFilter filter;

    /**
     * 设置{@code up}。
     */
    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        filter = new MetricsWebFilter(meterRegistry);
    }

    @Test
    @DisplayName("filter 应记录归一化后的 API 指标")
    void filter_shouldRecordMetricsForNormalizedApiPath() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users/123/orders/456").build());
        WebFilterChain chain = requestExchange -> {
            requestExchange.getResponse().setStatusCode(HttpStatus.CREATED);
            return requestExchange.getResponse().setComplete();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        Timer timer = meterRegistry
                .find("apex.api.response.time")
                .tags("method", "GET", "path", "/api/users/{id}/orders/{id}", "status", "201")
                .timer();
        Counter counter = meterRegistry
                .find("apex.api.request.count")
                .tags("method", "GET", "path", "/api/users/{id}/orders/{id}", "status", "201")
                .counter();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0d);
    }

    @Test
    @DisplayName("filter 对排除路径不应记录指标")
    void filter_shouldSkipExcludedPath() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/health").build());
        WebFilterChain chain = requestExchange -> requestExchange.getResponse().setComplete();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(meterRegistry.find("apex.api.response.time").meters()).isEmpty();
        assertThat(meterRegistry.find("apex.api.request.count").meters()).isEmpty();
    }

    /**
     * 验证正常完成但未显式设置状态码时使用 HTTP 默认成功状态。
     */
    @Test
    @DisplayName("filter 在正常响应状态缺失时应使用 HTTP 默认 200 状态码")
    void filter_shouldUseHttpSuccessWhenStatusMissing() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/radar").build());
        WebFilterChain chain = requestExchange -> Mono.empty();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        Timer timer = meterRegistry
                .find("apex.api.response.time")
                .tags("method", "GET", "path", "/api/radar", "status", "200")
                .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }
}
