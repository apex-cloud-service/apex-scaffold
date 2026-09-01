package com.apex.scaffold.common.exception;

import static org.junit.jupiter.api.Assertions.*;

import com.apex.scaffold.common.result.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * BaseException 基础异常类测试
 *
 * <p>验证 BaseException 所有构造函数、code/msg 字段和继承关系
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("BaseException 基础异常测试")
class BaseExceptionTest {

    @Nested
    @DisplayName("构造函数测试：仅消息")
    class ConstructorWithMessageOnlyTest {
        @Test
        @DisplayName("应设置 message 并自动使用 ERROR 错误码")
        void shouldSetMessageAndDefaultErrorCode() {
            BaseException ex = new BaseException("测试错误");

            assertEquals("测试错误", ex.getMessage());
            assertEquals("测试错误", ex.getMsg());
            assertEquals(ResultCode.ERROR.getCode(), ex.getCode());
        }
    }

    @Nested
    @DisplayName("构造函数测试：自定义 code 和 message")
    class ConstructorWithCodeAndMessageTest {
        @Test
        @DisplayName("应正确设置自定义 code 和 message")
        void shouldSetCustomCodeAndMessage() {
            BaseException ex = new BaseException(1001, "自定义错误");

            assertEquals(1001, ex.getCode());
            assertEquals("自定义错误", ex.getMessage());
            assertEquals("自定义错误", ex.getMsg());
        }
    }

    @Nested
    @DisplayName("构造函数测试：使用 ResultCode 枚举")
    class ConstructorWithResultCodeTest {
        @Test
        @DisplayName("使用 SUCCESS 枚举时应设置对应 code 和 msg")
        void shouldUseSuccessResultCode() {
            BaseException ex = new BaseException(ResultCode.SUCCESS);

            assertEquals(ResultCode.SUCCESS.getCode(), ex.getCode());
            assertEquals(ResultCode.SUCCESS.getMsg(), ex.getMsg());
            assertEquals(ResultCode.SUCCESS.getMsg(), ex.getMessage());
        }

        @Test
        @DisplayName("使用 NOT_FOUND 枚举时应设置对应 code 和 msg")
        void shouldUseNotFoundResultCode() {
            BaseException ex = new BaseException(ResultCode.NOT_FOUND);

            assertEquals(404, ex.getCode());
            assertEquals("资源不存在", ex.getMsg());
        }

        @Test
        @DisplayName("使用 RATE_LIMIT 枚举时应设置对应 code 和 msg")
        void shouldUseRateLimitResultCode() {
            BaseException ex = new BaseException(ResultCode.RATE_LIMIT);

            assertEquals(2001, ex.getCode());
            assertEquals("请求过于频繁", ex.getMsg());
        }
    }

    @Nested
    @DisplayName("构造函数测试：ResultCode + 自定义 message")
    class ConstructorWithResultCodeAndMessageTest {
        @Test
        @DisplayName("msg 应使用自定义 message，code 应使用 ResultCode 的 code")
        void shouldUseCustomMessageWithResultCode() {
            BaseException ex = new BaseException(ResultCode.NOT_FOUND, "用户未找到");

            assertEquals(ResultCode.NOT_FOUND.getCode(), ex.getCode());
            assertEquals("用户未找到", ex.getMsg());
            assertEquals("用户未找到", ex.getMessage());
        }

        @Test
        @DisplayName("自定义 message 应与 ResultCode.msg 独立")
        void customMessageShouldBeIndependentOfResultCodeMsg() {
            BaseException ex = new BaseException(ResultCode.ERROR, "自定义系统错误");

            assertEquals(ResultCode.ERROR.getCode(), ex.getCode());
            assertEquals("自定义系统错误", ex.getMsg());
            assertNotEquals(ResultCode.ERROR.getMsg(), ex.getMsg());
        }
    }

    @Nested
    @DisplayName("构造函数测试：message + Throwable cause")
    class ConstructorWithMessageAndCauseTest {
        @Test
        @DisplayName("应设置 message、cause 和默认 ERROR 错误码")
        void shouldSetMessageCauseAndDefaultErrorCode() {
            RuntimeException cause = new RuntimeException("原始错误");
            BaseException ex = new BaseException("包装错误", cause);

            assertEquals("包装错误", ex.getMessage());
            assertEquals("包装错误", ex.getMsg());
            assertEquals(ResultCode.ERROR.getCode(), ex.getCode());
            assertSame(cause, ex.getCause());
        }
    }

    @Nested
    @DisplayName("继承关系测试")
    class InheritanceTest {
        @Test
        @DisplayName("BaseException 应继承自 RuntimeException")
        void shouldExtendRuntimeException() {
            BaseException ex = new BaseException("test");
            assertInstanceOf(RuntimeException.class, ex);
        }

        @Test
        @DisplayName("BaseException 应继承自 Exception")
        void shouldExtendException() {
            BaseException ex = new BaseException("test");
            assertInstanceOf(Exception.class, ex);
        }
    }

    @Nested
    @DisplayName("getter 方法测试")
    class GetterTest {
        @Test
        @DisplayName("getMsg 和 getMessage 应返回相同值（构造时一致）")
        void getMsgAndGetMessageShouldReturnSame() {
            BaseException ex = new BaseException(500, "服务器内部错误");

            assertEquals(ex.getMessage(), ex.getMsg());
        }

        @Test
        @DisplayName("getCode 不应为 null")
        void getCodeShouldNotBeNull() {
            BaseException ex = new BaseException("test");
            assertNotNull(ex.getCode());
        }
    }
}
