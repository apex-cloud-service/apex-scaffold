package com.apex.scaffold.interfaces.filter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 请求/响应日志过滤器单元测试
 *
 * <p>验证 LoggingFilter 的过滤逻辑，包括正常请求日志记录和健康检查等路径跳过。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LoggingFilter 日志过滤器测试")
class LoggingFilterTest {

    @Mock
    private WebFilterChain chain;

    private LoggingFilter filter;

    /**
     * 设置{@code up}。
     */
    @BeforeEach
    void setUp() {
        filter = new LoggingFilter();
    }

    // ==================== filter_NormalRequest ====================

    /**
     * 执行 {@code filter_NormalRequest} 操作。
     */
    @Test
    @DisplayName("filter_NormalRequest: verify chain.filter called for normal request")
    void filter_NormalRequest() {
        MockServerHttpRequest request =
                MockServerHttpRequest.get("/api/v1/user/profile").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(any(ServerWebExchange.class));
    }

    // ==================== filter_ShouldSkipHealthCheck ====================

    /**
     * 执行 {@code filter_ShouldSkipHealthCheck} 操作。
     */
    @Test
    @DisplayName("filter_ShouldSkipHealthCheck: verify health check paths skipped")
    void filter_ShouldSkipHealthCheck() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/health").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(any(ServerWebExchange.class));
    }

    // ==================== filter_ShouldSkipActuator ====================

    /**
     * 执行 {@code filter_ShouldSkipActuator} 操作。
     */
    @Test
    @DisplayName("filter_ShouldSkipActuator: verify actuator paths skipped")
    void filter_ShouldSkipActuator() {
        MockServerHttpRequest request =
                MockServerHttpRequest.get("/actuator/health").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(any(ServerWebExchange.class));
    }

    // ==================== filter_ShouldSkipSwagger ====================

    /**
     * 执行 {@code filter_ShouldSkipSwagger} 操作。
     */
    @Test
    @DisplayName("filter_ShouldSkipSwagger: verify swagger paths skipped")
    void filter_ShouldSkipSwagger() {
        MockServerHttpRequest request =
                MockServerHttpRequest.get("/swagger-ui/index.html").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(any(ServerWebExchange.class));
    }
}
