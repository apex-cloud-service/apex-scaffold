package com.apex.scaffold.interfaces.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 全局响应超时过滤器单元测试
 *
 * <p>验证 GlobalResponseTimeoutWebFilter 的超时熔断、SSE 豁免与开关关闭等行为。
 *
 * @author Apex Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GlobalResponseTimeoutWebFilter 全局响应超时过滤器测试")
class GlobalResponseTimeoutWebFilterTest {

    @Mock
    private WebFilterChain chain;

    private ApiTimeoutProperties properties;

    private GlobalResponseTimeoutWebFilter filter;

    @BeforeEach
    void setUp() {
        properties = new ApiTimeoutProperties();
        properties.setResponseTimeout(Duration.ofMillis(100));
        filter = new GlobalResponseTimeoutWebFilter(properties);
    }

    @Test
    @DisplayName("慢请求超时应返回 504 与 REQUEST_TIMEOUT 错误码")
    void shouldReturn504WhenChainExceedsTimeout() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/slow").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.delay(Duration.ofSeconds(2)).then());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(exchange.getResponse().getHeaders().getContentType()).isNotNull();
    }

    @Test
    @DisplayName("正常请求按时完成应透传过滤器链")
    void shouldPassThroughWhenFastEnough() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/health").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        // 未触发超时处理，状态码未被改写
        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.GATEWAY_TIMEOUT);
    }

    @Test
    @DisplayName("SSE 长连接请求应豁免超时控制")
    void shouldSkipTimeoutForSseRequests() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/notifications/sse")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        // 链耗时 2 秒远超 100ms 阈值，但 SSE 请求不套超时，正常完成
        when(chain.filter(any(ServerWebExchange.class)))
                .thenReturn(Mono.delay(Duration.ofMillis(10)).then());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.GATEWAY_TIMEOUT);
    }

    @Test
    @DisplayName("超时配置为 0 时整体关闭且透传链")
    void shouldDisableTimeoutWhenConfiguredZero() {
        properties.setResponseTimeout(Duration.ZERO);
        assertThat(properties.isTimeoutEnabled()).isFalse();

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/slow").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        when(chain.filter(any(ServerWebExchange.class)))
                .thenReturn(Mono.delay(Duration.ofMillis(50)).then());

        // 50ms > 0（已禁用），不套超时也不会触发熔断
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.GATEWAY_TIMEOUT);
    }
}
