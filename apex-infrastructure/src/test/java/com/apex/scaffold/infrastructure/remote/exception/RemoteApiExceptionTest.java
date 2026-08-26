package com.apex.scaffold.infrastructure.remote.exception;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 远程API调用异常单元测试类
 *
 * <p>测试 RemoteApiException 的构造方法和业务方法，涵盖 HTTP 状态码异常、网络层异常、
 * 简短消息异常三种构造方式，以及 isClientError 和 isServerError 方法的各种边界情况。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
class RemoteApiExceptionTest {

    /**
     * 执行 {@code constructor_HttpStatusCode_SetsAllFields} 操作。
     */
    @Test
    @DisplayName("构造HTTP状态码异常 - 应正确设置所有字段")
    void constructor_HttpStatusCode_SetsAllFields() {
        RemoteApiException ex =
                new RemoteApiException(404, "user-service", "/api/users/1", "{\"error\":\"Not Found\"}", "用户服务返回404");

        assertThat(ex.getHttpStatus()).isEqualTo(404);
        assertThat(ex.getServiceName()).isEqualTo("user-service");
        assertThat(ex.getRequestPath()).isEqualTo("/api/users/1");
        assertThat(ex.getErrorBody()).isEqualTo("{\"error\":\"Not Found\"}");
        assertThat(ex.getMessage()).isEqualTo("用户服务返回404");
        assertThat(ex.getCause()).isNull();
    }

    /**
     * 执行 {@code constructor_Http500StatusCode_SetsFields} 操作。
     */
    @Test
    @DisplayName("构造HTTP状态码异常 - 5xx状态码")
    void constructor_Http500StatusCode_SetsFields() {
        RemoteApiException ex =
                new RemoteApiException(502, "order-service", "/api/orders", "Service Unavailable", "订单服务不可用");

        assertThat(ex.getHttpStatus()).isEqualTo(502);
        assertThat(ex.getServiceName()).isEqualTo("order-service");
        assertThat(ex.getRequestPath()).isEqualTo("/api/orders");
        assertThat(ex.getErrorBody()).isEqualTo("Service Unavailable");
    }

    /**
     * 执行 {@code constructor_NetworkError_SetsHttpStatusMinusOneAndCause} 操作。
     */
    @Test
    @DisplayName("构造网络层异常 - 应设置httpStatus为-1并包含cause")
    void constructor_NetworkError_SetsHttpStatusMinusOneAndCause() {
        Throwable cause = new java.net.ConnectException("Connection refused");
        RemoteApiException ex = new RemoteApiException("auth-service", "/api/auth/login", "连接认证服务超时", cause);

        assertThat(ex.getHttpStatus()).isEqualTo(-1);
        assertThat(ex.getServiceName()).isEqualTo("auth-service");
        assertThat(ex.getRequestPath()).isEqualTo("/api/auth/login");
        assertThat(ex.getErrorBody()).isNull();
        assertThat(ex.getMessage()).isEqualTo("连接认证服务超时");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    /**
     * 执行 {@code constructor_ShortMessage_SetsMinusOneAndNullFields} 操作。
     */
    @Test
    @DisplayName("构造简短消息异常 - 应设置httpStatus为-1且serviceName和requestPath为null")
    void constructor_ShortMessage_SetsMinusOneAndNullFields() {
        Throwable cause = new RuntimeException("原始异常");
        RemoteApiException ex = new RemoteApiException("发生未知错误", cause);

        assertThat(ex.getHttpStatus()).isEqualTo(-1);
        assertThat(ex.getServiceName()).isNull();
        assertThat(ex.getRequestPath()).isNull();
        assertThat(ex.getErrorBody()).isNull();
        assertThat(ex.getMessage()).isEqualTo("发生未知错误");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    /**
     * 判断是否{@code clientError_4xx_ReturnsTrue}。
     */
    @Test
    @DisplayName("isClientError - 4xx状态码应返回true")
    void isClientError_4xx_ReturnsTrue() {
        RemoteApiException ex400 = new RemoteApiException(400, "svc", "/path", null, "Bad Request");
        RemoteApiException ex401 = new RemoteApiException(401, "svc", "/path", null, "Unauthorized");
        RemoteApiException ex403 = new RemoteApiException(403, "svc", "/path", null, "Forbidden");
        RemoteApiException ex404 = new RemoteApiException(404, "svc", "/path", null, "Not Found");
        RemoteApiException ex499 = new RemoteApiException(499, "svc", "/path", null, "Client Closed");

        assertThat(ex400.isClientError()).isTrue();
        assertThat(ex401.isClientError()).isTrue();
        assertThat(ex403.isClientError()).isTrue();
        assertThat(ex404.isClientError()).isTrue();
        assertThat(ex499.isClientError()).isTrue();
    }

    /**
     * 判断是否{@code clientError_Non4xx_ReturnsFalse}。
     */
    @Test
    @DisplayName("isClientError - 非4xx状态码应返回false")
    void isClientError_Non4xx_ReturnsFalse() {
        RemoteApiException ex200 = new RemoteApiException(200, "svc", "/path", null, "OK");
        RemoteApiException ex500 = new RemoteApiException(500, "svc", "/path", null, "Server Error");
        RemoteApiException exMinus1 = new RemoteApiException("svc", "/path", "err", new Exception());

        assertThat(ex200.isClientError()).isFalse();
        assertThat(ex500.isClientError()).isFalse();
        assertThat(exMinus1.isClientError()).isFalse();
    }

    /**
     * 判断是否{@code serverError_5xx_ReturnsTrue}。
     */
    @Test
    @DisplayName("isServerError - 5xx状态码应返回true")
    void isServerError_5xx_ReturnsTrue() {
        RemoteApiException ex500 = new RemoteApiException(500, "svc", "/path", null, "Internal Error");
        RemoteApiException ex502 = new RemoteApiException(502, "svc", "/path", null, "Bad Gateway");
        RemoteApiException ex503 = new RemoteApiException(503, "svc", "/path", null, "Service Unavailable");
        RemoteApiException ex599 = new RemoteApiException(599, "svc", "/path", null, "Unknown");

        assertThat(ex500.isServerError()).isTrue();
        assertThat(ex502.isServerError()).isTrue();
        assertThat(ex503.isServerError()).isTrue();
        assertThat(ex599.isServerError()).isTrue();
    }

    /**
     * 判断是否{@code serverError_Non5xx_ReturnsFalse}。
     */
    @Test
    @DisplayName("isServerError - 非5xx状态码应返回false")
    void isServerError_Non5xx_ReturnsFalse() {
        RemoteApiException ex400 = new RemoteApiException(400, "svc", "/path", null, "Bad Request");
        RemoteApiException ex600 = new RemoteApiException(600, "svc", "/path", null, "Unknown");

        assertThat(ex400.isServerError()).isFalse();
        assertThat(ex600.isServerError()).isFalse();
    }

    /**
     * 判断是否{@code serverError_MinusOne_ReturnsFalse}。
     */
    @Test
    @DisplayName("isServerError - -1状态码应返回false")
    void isServerError_MinusOne_ReturnsFalse() {
        RemoteApiException ex = new RemoteApiException("svc", "/path", "err", new Exception());
        assertThat(ex.isServerError()).isFalse();
    }

    /**
     * 执行 {@code exception_IsRuntimeException} 操作。
     */
    @Test
    @DisplayName("RemoteApiException 是 RuntimeException 的子类")
    void exception_IsRuntimeException() {
        RemoteApiException ex = new RemoteApiException(500, "svc", "/path", null, "err");
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }
}
