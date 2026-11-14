package com.apex.scaffold.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.gateway.log.AccessLogProperties;
import com.apex.scaffold.gateway.log.AccessLogRecord;
import com.apex.scaffold.gateway.log.AccessLogRepository;
import com.apex.scaffold.gateway.log.AccessLogService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * {@link RequestLoggingFilter} 单元测试.
 *
 * <p>覆盖迁移方案 §5.3 过滤器链第 7 位（order = 50）的职责：链路末端观测、
 * 请求原样透传（不修改交换）、顺序校验，以及访问日志落库开关控制。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@DisplayName("RequestLoggingFilter 访问日志过滤器测试")
class RequestLoggingFilterTest {

    /** 默认关闭落库的过滤器（透传/顺序用例复用） */
    private final AccessLogProperties defaultProperties = new AccessLogProperties();

    private final RequestLoggingFilter filter = newFilter(defaultProperties, new AtomicInteger());

    /**
     * 构造一个使用计数仓储的过滤器.
     *
     * @param properties 落库配置
     * @param saveCount  落库调用计数
     * @return 访问日志过滤器
     */
    private static RequestLoggingFilter newFilter(AccessLogProperties properties, AtomicInteger saveCount) {
        AccessLogRepository repository = record -> {
            saveCount.incrementAndGet();
            return Mono.empty();
        };
        return new RequestLoggingFilter(
                new AccessLogService(repository, properties, new SimpleMeterRegistry()), properties);
    }

    /**
     * 构造直通链路，捕获被传入的交换对象。
     */
    private GatewayFilterChain capturingChain(ServerWebExchange[] holder) {
        return exchange -> {
            holder[0] = exchange;
            return Mono.empty();
        };
    }

    @Test
    void shouldEncodeRequestFieldsAndRestoreWorkerMdc() throws Exception {
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(RequestLoggingFilter.class);
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
        reactor.core.scheduler.Scheduler worker = reactor.core.scheduler.Schedulers.newSingle("access-log-test");
        java.util.Map<String, String> original =
                java.util.Map.of("traceId", "worker-trace", "tenantId", "worker-tenant", "tid", "agent-trace");
        MockServerWebExchange exchange =
                MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/orders?token=secret")
                        .header(TraceWebFilter.HEADER_TRACE_ID, "request-trace")
                        .header(UserContextRelayFilter.HEADER_TENANT_ID, "request-tenant")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer secret-token")
                        .build());
        exchange.getAttributes()
                .put(
                        org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR,
                        org.springframework.cloud.gateway.route.Route.async()
                                .id("orders-route")
                                .uri("lb://orders")
                                .predicate(e -> true)
                                .build());
        exchange.getResponse().setStatusCode(HttpStatus.CREATED);
        try {
            Mono.fromRunnable(() -> org.slf4j.MDC.setContextMap(original))
                    .subscribeOn(worker)
                    .block();
            filter.filter(exchange, e -> Mono.<Void>empty().publishOn(worker)).block();
            java.util.Map<String, String> restored = Mono.fromCallable(org.slf4j.MDC::getCopyOfContextMap)
                    .subscribeOn(worker)
                    .block();
            assertThat(restored).isEqualTo(original);
            var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(captured.get());
            assertThat(json.path("traceId").asText()).isEqualTo("request-trace");
            assertThat(json.path("tenantId").asText()).isEqualTo("request-tenant");
            assertThat(json.path("routeId").asText()).isEqualTo("orders-route");
            assertThat(json.path("status").asInt()).isEqualTo(201);
            assertThat(json.path("requestUri").asText()).isEqualTo("/api/v1/orders");
            assertThat(json.path("method").asText()).isEqualTo("GET");
            assertThat(json.path("duration").isNumber()).isTrue();
            assertThat(captured.get()).doesNotContain("secret-token", "token=secret");
        } finally {
            Mono.fromRunnable(org.slf4j.MDC::clear).subscribeOn(worker).block();
            worker.dispose();
            logger.detachAppender(appender);
            appender.stop();
            encoder.stop();
        }
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

    @Nested
    @DisplayName("访问日志落库开关")
    class PersistTests {

        @Test
        @DisplayName("总开关关闭时不应触发落库")
        void shouldNotPersistWhenDisabled() {
            AtomicInteger saveCount = new AtomicInteger();
            RequestLoggingFilter disabledFilter = newFilter(new AccessLogProperties(), saveCount);
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/v1/orders").build());

            StepVerifier.create(disabledFilter.filter(exchange, e -> Mono.empty()))
                    .verifyComplete();

            assertThat(saveCount).hasValue(0);
        }

        @Test
        @DisplayName("总开关开启时应组装并落库一条访问日志")
        void shouldPersistWhenEnabled() {
            AtomicInteger saveCount = new AtomicInteger();
            List<AccessLogRecord> captured = new ArrayList<>();
            AccessLogProperties properties = new AccessLogProperties();
            properties.setEnabled(true);
            AccessLogRepository repository = record -> {
                saveCount.incrementAndGet();
                captured.add(record);
                return Mono.empty();
            };
            RequestLoggingFilter enabledFilter = new RequestLoggingFilter(
                    new AccessLogService(repository, properties, new SimpleMeterRegistry()), properties);
            MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/orders")
                    .header(HttpHeaders.USER_AGENT, "junit")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer secret-token")
                    .header(GrayRouteFilter.HEADER_GRAY_TAG, GrayRouteFilter.GRAY_VALUE)
                    .header(UserContextRelayFilter.HEADER_USER_ID, "u-1")
                    .header(UserContextRelayFilter.HEADER_TENANT_ID, "t-1")
                    .build());

            StepVerifier.create(enabledFilter.filter(exchange, e -> {
                        exchange.getResponse().setStatusCode(HttpStatus.OK);
                        return Mono.empty();
                    }))
                    .verifyComplete();

            assertThat(saveCount).hasValue(1);
            assertThat(captured).hasSize(1);
            AccessLogRecord record = captured.get(0);
            assertThat(record.method()).isEqualTo("GET");
            assertThat(record.path()).isEqualTo("/api/v1/orders");
            assertThat(record.grayTag()).isEqualTo(GrayRouteFilter.GRAY_VALUE);
            assertThat(record.userId()).isEqualTo("u-1");
            assertThat(record.tenantId()).isEqualTo("t-1");
            assertThat(record.status()).isEqualTo(200);
            assertThat(record.requestHeadersJson()).contains("\"Authorization\":\"***\"");
        }
    }
}
