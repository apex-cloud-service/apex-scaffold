package com.apex.scaffold.interfaces.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.util.TraceIdUtil;
import com.apex.scaffold.infrastructure.remote.exception.RemoteApiException;
import com.apex.scaffold.infrastructure.wechat.exception.WeChatApiException;
import com.apex.scaffold.interfaces.monitor.ExceptionMonitor;
import com.apex.scaffold.interfaces.util.MessageUtils;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;
import org.springframework.core.codec.DecodingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 全局异常处理器单元测试
 *
 * <p>验证 GlobalExceptionHandler 对各种异常类型的处理逻辑，
 * 包括业务异常、微信API异常、参数校验异常、系统异常等场景
 *
 * @author Apex Team
 * @version 1.1.0
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("GlobalExceptionHandler 全局异常处理器测试")
class GlobalExceptionHandlerTest {

    @Mock
    private ExceptionMonitor exceptionMonitor;

    @Mock
    private MessageUtils messageUtils;

    @Mock
    private ServerWebExchange exchange;

    @InjectMocks
    private GlobalExceptionHandler handler;

    private MockedStatic<TraceIdUtil> traceIdUtilMockedStatic;

    /** 异常处理器测试 logger。 */
    private Logger handlerLogger;

    /** 测试前的 logger 级别，用于恢复全局状态。 */
    private Level originalLoggerLevel;

    private static final String TEST_TRACE_ID = "test-trace-id-123456";

    /**
     * 初始化异常处理依赖，并静默测试主动构造的预期异常堆栈。
     *
     */
    @BeforeEach
    void setUp() {
        handlerLogger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        originalLoggerLevel = handlerLogger.getLevel();
        handlerLogger.setLevel(Level.OFF);
        traceIdUtilMockedStatic = mockStatic(TraceIdUtil.class);
        traceIdUtilMockedStatic.when(TraceIdUtil::getTraceId).thenReturn(TEST_TRACE_ID);
        doNothing().when(exceptionMonitor).monitorException(any(Exception.class));
        when(messageUtils.get(any(Locale.class), anyString())).thenAnswer(invocation -> {
            String code = invocation.getArgument(1);
            if ("sys.param_error".equals(code)) {
                return "参数错误";
            } else if ("sys.error".equals(code)) {
                return "系统错误";
            } else if ("sys.internal_error".equals(code)) {
                return "系统繁忙，请稍后重试";
            }
            return code;
        });
    }

    /**
     * 关闭静态 Mock 并恢复 logger 级别，避免污染其他测试。
     *
     */
    @AfterEach
    void tearDown() {
        if (handlerLogger != null) {
            handlerLogger.setLevel(originalLoggerLevel);
        }
        if (traceIdUtilMockedStatic != null) {
            traceIdUtilMockedStatic.close();
        }
    }

