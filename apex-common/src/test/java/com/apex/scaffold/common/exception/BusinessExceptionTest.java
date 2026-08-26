package com.apex.scaffold.common.exception;

import static org.junit.jupiter.api.Assertions.*;

import com.apex.scaffold.common.result.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * BusinessException 业务异常测试
 *
 * <p>验证 BusinessException 所有构造函数和继承关系
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("BusinessException 业务异常测试")
class BusinessExceptionTest {

    @Nested
    @DisplayName("构造函数测试：仅 message")
    class ConstructorWithMessageOnlyTest {

        /**
         * 执行 {@code shouldSetMessageAndDefaultErrorCode} 操作。
         */
        @Test
        @DisplayName("应设置 message 并自动使用 ERROR 错误码")
        void shouldSetMessageAndDefaultErrorCode() {
            BusinessException ex = new BusinessException("业务错误");

            assertEquals("业务错误", ex.getMessage());
            assertEquals("业务错误", ex.getMsg());
            assertEquals(ResultCode.ERROR.getCode(), ex.getCode());
        }
    }

    @Nested
    @DisplayName("构造函数测试：自定义 code + message")
    class ConstructorWithCodeAndMessageTest {

        /**
         * 执行 {@code shouldSetCustomCodeAndMessage} 操作。
         */
        @Test
        @DisplayName("应正确设置自定义 code 和 message")
        void shouldSetCustomCodeAndMessage() {
            BusinessException ex = new BusinessException(5001, "自定义业务错误");

            assertEquals(5001, ex.getCode());
            assertEquals("自定义业务错误", ex.getMessage());
            assertEquals("自定义业务错误", ex.getMsg());
        }
    }

    @Nested
    @DisplayName("构造函数测试：使用 ResultCode 枚举")
    class ConstructorWithResultCodeTest {

        /**
         * 执行 {@code shouldUseResultCode} 操作。
         */
        @Test
        @DisplayName("使用预定义错误码时应设置对应 code 和 msg")
        void shouldUseResultCode() {
            BusinessException ex = new BusinessException(ResultCode.USER_ALREADY_EXISTS);

            assertEquals(ResultCode.USER_ALREADY_EXISTS.getCode(), ex.getCode());
            assertEquals(ResultCode.USER_ALREADY_EXISTS.getMsg(), ex.getMsg());
        }

        /**
         * 执行 {@code shouldUseRateLimitResultCode} 操作。
         */
        @Test
        @DisplayName("使用 RATE_LIMIT 错误码时应正确设置")
        void shouldUseRateLimitResultCode() {
            BusinessException ex = new BusinessException(ResultCode.RATE_LIMIT);

            assertEquals(2001, ex.getCode());
            assertEquals("请求过于频繁", ex.getMsg());
        }

        /**
         * 执行 {@code shouldUseSkipLimitReachedResultCode} 操作。
         */
        @Test
        @DisplayName("使用 SKIP_LIMIT_REACHED 错误码时应正确设置")
        void shouldUseSkipLimitReachedResultCode() {
            BusinessException ex = new BusinessException(ResultCode.SKIP_LIMIT_REACHED);

            assertEquals(4201, ex.getCode());
            assertEquals("跳过次数已达上限", ex.getMsg());
        }
    }

    @Nested
    @DisplayName("构造函数测试：ResultCode + 自定义 message")
    class ConstructorWithResultCodeAndMessageTest {

        /**
         * 执行 {@code shouldUseCustomMessageWithResultCode} 操作。
         */
        @Test
        @DisplayName("msg 使用自定义 message，code 使用 ResultCode")
        void shouldUseCustomMessageWithResultCode() {
            BusinessException ex = new BusinessException(ResultCode.NOT_FOUND, "订单不存在");

            assertEquals(ResultCode.NOT_FOUND.getCode(), ex.getCode());
            assertEquals("订单不存在", ex.getMsg());
        }

        /**
         * 执行 {@code customMessageShouldOverrideResultCodeMsg} 操作。
         */
        @Test
        @DisplayName("自定义 message 应覆盖 ResultCode 的默认 msg")
        void customMessageShouldOverrideResultCodeMsg() {
            BusinessException ex = new BusinessException(ResultCode.PROFILE_INCOMPLETE, "请先完善个人资料");

            assertEquals(ResultCode.PROFILE_INCOMPLETE.getCode(), ex.getCode());
            assertEquals("请先完善个人资料", ex.getMsg());
            assertNotEquals(ResultCode.PROFILE_INCOMPLETE.getMsg(), ex.getMsg());
        }
    }

    @Nested
    @DisplayName("继承关系测试")
    class InheritanceTest {

        /**
         * 执行 {@code shouldExtendBaseException} 操作。
         */
        @Test
        @DisplayName("BusinessException 应继承自 BaseException")
        void shouldExtendBaseException() {
            BusinessException ex = new BusinessException("test");
            assertInstanceOf(BaseException.class, ex);
        }

        /**
         * 执行 {@code shouldExtendRuntimeException} 操作。
         */
        @Test
        @DisplayName("BusinessException 应继承自 RuntimeException")
        void shouldExtendRuntimeException() {
            BusinessException ex = new BusinessException("test");
            assertInstanceOf(RuntimeException.class, ex);
        }

        /**
         * 执行 {@code shouldExtendException} 操作。
         */
        @Test
        @DisplayName("BusinessException 应继承自 Exception")
        void shouldExtendException() {
            BusinessException ex = new BusinessException("test");
            assertInstanceOf(Exception.class, ex);
        }

        /**
         * 执行 {@code shouldNotBeSameClassAsBaseException} 操作。
         */
        @Test
        @DisplayName("BusinessException 实例不应是 BaseException 的相同类")
        void shouldNotBeSameClassAsBaseException() {
            BaseException base = new BaseException("test");
            BusinessException biz = new BusinessException("test");

            assertNotEquals(base.getClass(), biz.getClass());
        }
    }

    @Nested
    @DisplayName("所有构造函数一致性测试")
    class AllConstructorsConsistencyTest {

        /**
         * 执行 {@code codeShouldNeverBeNull} 操作。
         */
        @Test
        @DisplayName("任何构造方式都不应导致 code 为 null")
        void codeShouldNeverBeNull() {
            assertNotNull(new BusinessException("msg").getCode());
            assertNotNull(new BusinessException(500, "msg").getCode());
            assertNotNull(new BusinessException(ResultCode.ERROR).getCode());
            assertNotNull(new BusinessException(ResultCode.ERROR, "msg").getCode());
        }

        /**
         * 执行 {@code msgShouldNeverBeNull} 操作。
         */
        @Test
        @DisplayName("任何构造方式都不应导致 msg 为 null")
        void msgShouldNeverBeNull() {
            assertNotNull(new BusinessException("msg").getMsg());
            assertNotNull(new BusinessException(500, "msg").getMsg());
            assertNotNull(new BusinessException(ResultCode.ERROR).getMsg());
            assertNotNull(new BusinessException(ResultCode.ERROR, "msg").getMsg());
        }
    }
}
