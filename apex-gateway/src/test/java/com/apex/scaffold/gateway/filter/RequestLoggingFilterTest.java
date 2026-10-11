package com.apex.scaffold.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * {@link RequestLoggingFilter} 单元测试.
 *
 * <p>覆盖迁移方案 §5.3 过滤器链第 7 位（order = 50）的职责：链路末端观测、
 * 请求原样透传（不修改交换）以及顺序校验。脱敏逻辑为内部实现，通过可观测的透传语义间接验证。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@DisplayName("RequestLoggingFilter 访问日志过滤器测试")
class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    /**
     * 构造直通链路，捕获被传入的交换对象。
     */
    private GatewayFilterChain capturingChain(ServerWebExchange[] holder) {
        return exchange -> {
            holder[0] = exchange;
            return Mono.empty();
        };
    }

    @Nested
    @DisplayName("过滤器顺序")
    class OrderTests {

        @Test
        @DisplayName("getOrder 应返回 50，位于链路末端")
        void shouldReturnExpectedOrder() {
            assertThat(filter.getOrder()).isEqualTo(RequestLoggingFilter.ORDER).isEqualTo(50);
        }
    }

    @Nested
    @DisplayName("链路透传")
    class PassthroughTests {

        @Test
        @DisplayName("过滤后应原样透传交换对象（不改写请求）")
        void shouldPassThroughUnchanged() {
            ServerWebExchange[] holder = new ServerWebExchange[1];
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/v1/orders").build());

            StepVerifier.create(filter.filter(exchange, capturingChain(holder))).verifyComplete();

            assertThat(holder[0]).isSameAs(exchange);
        }

        @Test
        @DisplayName("携带敏感头的请求应正常完成，不抛异常")
        void shouldCompleteWithSensitiveHeaders() {
            MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/orders")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer secret-token")
                    .header(HttpHeaders.COOKIE, "session=abc")
                    .header(HttpHeaders.USER_AGENT, "junit")
                    .build());

            StepVerifier.create(filter.filter(exchange, e -> Mono.empty())).verifyComplete();
        }

        @Test
        @DisplayName("链路异常时也应完成 finally 清理（不吞异常）")
        void shouldPropagateChainError() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/v1/orders").build());

            StepVerifier.create(filter.filter(exchange, e -> Mono.error(new IllegalStateException("boom"))))
                    .expectError(IllegalStateException.class)
                    .verify();
        }
    }
}
