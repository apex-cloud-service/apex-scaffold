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
            var response = new org.springframework.mock.http.server.reactive.MockServerHttpResponse();
            var handler =
                    httpHandler(exchange -> Mono.error(new IllegalArgumentException("password=exception-secret")));
            StepVerifier.create(handler.handle(request, response)).verifyComplete();
            org.assertj.core.api.Assertions.assertThat(response.isCommitted()).isTrue();
            org.assertj.core.api.Assertions.assertThat(response.getStatusCode())
                    .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
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
    void mappedArgumentErrorLogsFinalBadRequestStatusOnce() {
        assertCommittedLog(
                exchange -> Mono.error(new IllegalArgumentException("invalid argument")),
                org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    @Test
    void unknownErrorLogsFinalInternalServerErrorStatusOnce() {
        assertCommittedLog(
                exchange -> Mono.error(new RuntimeException("unexpected failure")),
                org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void normalResponseLogsOkStatusOnce() {
        assertCommittedLog(exchange -> Mono.empty(), org.springframework.http.HttpStatus.OK);
    }

    @Test
    void errorAfterCommitDoesNotLogAgain() {
        assertCommittedLog(
                exchange -> exchange.getResponse()
                        .setComplete()
                        .then(Mono.error(new IllegalStateException("failure after commit"))),
                org.springframework.http.HttpStatus.OK,
                true);
    }

    @Test
    void repeatedSubscriptionBeforeCommitLogsOnlyOnce() {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(LoggingFilter.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            var response = new org.springframework.mock.http.server.reactive.MockServerHttpResponse();
            var handler = httpHandler(
                    exchange -> Mono.empty(),
                    (exchange, next) -> next.filter(exchange).repeat(1).then());
            StepVerifier.create(handler.handle(
                            MockServerHttpRequest.get("/api/repeated").build(), response))
                    .verifyComplete();
            org.assertj.core.api.Assertions.assertThat(response.isCommitted()).isTrue();
            org.assertj.core.api.Assertions.assertThat(appender.list).hasSize(1);
            org.assertj.core.api.Assertions.assertThat(appender.list.get(0).getMDCPropertyMap())
                    .containsEntry("status", "200");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void cancellationBeforeCommitDoesNotLog() {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(LoggingFilter.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        var previous = org.slf4j.MDC.getCopyOfContextMap();
        var original = java.util.Map.of("traceId", "outer-trace", "requestId", "outer-request");
        try {
            org.slf4j.MDC.setContextMap(original);
            var response = new org.springframework.mock.http.server.reactive.MockServerHttpResponse();
            StepVerifier.create(httpHandler(exchange -> Mono.never())
                            .handle(MockServerHttpRequest.get("/api/cancelled").build(), response))
                    .thenCancel()
                    .verify();
            org.assertj.core.api.Assertions.assertThat(response.isCommitted()).isFalse();
            org.assertj.core.api.Assertions.assertThat(appender.list).isEmpty();
            org.assertj.core.api.Assertions.assertThat(org.slf4j.MDC.getCopyOfContextMap())
                    .isEqualTo(original);
        } finally {
            if (previous == null) {
                org.slf4j.MDC.clear();
            } else {
                org.slf4j.MDC.setContextMap(previous);
            }
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private void assertCommittedLog(
            org.springframework.web.server.WebHandler webHandler, org.springframework.http.HttpStatus status) {
        assertCommittedLog(webHandler, status, false);
    }

    private void assertCommittedLog(
            org.springframework.web.server.WebHandler webHandler,
            org.springframework.http.HttpStatus status,
            boolean errorAfterCommit) {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(LoggingFilter.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            var response = new org.springframework.mock.http.server.reactive.MockServerHttpResponse();
            var verifier = StepVerifier.create(httpHandler(webHandler)
                    .handle(MockServerHttpRequest.get("/api/result").build(), response));
            if (errorAfterCommit) {
                verifier.verifyError(IllegalStateException.class);
            } else {
                verifier.verifyComplete();
            }
            org.assertj.core.api.Assertions.assertThat(response.isCommitted()).isTrue();
            org.assertj.core.api.Assertions.assertThat(
                            response.getStatusCode() == null
                                    ? org.springframework.http.HttpStatus.OK
                                    : response.getStatusCode())
                    .isEqualTo(status);
            org.assertj.core.api.Assertions.assertThat(appender.list).hasSize(1);
            var event = appender.list.get(0);
            org.assertj.core.api.Assertions.assertThat(event.getMDCPropertyMap())
                    .containsEntry("status", String.valueOf(status.value()));
            org.assertj.core.api.Assertions.assertThat(event.getFormattedMessage())
                    .contains("status=" + status.value());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private org.springframework.http.server.reactive.HttpHandler httpHandler(
            org.springframework.web.server.WebHandler webHandler) {
        return httpHandler(webHandler, (exchange, next) -> next.filter(exchange));
    }

    private org.springframework.http.server.reactive.HttpHandler httpHandler(
            org.springframework.web.server.WebHandler webHandler,
            org.springframework.web.server.WebFilter outerFilter) {
        var exceptionHandler = new com.apex.scaffold.interfaces.exception.GlobalExceptionHandler(
                org.mockito.Mockito.mock(com.apex.scaffold.interfaces.monitor.ExceptionMonitor.class),
                org.mockito.Mockito.mock(com.apex.scaffold.interfaces.util.MessageUtils.class));
        return org.springframework.web.server.adapter.WebHttpHandlerBuilder.webHandler(webHandler)
                .filter(outerFilter, filter)
                .exceptionHandler(exceptionHandler.webExceptionHandler())
                .build();
    }

    @Test
    void requestLogEncodesFieldsAndRestoresMdc() throws Exception {
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(LoggingFilter.class);
        net.logstash.logback.encoder.LogstashEncoder encoder = new net.logstash.logback.encoder.LogstashEncoder();
        encoder.setContext(logger.getLoggerContext());
        encoder.start();
        java.util.concurrent.atomic.AtomicReference<String> captured =
                new java.util.concurrent.atomic.AtomicReference<>();
        ch.qos.logback.core.AppenderBase<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.AppenderBase<>() {
                    @Override
                    protected void append(ch.qos.logback.classic.spi.ILoggingEvent event) {
                        captured.set(new String(encoder.encode(event), java.nio.charset.StandardCharsets.UTF_8));
                    }
                };
        appender.setContext(logger.getLoggerContext());
        appender.start();
        logger.addAppender(appender);
        java.util.Map<String, String> previous = org.slf4j.MDC.getCopyOfContextMap();
        java.util.Map<String, String> original = java.util.Map.of("traceId", "worker-trace", "tid", "agent-trace");
        try {
            org.slf4j.MDC.setContextMap(original);
            var request = MockServerHttpRequest.get("/api/orders?token=secret")
                    .remoteAddress(new java.net.InetSocketAddress("127.0.0.1", 12345))
                    .build();
            var response = new org.springframework.mock.http.server.reactive.MockServerHttpResponse();
            var handler = httpHandler(exchange -> {
                exchange.getAttributes().put("traceId", "request-trace");
                exchange.getAttributes().put("requestId", "request-id");
                exchange.getAttributes().put("userId", "user-id");
                exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.CREATED);
                return exchange.getResponse().setComplete();
            });

            StepVerifier.create(handler.handle(request, response)).verifyComplete();

            var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(captured.get());
            org.assertj.core.api.Assertions.assertThat(json.path("traceId").asText())
                    .isEqualTo("request-trace");
            org.assertj.core.api.Assertions.assertThat(json.path("requestId").asText())
                    .isEqualTo("request-id");
            org.assertj.core.api.Assertions.assertThat(json.path("userId").asText())
                    .isEqualTo("user-id");
            org.assertj.core.api.Assertions.assertThat(json.path("method").asText())
                    .isEqualTo("GET");
            org.assertj.core.api.Assertions.assertThat(json.path("uri").asText())
                    .isEqualTo("/api/orders");
            org.assertj.core.api.Assertions.assertThat(json.path("status").asText())
                    .isEqualTo("201");
            org.assertj.core.api.Assertions.assertThat(json.path("clientIp").asText())
                    .isEqualTo("127.0.0.1");
            org.assertj.core.api.Assertions.assertThat(
                            Long.parseLong(json.path("duration").asText()))
                    .isNotNegative();
            org.assertj.core.api.Assertions.assertThat(captured.get()).doesNotContain("token=secret");
            org.assertj.core.api.Assertions.assertThat(org.slf4j.MDC.getCopyOfContextMap())
                    .isEqualTo(original);
        } finally {
            if (previous == null) {
                org.slf4j.MDC.clear();
            } else {
                org.slf4j.MDC.setContextMap(previous);
            }
            logger.detachAppender(appender);
            appender.stop();
            encoder.stop();
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
