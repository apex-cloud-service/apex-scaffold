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
 * <p>迁移目标：由 SkyWalking Agent 负责跨进程链路，业务侧 {@link TraceFilter} 负责把请求入口的
 * 统一 traceId 同时写入 SkyWalking 标准 MDC 键 {@code tid}，使 JSON 日志（{@code %X{tid}}）
 * 与 SkyWalking 链路、响应头 {@code X-Trace-Id} 三者可相互关联。
 *
 * <p>本测试不依赖任何 Brave/Zipkin 类型，仅断言迁移后应具备的“统一 traceId”契约。
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
    @DisplayName("请求处理期间 MDC 键 tid 应等于响应头统一 traceId（供 SkyWalking 关联日志）")
    void filter_shouldPutUnifiedTraceIdIntoTidMdc() {
        String[] observedTid = {null};
        when(chain.filter(any(ServerWebExchange.class)))
                .thenAnswer(invocation -> {
                    observedTid[0] = MDC.get(TID_MDC_KEY);
                    return Mono.empty();
                });

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/order").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        String responseTraceId = runAndGetResponseTraceId(exchange);

        assertThat(exchange.getResponse().getHeaders().getFirst(TRACE_ID_HEADER)).isNotBlank();
        assertThat(observedTid[0])
                .as("SkyWalking 标准 MDC 键 tid 应与响应头 X-Trace-Id 指向同一 traceId")
                .isEqualTo(responseTraceId);
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
        assertThat(exchange.getResponse().getHeaders().getFirst(TRACE_ID_HEADER)).isNotBlank();
    }

    private String runAndGetResponseTraceId(ServerWebExchange exchange) {
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        return exchange.getResponse().getHeaders().getFirst(TRACE_ID_HEADER);
    }
}
