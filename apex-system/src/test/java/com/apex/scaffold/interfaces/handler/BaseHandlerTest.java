package com.apex.scaffold.interfaces.handler;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link BaseHandler} 抽象基类的单元测试类
 *
 * <p>测试 safeParseLong、safeParseInt 等安全解析方法。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("BaseHandler 单元测试")
class BaseHandlerTest {

    /** 测试用具体 Handler 子类 */
    static class TestHandler extends BaseHandler {

        TestHandler() {
            super(null);
        }
    }

    private TestHandler handler;

    /**
     * 设置{@code up}。
     */
    @BeforeEach
    void setUp() {
        handler = new TestHandler();
    }

    @Nested
    @DisplayName("safeParseLong 方法")
    class SafeParseLongTests {
        @Test
        @DisplayName("有效数字应返回对应 Long 值")
        void shouldParseValidLong() {
            assertThat(handler.safeParseLong("123")).isEqualTo(123L);
            assertThat(handler.safeParseLong("0")).isEqualTo(0L);
            assertThat(handler.safeParseLong("-1")).isEqualTo(-1L);
        }

        @Test
        @DisplayName("null 应返回 null")
        void shouldReturnNullForNull() {
            assertThat(handler.safeParseLong(null)).isNull();
        }

        @Test
        @DisplayName("空字符串应返回 null")
        void shouldReturnNullForBlank() {
            assertThat(handler.safeParseLong("")).isNull();
            assertThat(handler.safeParseLong("   ")).isNull();
        }

        @Test
        @DisplayName("非数字应返回 null")
        void shouldReturnNullForNonNumeric() {
            assertThat(handler.safeParseLong("abc")).isNull();
            assertThat(handler.safeParseLong("12.5")).isNull();
            assertThat(handler.safeParseLong("12L")).isNull();
        }

        @Test
        @DisplayName("前后带空格的数字应正确解析")
        void shouldTrimAndParse() {
            assertThat(handler.safeParseLong("  456  ")).isEqualTo(456L);
        }
    }

    @Nested
    @DisplayName("safeParseInt 方法")
    class SafeParseIntTests {
        @Test
        @DisplayName("有效数字应返回对应值")
        void shouldParseValidInt() {
            assertThat(handler.safeParseInt("42", 1)).isEqualTo(42);
            assertThat(handler.safeParseInt("100", 1)).isEqualTo(100);
        }

        @Test
        @DisplayName("null 应返回默认值")
        void shouldReturnDefaultForNull() {
            assertThat(handler.safeParseInt(null, 10)).isEqualTo(10);
        }

        @Test
        @DisplayName("空字符串应返回默认值")
        void shouldReturnDefaultForBlank() {
            assertThat(handler.safeParseInt("", 5)).isEqualTo(5);
        }

        @Test
        @DisplayName("非数字应返回默认值")
        void shouldReturnDefaultForNonNumeric() {
            assertThat(handler.safeParseInt("abc", 20)).isEqualTo(20);
        }

        @Test
        @DisplayName("0 应返回 0，负数应返回默认值")
        void shouldReturnZeroForZeroDefaultForNegative() {
            assertThat(handler.safeParseInt("0", 1)).isEqualTo(0);
            assertThat(handler.safeParseInt("-5", 10)).isEqualTo(10);
        }
    }
}
