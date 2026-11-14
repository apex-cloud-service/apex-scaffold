package com.apex.scaffold.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.common.util.TraceIdUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * {@link TraceWebFilter} 单元测试.
 *
 * <p>覆盖迁移方案 §5.3 过滤器链第 1 位的核心职责：TraceId 生成 / 透传、
 * 请求头与响应头回写，以及过滤器顺序校验。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@DisplayName("TraceWebFilter 链路追踪过滤器测试")
class TraceWebFilterTest {

    private final TraceWebFilter filter = new TraceWebFilter();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    /**
     * 构造直通链路，捕获被改写的交换对象。
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
        @DisplayName("getOrder 应返回 -1，保证链路最先执行")
        void shouldReturnExpectedOrder() {
            assertThat(filter.getOrder()).isEqualTo(TraceWebFilter.ORDER).isEqualTo(-1);
        }
    }

    @Nested
    @DisplayName("TraceId 注入与透传")
    class TraceIdTests {

        @Test
        @DisplayName("客户端未携带 X-Trace-Id 时应由网关注入新 TraceId")
        void shouldGenerateTraceIdWhenAbsent() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/v1/orders").build());
            ServerWebExchange[] holder = new ServerWebExchange[1];

            filter.filter(exchange, capturingChain(holder)).block();

            String injected = holder[0].getRequest().getHeaders().getFirst(TraceWebFilter.HEADER_TRACE_ID);
            assertThat(injected).isNotNull().hasSize(32).matches("^[0-9a-f]{32}$");
        }

        @Test
        @DisplayName("客户端已携带 X-Trace-Id 时应原样透传")
        void shouldPassthroughIncomingTraceId() {
            String incoming = "abcdef0123456789abcdef0123456789";
            MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/orders")
                    .header(TraceWebFilter.HEADER_TRACE_ID, incoming)
                    .build());
            ServerWebExchange[] holder = new ServerWebExchange[1];

            filter.filter(exchange, capturingChain(holder)).block();

            assertThat(holder[0].getRequest().getHeaders().getFirst(TraceWebFilter.HEADER_TRACE_ID))
                    .isEqualTo(incoming);
        }

        @Test
        @DisplayName("响应头应回写 TraceId，便于前端上报")
        void shouldWriteTraceIdToResponseHeader() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/v1/orders").build());
            ServerWebExchange[] holder = new ServerWebExchange[1];

            filter.filter(exchange, capturingChain(holder)).block();

            String responseTraceId = exchange.getResponse().getHeaders().getFirst(TraceWebFilter.HEADER_TRACE_ID);
            assertThat(responseTraceId).isNotNull().hasSize(32);
        }
    }

    @Nested
    @DisplayName("常量约定")
    class ConstantTests {

        @Test
        @DisplayName("请求头名称应为 X-Trace-Id")
        void shouldHaveCorrectHeaderName() {
            assertThat(TraceWebFilter.HEADER_TRACE_ID).isEqualTo("X-Trace-Id");
        }

        @Test
        @DisplayName("TraceId 生成规则与 TraceIdUtil 保持一致")
        void shouldShareTraceIdUtilRule() {
            assertThat(TraceIdUtil.generateTraceId()).hasSize(32);
        }
    }
}
