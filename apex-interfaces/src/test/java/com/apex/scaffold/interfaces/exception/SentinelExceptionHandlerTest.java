package com.apex.scaffold.interfaces.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.authority.AuthorityException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowException;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.csp.sentinel.slots.system.SystemBlockException;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.util.TraceIdUtil;
import com.apex.scaffold.interfaces.monitor.SecurityMetricsRecorder;
import com.apex.scaffold.interfaces.util.MessageUtils;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Sentinel 流量控制异常处理器单元测试
 *
 * <p>验证 SentinelExceptionHandler 对各种 Sentinel 异常类型的处理逻辑，
 * 包括限流、熔断、降级、系统保护和授权规则异常的场景
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SentinelExceptionHandler Sentinel异常处理器测试")
class SentinelExceptionHandlerTest {

    @Mock
    private MessageUtils messageUtils;

    @Mock
    private SecurityMetricsRecorder securityMetricsRecorder;

    @Mock
    private DegradeFallbackService degradeFallbackService;

    @InjectMocks
    private SentinelExceptionHandler handler;

    private MockedStatic<TraceIdUtil> traceIdUtilMockedStatic;

    private static final String TEST_TRACE_ID = "test-trace-id-123456";

    /**
     * 设置{@code up}。
     */
    @BeforeEach
    void setUp() {
        traceIdUtilMockedStatic = mockStatic(TraceIdUtil.class);
        traceIdUtilMockedStatic.when(TraceIdUtil::getTraceId).thenReturn(TEST_TRACE_ID);
        lenient().when(messageUtils.get(any(Locale.class), any())).thenReturn("");
    }

    /**
     * 执行 {@code tearDown} 操作。
     */
    @AfterEach
    void tearDown() {
        traceIdUtilMockedStatic.close();
    }

    /**
     * 设置{@code ruleOnException}。
     * @param ex 参数 {@code ex}
     * @param rule 参数 {@code rule}
     */
    private static void setRuleOnException(BlockException ex, com.alibaba.csp.sentinel.slots.block.Rule rule) {
        try {
            java.lang.reflect.Field field = BlockException.class.getDeclaredField("rule");
            field.setAccessible(true);
            field.set(ex, rule);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set rule on exception", e);
        }
    }

