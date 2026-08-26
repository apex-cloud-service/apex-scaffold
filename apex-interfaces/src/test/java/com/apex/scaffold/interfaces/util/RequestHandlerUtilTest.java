package com.apex.scaffold.interfaces.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.util.TraceIdUtil;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Optional;
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
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 请求处理工具类单元测试
 *
 * <p>验证 RequestHandlerUtil 的请求体处理、参数验证、
 * 响应构建和参数提取等核心功能
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RequestHandlerUtil 请求处理工具类测试")
class RequestHandlerUtilTest {

    @Mock
    private Validator validator;

    @Mock
    private ServerRequest serverRequest;

    @InjectMocks
    private RequestHandlerUtil requestHandlerUtil;

    private MockedStatic<TraceIdUtil> traceIdUtilMockedStatic;

    private static final String TEST_TRACE_ID = "test-trace-id-123456";

    /**
     * 设置{@code up}。
     */
    @BeforeEach
    void setUp() {
        traceIdUtilMockedStatic = mockStatic(TraceIdUtil.class);
        traceIdUtilMockedStatic.when(TraceIdUtil::getTraceId).thenReturn(TEST_TRACE_ID);
    }

    /**
     * 执行 {@code tearDown} 操作。
     */
    @AfterEach
    void tearDown() {
        traceIdUtilMockedStatic.close();
    }

    static class TestRequest {
        private String name;
        private Integer age;

        /**
         * 获取{@code name}。
         * @return 字符串结果
         */
        public String getName() {
            return name;
        }

        /**
         * 设置{@code name}。
         * @param name 参数 {@code name}
         */
        public void setName(String name) {
            this.name = name;
        }

        /**
         * 获取{@code age}。
         * @return 操作结果
         */
        public Integer getAge() {
            return age;
        }

        /**
         * 设置{@code age}。
         * @param age 参数 {@code age}
         */
        public void setAge(Integer age) {
            this.age = age;
        }
    }

    /**
     * 处理{@code requestBody_withValidBody_shouldReturnBody}。
     */
    @Test
    @DisplayName("处理请求体且验证通过时应返回请求体")
    void handleRequestBody_withValidBody_shouldReturnBody() {
        TestRequest body = new TestRequest();
        body.setName("test");
        body.setAge(20);

        when(serverRequest.bodyToMono(TestRequest.class)).thenReturn(Mono.just(body));
        when(validator.validate(body)).thenReturn(Set.of());

        StepVerifier.create(requestHandlerUtil.handleRequestBody(serverRequest, TestRequest.class))
                .assertNext(result -> {
                    assertThat(result).isEqualTo(body);
                })
                .verifyComplete();
    }

