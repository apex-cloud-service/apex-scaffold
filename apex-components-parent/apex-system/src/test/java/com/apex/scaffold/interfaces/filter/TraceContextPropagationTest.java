package com.apex.scaffold.interfaces.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 链路上下文传播测试（Zipkin/Brave → SkyWalking 迁移后）.
 *
 * <p>SkyWalking Agent 负责跨进程链路；业务侧 {@link TraceFilter} 传播
 * {@code X-Trace-Id}，不得覆盖或清理 Agent 的 {@code tid}。
 *
 * <p>本测试断言两类标识相互独立，以及 SkyWalking 传播头保持不变。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TraceContextPropagation 链路上下文传播（迁移后统一 traceId）")
class TraceContextPropagationTest {

    /** SkyWalking 标准 MDC 键 */
    private static final String TID_MDC_KEY = "tid";

    /** 既有业务追踪头 */
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    /** SkyWalking Agent 跨进程传播头 */
    private static final String SW8_HEADER = "sw8";

    @Mock
    private WebFilterChain chain;

    private final TraceFilter filter = new TraceFilter();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("业务 traceId 应与 Agent tid 分离且保留既有 tid")
    void filter_shouldPreserveAgentTid() {
        MDC.put(TID_MDC_KEY, "agent-trace");
        String[] observedTid = {null};
        when(chain.filter(any(ServerWebExchange.class))).thenAnswer(invocation -> {
            observedTid[0] = MDC.get(TID_MDC_KEY);
            return Mono.empty();
        });

        MockServerHttpRequest request =
                MockServerHttpRequest.get("/api/v1/order").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        String responseTraceId = runAndGetResponseTraceId(exchange);

        assertThat(exchange.getResponse().getHeaders().getFirst(TRACE_ID_HEADER))
                .isNotBlank();
        assertThat(observedTid[0]).isEqualTo("agent-trace").isNotEqualTo(responseTraceId);
        assertThat(MDC.get(TID_MDC_KEY)).isEqualTo("agent-trace");
    }

    @Test
    @DisplayName("应透传上游 sw8 头（SkyWalking 跨进程链路）且仍生成 X-Trace-Id")
    void filter_shouldKeepSw8HeaderAndGenerateTraceId() {
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/order")
                .header(SW8_HEADER, "1-abc-trace-2-3")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getRequest().getHeaders().getFirst(SW8_HEADER)).isEqualTo("1-abc-trace-2-3");
        assertThat(exchange.getResponse().getHeaders().getFirst(TRACE_ID_HEADER))
                .isNotBlank();
    }

    private String runAndGetResponseTraceId(ServerWebExchange exchange) {
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        return exchange.getResponse().getHeaders().getFirst(TRACE_ID_HEADER);
    }
}
