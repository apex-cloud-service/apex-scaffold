package com.apex.scaffold.interfaces.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.util.TraceIdUtil;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.test.StepVerifier;

/**
 * HTTP JSON 响应写入工具类单元测试
 *
 * <p>验证 ResponseUtil 各静态方法能正确写入 JSON 格式的 HTTP 响应，
 * 包括状态码、Content-Type 和响应体内容的正确性
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("ResponseUtil 响应写入工具类测试")
class ResponseUtilTest {

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

    @AfterEach
    void tearDown() {
        traceIdUtilMockedStatic.close();
    }

    @Test
    @DisplayName("工具类构造函数应为私有")
    void constructor_shouldBePrivate() throws Exception {
        Constructor<ResponseUtil> constructor = ResponseUtil.class.getDeclaredConstructor();
        assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
        constructor.setAccessible(true);
        assertThatThrownBy(constructor::newInstance)
                .isInstanceOf(InvocationTargetException.class)
                .cause()
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("工具类不能实例化");
    }

    @Test
    @DisplayName("writeJson 应正确写入JSON响应")
    void writeJson_shouldWriteJsonResponse() {
        ServerWebExchange exchange =
                MockServerWebExchange.from(MockServerHttpRequest.get("/test").build());

        Result<String> result = Result.success("test-data", TEST_TRACE_ID);

        StepVerifier.create(ResponseUtil.writeJson(exchange, HttpStatus.OK, result))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exchange.getResponse().getHeaders().getContentType()).hasToString("application/json");
    }

    @Test
    @DisplayName("unauthorized 应返回401未授权响应")
    void unauthorized_shouldReturn401Response() {
        ServerWebExchange exchange =
                MockServerWebExchange.from(MockServerHttpRequest.get("/test").build());

        StepVerifier.create(ResponseUtil.unauthorized(exchange, "未授权，请先登录")).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getContentType()).hasToString("application/json");
    }

    @Test
    @DisplayName("forbidden 应返回403禁止访问响应")
    void forbidden_shouldReturn403Response() {
        ServerWebExchange exchange =
                MockServerWebExchange.from(MockServerHttpRequest.get("/test").build());

        StepVerifier.create(ResponseUtil.forbidden(exchange, "无权限访问")).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange.getResponse().getHeaders().getContentType()).hasToString("application/json");
    }

    @Test
    @DisplayName("error 应返回自定义错误响应")
    void error_shouldReturnCustomErrorResponse() {
        ServerWebExchange exchange =
                MockServerWebExchange.from(MockServerHttpRequest.get("/test").build());

        StepVerifier.create(
                        ResponseUtil.error(exchange, HttpStatus.BAD_REQUEST, ResultCode.PARAM_ERROR.getCode(), "参数错误"))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exchange.getResponse().getHeaders().getContentType()).hasToString("application/json");
    }
}
