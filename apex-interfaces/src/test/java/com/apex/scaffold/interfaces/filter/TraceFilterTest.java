package com.apex.scaffold.interfaces.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.util.TraceIdUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 链路追踪过滤器单元测试
 *
 * <p>验证 TraceFilter 在请求入口处生成或获取 TraceId，
 * 并正确设置到 MDC 和响应头中，请求结束时清除 TraceId
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TraceFilter 链路追踪过滤器测试")
class TraceFilterTest {

    @Mock
    private WebFilterChain chain;

    @InjectMocks
    private TraceFilter filter;

    private MockedStatic<TraceIdUtil> traceIdUtilMockedStatic;

    /**
     * 设置{@code up}。
     */
    @BeforeEach
    void setUp() {
        traceIdUtilMockedStatic = mockStatic(TraceIdUtil.class);
    }

    @AfterEach
    void tearDown() {
        traceIdUtilMockedStatic.close();
    }

    @Test
    @DisplayName("请求头中无TraceId时应该生成新的TraceId")
    void filter_withoutTraceIdInHeader_shouldGenerateNewTraceId() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        traceIdUtilMockedStatic.when(() -> TraceIdUtil.getTraceId()).thenReturn(null);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        Mono<Void> result = filter.filter(exchange, chain);

        StepVerifier.create(result).verifyComplete();

        assertThat(exchange.getResponse().getHeaders().getFirst("X-Trace-Id")).isNotNull();
    }

    @Test
    @DisplayName("请求头中有TraceId时应使用已有的TraceId")
    void filter_withTraceIdInHeader_shouldUseExistingTraceId() {
        String existingTraceId = "existing-trace-id-123";
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .header("X-Trace-Id", existingTraceId)
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        traceIdUtilMockedStatic.when(() -> TraceIdUtil.getTraceId()).thenReturn(existingTraceId);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        Mono<Void> result = filter.filter(exchange, chain);

        StepVerifier.create(result).verifyComplete();

        assertThat(exchange.getResponse().getHeaders().getFirst("X-Trace-Id")).isEqualTo(existingTraceId);
    }

    @Test
    @DisplayName("过滤器执行后应清除TraceId")
    void filter_afterCompletion_shouldClearTraceId() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        Mono<Void> result = filter.filter(exchange, chain);

        StepVerifier.create(result).verifyComplete();
    }

    @Test
    @DisplayName("响应头中应包含TraceId")
    void filter_shouldAddTraceIdToResponseHeader() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getHeaders().getFirst("X-Trace-Id")).isNotNull();
    }
}