    /**
     * 处理{@code businessException_shouldReturnErrorResponse}。
     */
    @Test
    @DisplayName("处理业务异常应返回对应的错误响应")
    void handleBusinessException_shouldReturnErrorResponse() {
        BusinessException ex = new BusinessException(ResultCode.PARAM_ERROR, "参数错误");

        Mono<ResponseEntity<Result<Void>>> result = handler.handleBusinessException(ex, exchange);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    Result<Void> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
                    assertThat(body.getMsg()).isEqualTo("参数错误");
                    assertThat(body.getTraceId()).isEqualTo(TEST_TRACE_ID);
                })
                .verifyComplete();

        verify(exceptionMonitor).monitorException(ex);
    }

    /** 非正脸业务码应映射为用户可直接操作的本地化提示。 */
    @Test
    @DisplayName("处理非正脸异常应提示用户重新上传")
    void handleBusinessException_photoNotFrontal_shouldReturnRetryPrompt() {
        when(messageUtils.get(any(Locale.class), eq("param.photo_not_frontal"))).thenReturn("照片不是清晰正脸，请重新上传正脸照片");
        BusinessException ex = new BusinessException(ResultCode.PHOTO_NOT_FRONTAL);

        StepVerifier.create(handler.handleBusinessException(ex, exchange))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(response.getBody()).isNotNull();
                    assertThat(response.getBody().getCode()).isEqualTo(ResultCode.PHOTO_NOT_FRONTAL.getCode());
                    assertThat(response.getBody().getMsg()).isEqualTo("请上传清晰的正脸照片后重试");
                })
                .verifyComplete();
    }

    /**
     * 处理{@code businessException_withUnauthorizedCode_shouldReturnUnauthorized}。
     */
    @Test
    @DisplayName("处理401业务异常应返回401状态码")
    void handleBusinessException_withUnauthorizedCode_shouldReturnUnauthorized() {
        BusinessException ex = new BusinessException(ResultCode.UNAUTHORIZED, "未授权");

        Mono<ResponseEntity<Result<Void>>> result = handler.handleBusinessException(ex, exchange);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                })
                .verifyComplete();
    }

    /**
     * 处理{@code businessException_withForbiddenCode_shouldReturnForbidden}。
     */
    @Test
    @DisplayName("处理403业务异常应返回403状态码")
    void handleBusinessException_withForbiddenCode_shouldReturnForbidden() {
        BusinessException ex = new BusinessException(ResultCode.FORBIDDEN, "禁止访问");

        Mono<ResponseEntity<Result<Void>>> result = handler.handleBusinessException(ex, exchange);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                })
                .verifyComplete();
    }

    /**
     * 处理{@code businessException_withNotFoundCode_shouldReturnNotFound}。
     */
    @Test
    @DisplayName("处理404业务异常应返回404状态码")
    void handleBusinessException_withNotFoundCode_shouldReturnNotFound() {
        BusinessException ex = new BusinessException(ResultCode.NOT_FOUND, "未找到");

        Mono<ResponseEntity<Result<Void>>> result = handler.handleBusinessException(ex, exchange);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                })
                .verifyComplete();
    }

    /**
     * 处理{@code businessException_withServerErrorCode_shouldReturnInternalServerError}。
     */
    @Test
    @DisplayName("处理大于500的错误码应返回500状态码")
    void handleBusinessException_withServerErrorCode_shouldReturnInternalServerError() {
        BusinessException ex = new BusinessException(500, "服务器错误");

        Mono<ResponseEntity<Result<Void>>> result = handler.handleBusinessException(ex, exchange);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                })
                .verifyComplete();
    }

    /**
     * 处理{@code weChatApiException_shouldReturnBadGateway}。
     */
    @Test
    @DisplayName("处理微信API异常应返回502状态码")
    void handleWeChatApiException_shouldReturnBadGateway() {
        WeChatApiException ex = new WeChatApiException(40013, "无效的AppID");

        Mono<ResponseEntity<Result<Void>>> result = handler.handleWeChatApiException(ex, exchange);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    Result<Void> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.getCode()).isEqualTo(ResultCode.WX_API_ERROR.getCode());
                    assertThat(body.getTraceId()).isEqualTo(TEST_TRACE_ID);
                })
                .verifyComplete();

        verify(exceptionMonitor).monitorException(ex);
    }

    /**
     * 处理{@code remoteApiException_clientError_shouldReturnBadRequest}。
     */
    @Test
    @DisplayName("远端服务4xx应映射为400且不透出上游响应体")
    void handleRemoteApiException_clientError_shouldReturnBadRequest() {
        RemoteApiException ex = new RemoteApiException(
                422, "face-analysis", "/api/mediapipe-feature-zscore", "sensitive detail", "upstream failed");

        StepVerifier.create(handler.handleRemoteApiException(ex, exchange))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(response.getBody()).isNotNull();
                    assertThat(response.getBody().getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
                    assertThat(response.getBody().getMsg()).doesNotContain("sensitive detail");
                })
                .verifyComplete();
    }

    /**
     * 处理{@code handleRemoteApiException_serverError_shouldReturnBadGateway}。
     */
    @Test
    @DisplayName("远端服务5xx应映射为502和熔断业务码")
    void handleRemoteApiException_serverError_shouldReturnBadGateway() {
        RemoteApiException ex = new RemoteApiException(
                503, "face-analysis", "/api/mediapipe-feature-zscore", "unavailable", "upstream failed");

        StepVerifier.create(handler.handleRemoteApiException(ex, exchange))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(response.getBody()).isNotNull();
                    assertThat(response.getBody().getCode()).isEqualTo(ResultCode.CIRCUIT_BREAKER.getCode());
                })
                .verifyComplete();
    }

    /**
     * 处理{@code constraintViolationException_shouldReturnBadRequest}。
     */
    @Test
    @DisplayName("处理参数校验异常应返回400状态码")
    void handleConstraintViolationException_shouldReturnBadRequest() {
        ConstraintViolationException ex = new ConstraintViolationException("字段不能为空", Set.of());

        Mono<ResponseEntity<Result<Void>>> result = handler.handleConstraintViolationException(ex, exchange);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    Result<Void> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
                    assertThat(body.getTraceId()).isEqualTo(TEST_TRACE_ID);
                })
                .verifyComplete();

        verify(exceptionMonitor).monitorException(ex);
    }

    /**
     * 处理{@code webExchangeBindException_shouldReturnBadRequest}。
     */
    @Test
    @DisplayName("处理WebFlux绑定异常应返回400状态码")
    void handleWebExchangeBindException_shouldReturnBadRequest() {
        WebExchangeBindException ex = mock(WebExchangeBindException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("object", "field", "字段校验失败");

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        Mono<ResponseEntity<Result<Void>>> result = handler.handleWebExchangeBindException(ex, exchange);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    Result<Void> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
                })
                .verifyComplete();

        verify(exceptionMonitor).monitorException(ex);
    }

    /**
     * 处理{@code illegalArgumentException_shouldReturnBadRequest}。
     */
    @Test
    @DisplayName("处理非法参数异常应返回400状态码")
    void handleIllegalArgumentException_shouldReturnBadRequest() {
        IllegalArgumentException ex = new IllegalArgumentException("非法的参数值");

        Mono<ResponseEntity<Result<Void>>> result = handler.handleIllegalArgumentException(ex, exchange);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    Result<Void> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
                    assertThat(body.getMsg()).isEqualTo("参数错误");
                })
                .verifyComplete();

        verify(exceptionMonitor).monitorException(ex);
    }

    /**
     * 处理{@code decodingException_shouldReturnBadRequest}。
     */
    @Test
    @DisplayName("处理请求体解码异常应返回400状态码")
    void handleDecodingException_shouldReturnBadRequest() {
        DecodingException ex = new DecodingException("Could not find first boundary");
        ServerWebExchange decodingExchange =
                MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/file/upload")
                        .header("Content-Type", "multipart/form-data")
                        .build());

        Mono<ResponseEntity<Result<Void>>> result = handler.handleDecodingException(ex, decodingExchange);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    Result<Void> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
                    assertThat(body.getMsg()).isEqualTo("参数错误");
                    assertThat(body.getTraceId()).isEqualTo(TEST_TRACE_ID);
                })
                .verifyComplete();

        verify(exceptionMonitor).monitorException(ex);
    }

    /**
     * 处理{@code illegalStateException_shouldReturnConflict}。
     */
    @Test
    @DisplayName("处理非法状态异常应返回409状态码")
    void handleIllegalStateException_shouldReturnConflict() {
        IllegalStateException ex = new IllegalStateException("状态异常");

        Mono<ResponseEntity<Result<Void>>> result = handler.handleIllegalStateException(ex, exchange);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    Result<Void> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.getCode()).isEqualTo(ResultCode.ERROR.getCode());
                    assertThat(body.getMsg()).isEqualTo("状态异常");
                })
                .verifyComplete();

        verify(exceptionMonitor).monitorException(ex);
    }

    /**
     * 处理{@code nullPointerException_shouldReturnInternalServerError}。
     */
    @Test
    @DisplayName("处理空指针异常应返回500状态码")
    void handleNullPointerException_shouldReturnInternalServerError() {
        NullPointerException ex = new NullPointerException("空指针");

        Mono<ResponseEntity<Result<Void>>> result = handler.handleNullPointerException(ex, exchange);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                    Result<Void> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.getCode()).isEqualTo(ResultCode.ERROR.getCode());
                    assertThat(body.getMsg()).isEqualTo("系统错误");
                })
                .verifyComplete();

        verify(exceptionMonitor).monitorException(ex);
    }

    /**
     * 验证响应式线程 MDC 缺失时仍优先复用请求交换对象中的 Trace ID，确保响应体与访问日志可关联。
     */
    @Test
    @DisplayName("处理空指针异常时应优先使用请求交换对象的TraceId")
    void handleNullPointerException_shouldPreferExchangeTraceId() {
        String requestTraceId = "request-trace-id-from-filter";
        when(exchange.<String>getAttribute(TraceIdUtil.TRACE_ID_KEY)).thenReturn(requestTraceId);
        NullPointerException ex = new NullPointerException("跨线程空指针");

        Mono<ResponseEntity<Result<Void>>> result = handler.handleNullPointerException(ex, exchange);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                    Result<Void> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.getTraceId()).isEqualTo(requestTraceId);
                })
                .verifyComplete();

        verify(exceptionMonitor).monitorException(ex);
    }

    /**
     * 处理{@code exception_shouldReturnInternalServerError}。
     */
    @Test
    @DisplayName("处理未捕获异常应返回500状态码")
    void handleException_shouldReturnInternalServerError() {
        Exception ex = new Exception("未知异常");

        Mono<ResponseEntity<Result<Void>>> result = handler.handleException(ex, exchange);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                    Result<Void> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.getCode()).isEqualTo(ResultCode.ERROR.getCode());
                    assertThat(body.getMsg()).isEqualTo("系统繁忙，请稍后重试");
                })
                .verifyComplete();

        verify(exceptionMonitor).monitorException(ex);
    }

    /**
     * 执行 {@code webExceptionHandler_shouldTreatDecodingExceptionAsBadRequest} 操作。
     */
    @Test
    @DisplayName("WebFlux 兜底处理器应将解码异常映射为400")
    void webExceptionHandler_shouldTreatDecodingExceptionAsBadRequest() {
        MockServerWebExchange decodingExchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/file/upload").build());
        DecodingException ex = new DecodingException("Could not find first boundary");

        StepVerifier.create(handler.webExceptionHandler().handle(decodingExchange, ex))
                .verifyComplete();

        assertThat(decodingExchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
