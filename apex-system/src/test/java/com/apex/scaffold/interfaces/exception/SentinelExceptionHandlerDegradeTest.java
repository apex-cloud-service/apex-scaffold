package com.apex.scaffold.interfaces.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.Rule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
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
 * Sentinel 异常处理器降级增强测试。
 *
 * <p>验证 {@link SentinelExceptionHandler} 在熔断降级场景下，
 * 读接口返回缓存兜底值、写接口返回排队中状态的业务级降级逻辑。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SentinelExceptionHandler 降级增强测试")
class SentinelExceptionHandlerDegradeTest {

    /** Mock 国际化工具 */
    @Mock
    private MessageUtils messageUtils;

    /** Mock 安全指标记录器 */
    @Mock
    private SecurityMetricsRecorder securityMetricsRecorder;

    /** Mock 降级兜底服务 */
    @Mock
    private DegradeFallbackService degradeFallbackService;

    /** 被测处理器 */
    @InjectMocks
    private SentinelExceptionHandler handler;

    /** TraceIdUtil 静态 Mock */
    private MockedStatic<TraceIdUtil> traceIdUtilMockedStatic;

    /** 测试 traceId */
    private static final String TEST_TRACE_ID = "degrade-test-trace-001";

    /**
     * 初始化 Mock 环境。
     */
    @BeforeEach
    void setUp() {
        traceIdUtilMockedStatic = mockStatic(TraceIdUtil.class);
        traceIdUtilMockedStatic.when(TraceIdUtil::getTraceId).thenReturn(TEST_TRACE_ID);
        lenient().when(messageUtils.get(any(Locale.class), any())).thenReturn("");
    }

    /**
     * 清理静态 Mock。
     */
    @AfterEach
    void tearDown() {
        traceIdUtilMockedStatic.close();
    }

    /**
     * 测试 GET 请求触发熔断降级时应调用读接口降级逻辑。
     */
    @Test
    @DisplayName("GET请求熔断降级应调用读接口降级逻辑")
    void shouldCallReadDegradedOnGetRequest() {
        DegradeRule rule = new DegradeRule("GET:/api/v1/explore");
        rule.setCount(1);
        rule.setTimeWindow(10);
        DegradeException ex = new DegradeException("GET:/api/v1/explore");
        setRuleOnException(ex, rule);

        ServerWebExchange exchange = createMockExchange("GET");
        Result<Object> cachedResult = Result.success("cached-data", TEST_TRACE_ID);
        when(degradeFallbackService.buildDegradedReadResponse(anyString(), anyString()))
                .thenReturn(Mono.just(cachedResult));

        Mono<ResponseEntity<Result<Object>>> result = handler.handleDegradeException(ex, exchange);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    Result<Object> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.getCode()).isEqualTo(0);
                    assertThat(body.getData()).isEqualTo("cached-data");
                })
                .verifyComplete();
        verify(securityMetricsRecorder).recordCircuitBreakerOpened();
    }

    /**
     * 测试 POST 请求触发熔断降级时应调用写接口降级逻辑返回排队中状态。
     */
    @Test
    @DisplayName("POST请求熔断降级应返回排队中状态")
    void shouldReturnQueuedOnPostRequest() {
        DegradeRule rule = new DegradeRule("POST:/api/v1/vote");
        rule.setCount(1);
        rule.setTimeWindow(10);
        DegradeException ex = new DegradeException("POST:/api/v1/vote");
        setRuleOnException(ex, rule);

        ServerWebExchange exchange = createMockExchange("POST");
        Result<Object> queuedResult = Result.error(2003, "系统繁忙，请求已排队", TEST_TRACE_ID);
        when(degradeFallbackService.buildDegradedWriteResponse(anyString(), anyString()))
                .thenReturn(Mono.just(queuedResult));

        Mono<ResponseEntity<Result<Object>>> result = handler.handleDegradeException(ex, exchange);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    Result<Object> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.getCode()).isEqualTo(2003);
                })
                .verifyComplete();
    }

    /**
     * 测试限流异常仍返回标准 429 状态码。
     */
    @Test
    @DisplayName("限流异常应返回429标准错误码")
    void shouldReturn429OnFlowException() {
        FlowRule rule = new FlowRule("GET:/api/v1/explore");
        rule.setCount(1);
        FlowException ex = new FlowException("GET:/api/v1/explore");
        setRuleOnException(ex, rule);
        ServerWebExchange exchange = createMockExchange("GET");

        Mono<ResponseEntity<Result<Void>>> result = handler.handleFlowException(ex, exchange);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(response.getBody().getCode()).isEqualTo(ResultCode.RATE_LIMIT.getCode());
                })
                .verifyComplete();
    }

    /**
     * 创建模拟的 ServerWebExchange。
     *
     * @param method HTTP 方法名称
     * @return Mock 的 ServerWebExchange
     */
    private ServerWebExchange createMockExchange(String method) {
        ServerWebExchange exchange = org.mockito.Mockito.mock(ServerWebExchange.class);
        org.springframework.http.HttpMethod httpMethod = org.springframework.http.HttpMethod.valueOf(method);
        org.springframework.mock.http.server.reactive.MockServerHttpRequest request =
                org.springframework.mock.http.server.reactive.MockServerHttpRequest.method(
                                httpMethod, "http://localhost/api/v1/test")
                        .build();
        org.springframework.mock.web.server.MockServerWebExchange mockExchange =
                new org.springframework.mock.web.server.MockServerWebExchange.Builder(request).build();
        lenient().when(exchange.getRequest()).thenReturn(mockExchange.getRequest());
        return mockExchange;
    }

    /**
     * 通过反射设置 BlockException 的 rule 字段。
     *
     * @param ex   异常对象
     * @param rule 规则对象
     */
    private static void setRuleOnException(BlockException ex, Rule rule) {
        try {
            java.lang.reflect.Field field = BlockException.class.getDeclaredField("rule");
            field.setAccessible(true);
            field.set(ex, rule);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set rule on exception", e);
        }
    }
}
