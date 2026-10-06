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

    @Test
    void failureLogRedactsQueryAndBoundsMetadata() {
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(LoggingFilter.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            var request = MockServerHttpRequest.get("/api/login?password=secret-value&access_token=token-value")
                    .header("User-Agent", "x".repeat(10000))
                    .header("Referer", "https://example.com/?code=private-code")
                    .build();
            when(chain.filter(any())).thenReturn(Mono.error(new IllegalStateException("password=exception-secret")));
            StepVerifier.create(filter.filter(MockServerWebExchange.from(request), chain))
                    .expectError(IllegalStateException.class)
                    .verify();
            org.assertj.core.api.Assertions.assertThat(appender.list).isNotEmpty();
            var event = appender.list.get(0);
            org.assertj.core.api.Assertions.assertThat(event.getFormattedMessage())
                    .doesNotContain("secret-value", "token-value", "private-code", "exception-secret")
                    .hasSizeLessThan(2048);
            org.assertj.core.api.Assertions.assertThat(event.getThrowableProxy())
                    .isNull();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void assemblyAndCancellationPreserveMdc() {
        org.slf4j.MDC.put("traceId", "outer-trace");
        try {
            when(chain.filter(any())).thenReturn(Mono.never());
            var result = filter.filter(
                    MockServerWebExchange.from(
                            MockServerHttpRequest.get("/api/user").build()),
                    chain);
            org.assertj.core.api.Assertions.assertThat(org.slf4j.MDC.get("traceId"))
                    .isEqualTo("outer-trace");
            StepVerifier.create(result).thenCancel().verify();
            org.assertj.core.api.Assertions.assertThat(org.slf4j.MDC.get("traceId"))
                    .isEqualTo("outer-trace");
        } finally {
            org.slf4j.MDC.clear();
        }
    }
}
