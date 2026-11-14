package com.apex.scaffold.interfaces.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.apex.scaffold.common.util.TraceIdUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 链路追踪过滤器单元测试。
 *
 * <p>验证请求级 Reactor Context 传播与日志回调的 MDC 保存恢复。
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

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("请求头中无TraceId时应该生成新的TraceId")
    void filter_withoutTraceIdInHeader_shouldGenerateNewTraceId() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/test").build());
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getHeaders().getFirst("X-Trace-Id")).matches("[0-9a-f]{32}");
        assertThat(MDC.get(TraceIdUtil.TRACE_ID_KEY)).isNull();
    }

    @Test
    @DisplayName("请求头中有TraceId时应传播到Context、属性和响应头")
    void filter_withTraceIdInHeader_shouldUseExistingTraceId() {
        ServerWebExchange exchange = exchangeWithTraceId();
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.deferContextual(context -> {
            assertThat(context.<String>get(TraceIdUtil.TRACE_ID_KEY)).isEqualTo("business-trace");
            return Mono.empty();
        }));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.<String>getAttribute("traceId")).isEqualTo("business-trace");
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Trace-Id")).isEqualTo("business-trace");
    }

    @Test
    void filterConstructionShouldPreserveEntryThreadMdc() {
        MDC.put(TraceIdUtil.TRACE_ID_KEY, "entry-trace");
        MDC.put("tid", "agent-trace");
        when(chain.filter(any(ServerWebExchange.class))).thenAnswer(invocation -> {
            assertThat(MDC.get(TraceIdUtil.TRACE_ID_KEY)).isEqualTo("entry-trace");
            return Mono.empty();
        });

        filter.filter(exchangeWithTraceId(), chain);

        assertThat(MDC.get(TraceIdUtil.TRACE_ID_KEY)).isEqualTo("entry-trace");
        assertThat(MDC.get("tid")).isEqualTo("agent-trace");
    }

    @Test
    void filterConstructionShouldNotPopulateEmptyEntryThreadMdc() {
        MDC.clear();
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        filter.filter(exchangeWithTraceId(), chain);

        assertThat(MDC.get(TraceIdUtil.TRACE_ID_KEY)).isNull();
    }

    @Test
    void completionShouldPreserveSignalThreadMdc() {
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
        Mono<Void> result = filter.filter(exchangeWithTraceId(), chain);
        MDC.put(TraceIdUtil.TRACE_ID_KEY, "signal-trace");
        MDC.put("tid", "agent-trace");

        StepVerifier.create(result).verifyComplete();

        assertThat(MDC.get(TraceIdUtil.TRACE_ID_KEY)).isEqualTo("signal-trace");
        assertThat(MDC.get("tid")).isEqualTo("agent-trace");
    }

    @Test
    void errorShouldPreserveSignalThreadMdc() {
        IllegalStateException failure = new IllegalStateException("downstream failure");
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.error(failure));
        Mono<Void> result = filter.filter(exchangeWithTraceId(), chain);
        MDC.put(TraceIdUtil.TRACE_ID_KEY, "signal-trace");

        StepVerifier.create(result)
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(failure))
                .verify();

        assertThat(MDC.get(TraceIdUtil.TRACE_ID_KEY)).isEqualTo("signal-trace");
    }

    @Test
    void cancellationShouldPreserveSignalThreadMdc() {
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.never());
        Mono<Void> result = filter.filter(exchangeWithTraceId(), chain);
        MDC.put(TraceIdUtil.TRACE_ID_KEY, "signal-trace");

        StepVerifier.create(result).thenCancel().verify();

        assertThat(MDC.get(TraceIdUtil.TRACE_ID_KEY)).isEqualTo("signal-trace");
    }

    @Test
    void synchronousChainFailureShouldPreserveEntryThreadMdc() {
        IllegalStateException failure = new IllegalStateException("chain construction failure");
        MDC.put(TraceIdUtil.TRACE_ID_KEY, "entry-trace");
        when(chain.filter(any(ServerWebExchange.class))).thenThrow(failure);

        assertThatThrownBy(() -> filter.filter(exchangeWithTraceId(), chain)).isSameAs(failure);

        assertThat(MDC.get(TraceIdUtil.TRACE_ID_KEY)).isEqualTo("entry-trace");
    }

    @Test
    void filterLogsShouldCarryRequestTraceAndPreserveOtherMdc() {
        Logger logger = (Logger) LoggerFactory.getLogger(TraceFilter.class);
        Level originalLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>() {
            @Override
            protected void append(ILoggingEvent event) {
                event.prepareForDeferredProcessing();
                super.append(event);
            }
        };
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
        MDC.put(TraceIdUtil.TRACE_ID_KEY, "entry-trace");
        MDC.put("tid", "agent-trace");
        MDC.put("tenantId", "tenant-a");
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
        try {
            filter.filter(exchangeWithTraceId(), chain).block();

            assertThat(appender.list).hasSize(2).allSatisfy(event -> {
                assertThat(event.getMDCPropertyMap()).containsEntry(TraceIdUtil.TRACE_ID_KEY, "business-trace");
                assertThat(event.getMDCPropertyMap()).containsEntry("tid", "agent-trace");
                assertThat(event.getMDCPropertyMap()).containsEntry("tenantId", "tenant-a");
            });
            assertThat(MDC.get(TraceIdUtil.TRACE_ID_KEY)).isEqualTo("entry-trace");
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(originalLevel);
            appender.stop();
        }
    }

    @Test
    void businessTraceMustNotBecomeSkyWalkingTid() {
        MDC.clear();
        when(chain.filter(any(ServerWebExchange.class))).thenAnswer(invocation -> {
            assertThat(MDC.get("tid")).isNull();
            return Mono.empty();
        });

        filter.filter(exchangeWithTraceId(), chain).block();

        assertThat(MDC.get("tid")).isNull();
    }

    private ServerWebExchange exchangeWithTraceId() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/test")
                .header("X-Trace-Id", "business-trace")
                .build());
    }
}