    /**
     * 处理{@code flowException_shouldReturnTooManyRequests}。
     */
    @Test
    @DisplayName("处理FlowException应返回429状态码")
    void handleFlowException_shouldReturnTooManyRequests() {
        FlowRule rule = new FlowRule("flow blocked");
        rule.setCount(1);
        FlowException ex = new FlowException("flow blocked");
        setRuleOnException(ex, rule);
        ServerWebExchange exchange = org.mockito.Mockito.mock(ServerWebExchange.class);

        Mono<ResponseEntity<Result<Void>>> result = handler.handleFlowException(ex, exchange);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    Result<Void> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.getCode()).isEqualTo(ResultCode.RATE_LIMIT.getCode());
                    assertThat(body.getTraceId()).isEqualTo(TEST_TRACE_ID);
                })
                .verifyComplete();
        verify(securityMetricsRecorder).recordRateLimitRejected();
    }

    /**
     * 处理{@code paramFlowException_shouldReturnTooManyRequests}。
     */
    @Test
    @DisplayName("处理ParamFlowException应返回429状态码")
    void handleParamFlowException_shouldReturnTooManyRequests() {
        ParamFlowRule rule = new ParamFlowRule("hotResource");
        rule.setCount(1);
        ParamFlowException ex = new ParamFlowException("hotResource", "hot parameter blocked");
        setRuleOnException(ex, rule);
        ServerWebExchange exchange = org.mockito.Mockito.mock(ServerWebExchange.class);

        Mono<ResponseEntity<Result<Void>>> result = handler.handleParamFlowException(ex, exchange);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    Result<Void> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.getCode()).isEqualTo(ResultCode.RATE_LIMIT.getCode());
                    assertThat(body.getTraceId()).isEqualTo(TEST_TRACE_ID);
                })
                .verifyComplete();
    }

    /**
     * 处理{@code degradeException_shouldReturnServiceUnavailable}。
     */
    @Test
    @DisplayName("处理DegradeException应返回503状态码")
    void handleDegradeException_shouldReturnServiceUnavailable() {
        DegradeRule rule = new DegradeRule("degraded");
        rule.setCount(1);
        rule.setTimeWindow(10);
        DegradeException ex = new DegradeException("degraded");
        setRuleOnException(ex, rule);

        org.springframework.http.HttpMethod httpMethod = org.springframework.http.HttpMethod.GET;
        org.springframework.mock.http.server.reactive.MockServerHttpRequest request =
                org.springframework.mock.http.server.reactive.MockServerHttpRequest.method(
                                httpMethod, "http://localhost/api/v1/test")
                        .build();
        org.springframework.mock.web.server.MockServerWebExchange exchange =
                new org.springframework.mock.web.server.MockServerWebExchange.Builder(request).build();

        com.apex.scaffold.common.result.Result<Object> degradedResult =
                com.apex.scaffold.common.result.Result.error(2003, "服务暂时降级", TEST_TRACE_ID);
        org.mockito.Mockito.when(degradeFallbackService.buildDegradedReadResponse(
                        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Mono.just(degradedResult));

        Mono<ResponseEntity<Result<Object>>> result = handler.handleDegradeException(ex, exchange);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    Result<Object> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.getTraceId()).isEqualTo(TEST_TRACE_ID);
                })
                .verifyComplete();
        verify(securityMetricsRecorder).recordCircuitBreakerOpened();
    }

    /**
     * 处理{@code systemBlockException_shouldReturnServiceUnavailable}。
     */
    @Test
    @DisplayName("处理SystemBlockException应返回503状态码")
    void handleSystemBlockException_shouldReturnServiceUnavailable() {
        SystemBlockException ex = new SystemBlockException("system", "system blocked");
        ServerWebExchange exchange = org.mockito.Mockito.mock(ServerWebExchange.class);

        Mono<ResponseEntity<Result<Void>>> result = handler.handleSystemBlockException(ex, exchange);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    Result<Void> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.getCode()).isEqualTo(ResultCode.DEGRADE.getCode());
                    assertThat(body.getTraceId()).isEqualTo(TEST_TRACE_ID);
                })
                .verifyComplete();
    }

    /**
     * 处理{@code authorityException_shouldReturnForbidden}。
     */
    @Test
    @DisplayName("处理AuthorityException应返回403状态码")
    void handleAuthorityException_shouldReturnForbidden() {
        AuthorityException ex = new AuthorityException("not authorized");
        ServerWebExchange exchange = org.mockito.Mockito.mock(ServerWebExchange.class);

        Mono<ResponseEntity<Result<Void>>> result = handler.handleAuthorityException(ex, exchange);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    Result<Void> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.getCode()).isEqualTo(ResultCode.FORBIDDEN.getCode());
                    assertThat(body.getTraceId()).isEqualTo(TEST_TRACE_ID);
                })
                .verifyComplete();
    }

    /**
     * 处理{@code blockException_shouldReturnServiceUnavailable}。
     */
    @Test
    @DisplayName("处理BlockException应返回503状态码")
    void handleBlockException_shouldReturnServiceUnavailable() {
        BlockException ex = new BlockException("blocked") {};
        ServerWebExchange exchange = org.mockito.Mockito.mock(ServerWebExchange.class);

        Mono<ResponseEntity<Result<Void>>> result = handler.handleBlockException(ex, exchange);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    Result<Void> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.getCode()).isEqualTo(ResultCode.CIRCUIT_BREAKER.getCode());
                    assertThat(body.getTraceId()).isEqualTo(TEST_TRACE_ID);
                })
                .verifyComplete();
    }
}