    /**
     * 处理{@code requestBody_withInvalidBody_shouldThrowBusinessException}。
     */
    @Test
    @DisplayName("处理请求体且验证失败时应抛出业务异常")
    void handleRequestBody_withInvalidBody_shouldThrowBusinessException() {
        TestRequest body = new TestRequest();
        body.setName("");
        body.setAge(null);

        @SuppressWarnings("unchecked")
        ConstraintViolation<TestRequest> violation =
                (ConstraintViolation<TestRequest>) org.mockito.Mockito.mock(ConstraintViolation.class);
        when(violation.getMessage()).thenReturn("名称不能为空");

        when(serverRequest.bodyToMono(TestRequest.class)).thenReturn(Mono.just(body));
        when(validator.validate(body)).thenReturn(Set.of(violation));

        StepVerifier.create(requestHandlerUtil.handleRequestBody(serverRequest, TestRequest.class))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(BusinessException.class);
                    BusinessException be = (BusinessException) error;
                    assertThat(be.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
                })
                .verify();
    }

    /**
     * 构建{@code successResponse_shouldReturnSuccessResponse}。
     */
    @Test
    @DisplayName("构建成功响应应返回正确的ServerResponse")
    void buildSuccessResponse_shouldReturnSuccessResponse() {
        String data = "test-data";

        StepVerifier.create(requestHandlerUtil.buildSuccessResponse(data))
                .assertNext(response -> {
                    assertThat(response.statusCode().value()).isEqualTo(200);
                    assertThat(response.headers().getContentType()).hasToString(MediaType.APPLICATION_JSON.toString());
                })
                .verifyComplete();
    }

    /**
     * 构建{@code errorResponse_shouldReturnErrorResponse}。
     */
    @Test
    @DisplayName("构建错误响应应返回正确的错误ServerResponse")
    void buildErrorResponse_shouldReturnErrorResponse() {
        StepVerifier.create(requestHandlerUtil.buildErrorResponse(400, "参数错误"))
                .assertNext(response -> {
                    assertThat(response.statusCode().value()).isEqualTo(400);
                    assertThat(response.headers().getContentType()).hasToString(MediaType.APPLICATION_JSON.toString());
                })
                .verifyComplete();
    }

    /**
     * 构建{@code errorResponse_withResultCode_shouldReturnErrorResponse}。
     */
    @Test
    @DisplayName("使用ResultCode构建错误响应应返回正确的错误ServerResponse")
    void buildErrorResponse_withResultCode_shouldReturnErrorResponse() {
        StepVerifier.create(requestHandlerUtil.buildErrorResponse(ResultCode.PARAM_ERROR, "参数错误"))
                .assertNext(response -> {
                    assertThat(response.statusCode().value()).isEqualTo(400);
                })
                .verifyComplete();
    }

    /**
     * 构建{@code unauthorizedResponse_shouldReturnUnauthorizedResponse}。
     */
    @Test
    @DisplayName("构建401错误响应应返回正确的ServerResponse")
    void buildUnauthorizedResponse_shouldReturnUnauthorizedResponse() {
        StepVerifier.create(requestHandlerUtil.buildUnauthorizedResponse("未授权"))
                .assertNext(response -> {
                    assertThat(response.statusCode().value()).isEqualTo(401);
                    assertThat(response.headers().getContentType()).hasToString(MediaType.APPLICATION_JSON.toString());
                })
                .verifyComplete();
    }

    /**
     * 执行 {@code extractToken_withAuthorizationHeader_shouldReturnToken} 操作。
     */
    @Test
    @DisplayName("提取请求头中的Token应返回正确的Token值")
    void extractToken_withAuthorizationHeader_shouldReturnToken() {
        ServerRequest.Headers headers = mock(ServerRequest.Headers.class);
        when(headers.firstHeader("Authorization")).thenReturn("Bearer test-token-value");
        when(serverRequest.headers()).thenReturn(headers);

        Optional<String> token = requestHandlerUtil.extractToken(serverRequest);

        assertThat(token).isPresent();
        assertThat(token.get()).isEqualTo("test-token-value");
    }

    /**
     * 执行 {@code extractToken_withoutAuthorizationHeader_shouldReturnEmpty} 操作。
     */
    @Test
    @DisplayName("请求头中无Token时应返回空Optional")
    void extractToken_withoutAuthorizationHeader_shouldReturnEmpty() {
        ServerRequest.Headers headers = mock(ServerRequest.Headers.class);
        when(headers.firstHeader("Authorization")).thenReturn(null);
        when(serverRequest.headers()).thenReturn(headers);

        Optional<String> token = requestHandlerUtil.extractToken(serverRequest);

        assertThat(token).isEmpty();
    }

    /**
     * 执行 {@code extractQueryParam_shouldReturnParamValue} 操作。
     */
    @Test
    @DisplayName("提取查询参数应返回正确的参数值")
    void extractQueryParam_shouldReturnParamValue() {
        when(serverRequest.queryParam("name")).thenReturn(Optional.of("test-value"));

        Optional<String> param = requestHandlerUtil.extractQueryParam(serverRequest, "name");

        assertThat(param).isPresent();
        assertThat(param.get()).isEqualTo("test-value");
    }

    /**
     * 执行 {@code extractPathParam_shouldReturnParamValue} 操作。
     */
    @Test
    @DisplayName("提取路径参数应返回正确的参数值")
    void extractPathParam_shouldReturnParamValue() {
        when(serverRequest.pathVariable("id")).thenReturn("123");

        String param = requestHandlerUtil.extractPathParam(serverRequest, "id");

        assertThat(param).isEqualTo("123");
    }
}
