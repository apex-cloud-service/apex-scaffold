package com.apex.scaffold.common.enums;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * UserStatus 用户状态枚举测试
 *
 * <p>验证 UserStatus 枚举的各个值和 getCode/getDesc 方法
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("UserStatus 用户状态枚举测试")
class UserStatusTest {

    @Nested
    @DisplayName("枚举值测试")
    class EnumValuesTest {
        @Test
        @DisplayName("应包含两个枚举值：NORMAL、DISABLED")
        void shouldHaveTwoValues() {
            UserStatus[] values = UserStatus.values();
            assertEquals(2, values.length);
        }

        @Test
        @DisplayName("NORMAL 的 code 应为 1，desc 应为 '正常'")
        void normalShouldHaveCorrectCodeAndDesc() {
            assertEquals(1, UserStatus.NORMAL.getCode());
            assertEquals("正常", UserStatus.NORMAL.getDesc());
        }

        @Test
        @DisplayName("DISABLED 的 code 应为 0，desc 应为 '禁用'")
        void disabledShouldHaveCorrectCodeAndDesc() {
            assertEquals(0, UserStatus.DISABLED.getCode());
            assertEquals("禁用", UserStatus.DISABLED.getDesc());
        }

        @Test
        @DisplayName("ordinal 值应正确排列")
        void ordinalsShouldBeCorrect() {
            assertEquals(0, UserStatus.NORMAL.ordinal());
            assertEquals(1, UserStatus.DISABLED.ordinal());
        }

        @Test
        @DisplayName("name 方法应返回枚举常量名称")
        void nameShouldReturnEnumConstantName() {
            assertEquals("NORMAL", UserStatus.NORMAL.name());
            assertEquals("DISABLED", UserStatus.DISABLED.name());
        }
    }

    @Nested
    @DisplayName("枚举属性测试")
    class EnumPropertiesTest {
        @Test
        @DisplayName("getCode 应返回 Integer 类型")
        void getCodeShouldReturnInteger() {
            assertInstanceOf(Integer.class, UserStatus.NORMAL.getCode());
            assertInstanceOf(Integer.class, UserStatus.DISABLED.getCode());
        }

        @Test
        @DisplayName("getDesc 应返回 String 类型")
        void getDescShouldReturnString() {
            assertInstanceOf(String.class, UserStatus.NORMAL.getDesc());
            assertInstanceOf(String.class, UserStatus.DISABLED.getDesc());
        }
    }

    @Nested
    @DisplayName("枚举使用场景测试")
    class UsageScenarioTest {
        @Test
        @DisplayName("NORMAL 和 DISABLED 的 code 应互不相同")
        void normalAndDisabledCodeShouldBeDifferent() {
            assertNotEquals(UserStatus.NORMAL.getCode(), UserStatus.DISABLED.getCode());
        }

        @Test
        @DisplayName("NORMAL 和 DISABLED 的 desc 应互不相同")
        void normalAndDisabledDescShouldBeDifferent() {
            assertNotEquals(UserStatus.NORMAL.getDesc(), UserStatus.DISABLED.getDesc());
        }

        @Test
        @DisplayName("valueOf 应正确解析枚举名称")
        void valueOfShouldParseCorrectly() {
            assertEquals(UserStatus.NORMAL, UserStatus.valueOf("NORMAL"));
            assertEquals(UserStatus.DISABLED, UserStatus.valueOf("DISABLED"));
        }

        @Test
        @DisplayName("非法 name 应抛出 IllegalArgumentException")
        void invalidNameShouldThrowException() {
            assertThrows(IllegalArgumentException.class, () -> UserStatus.valueOf("INVALID"));
        }
    }
}
