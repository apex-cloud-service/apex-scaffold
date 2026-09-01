package com.apex.scaffold.infrastructure.chuanglan.exception;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 创蓝云平台API调用异常单元测试类
 *
 * <p>测试 ChuanglanApiException 的两种构造方法，包括带错误码构造和带cause构造，
 * 验证错误码、消息和cause的正确性。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
class ChuanglanApiExceptionTest {
    @Test
    @DisplayName("构造异常 - 带错误码和消息")
    void constructor_ErrorCodeAndMessage_SetsFields() {
        ChuanglanApiException ex = new ChuanglanApiException("E1001", "账号密码错误");

        assertThat(ex.getErrorCode()).isEqualTo("E1001");
        assertThat(ex.getMessage()).isEqualTo("账号密码错误");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    @DisplayName("构造异常 - 空错误码")
    void constructor_EmptyErrorCode_SetsFields() {
        ChuanglanApiException ex = new ChuanglanApiException("", "余额不足");

        assertThat(ex.getErrorCode()).isEqualTo("");
        assertThat(ex.getMessage()).isEqualTo("余额不足");
    }

    @Test
    @DisplayName("构造异常 - null错误码")
    void constructor_NullErrorCode_SetsFields() {
        ChuanglanApiException ex = new ChuanglanApiException((String) null, "未知错误");

        assertThat(ex.getErrorCode()).isNull();
        assertThat(ex.getMessage()).isEqualTo("未知错误");
    }

    @Test
    @DisplayName("构造异常 - 带消息和cause，错误码应为UNKNOWN")
    void constructor_MessageAndCause_SetsErrorCodeToUnknown() {
        Throwable cause = new java.net.SocketTimeoutException("连接超时");
        ChuanglanApiException ex = new ChuanglanApiException("网络请求失败", cause);

        assertThat(ex.getErrorCode()).isEqualTo("UNKNOWN");
        assertThat(ex.getMessage()).isEqualTo("网络请求失败");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    @DisplayName("构造异常 - 带消息和cause，cause为null")
    void constructor_MessageAndNullCause_SetsErrorCodeToUnknown() {
        ChuanglanApiException ex = new ChuanglanApiException("发生异常", (Throwable) null);

        assertThat(ex.getErrorCode()).isEqualTo("UNKNOWN");
        assertThat(ex.getMessage()).isEqualTo("发生异常");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    @DisplayName("ChuanglanApiException 是 RuntimeException 的子类")
    void exception_IsRuntimeException() {
        ChuanglanApiException ex = new ChuanglanApiException("E0001", "err");
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }
}
