package com.apex.scaffold.interfaces.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.apex.scaffold.infrastructure.circuitbreaker.CircuitBreakerService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * {@link ApiGatewayFilter} 的响应式异常路径测试。
 *
 * <p>重点保护网关指标修复：过滤器链异常必须向上游保留原始错误，并且只订阅一次，
 * 同时仍然登记一次 5xx 请求与错误指标。若错误处理重新执行 {@code chain.filter(exchange)}，
 * 该测试会通过调用次数直接发现重复执行。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("ApiGatewayFilter 单元测试")
class ApiGatewayFilterTest {

    /** 被测指标注册表。 */
    private SimpleMeterRegistry meterRegistry;

    /** 被测网关过滤器。 */
    private ApiGatewayFilter filter;

    /**
     * 初始化不依赖 Spring 容器的测试夹具。
     */
    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        filter = new ApiGatewayFilter(meterRegistry, mock(CircuitBreakerService.class));
    }

    /**
     * 验证过滤器链异常只执行一次并保留原始错误。
     */
    @Test
    @DisplayName("异常请求不得重新执行过滤器链")
    void filter_shouldPropagateErrorWithoutReinvokingChain() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/test/123").build());
        AtomicInteger chainCalls = new AtomicInteger();
        WebFilterChain chain = ignored -> {
            chainCalls.incrementAndGet();
            return Mono.error(new IllegalStateException("gateway failure"));
        };

        StepVerifier.create(filter.filter(exchange, chain))
                .expectErrorMessage("gateway failure")
                .verify();

        assertThat(chainCalls).hasValue(1);

        Counter requestCounter = meterRegistry
                .find("apex.gateway.request.count")
                .tags("method", "GET", "path", "/api/v1/test/{id}", "status", "500", "statusClass", "5xx")
                .counter();
        Counter errorCounter = meterRegistry
                .find("apex.gateway.error.count")
                .tags("method", "GET", "path", "/api/v1/test/{id}", "status", "500", "statusClass", "5xx")
                .counter();

        assertThat(requestCounter).isNotNull();
        assertThat(requestCounter.count()).isEqualTo(1.0d);
        assertThat(errorCounter).isNotNull();
        assertThat(errorCounter.count()).isEqualTo(1.0d);
    }

    /**
     * 验证异常请求的响应耗时仍通过网关响应时间 Timer 登记。
     */
    @Test
    @DisplayName("异常请求应记录响应耗时")
    void filter_shouldRecordErrorDuration() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/test").build());
        WebFilterChain chain = ignored -> Mono.error(new IllegalStateException("gateway failure"));

        StepVerifier.create(filter.filter(exchange, chain))
                .expectErrorMessage("gateway failure")
                .verify();

        Timer timer = meterRegistry
                .find("apex.gateway.response.time")
                .tags("method", "GET", "path", "/api/v1/test", "status", "500", "statusClass", "5xx")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1L);
    }
}
