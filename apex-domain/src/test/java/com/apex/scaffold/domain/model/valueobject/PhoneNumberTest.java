package com.apex.scaffold.domain.model.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link PhoneNumber} 值对象的单元测试类
 *
 * <p>测试手机号校验、标准化、脱敏、格式化等核心逻辑。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("PhoneNumber 值对象单元测试")
class PhoneNumberTest {

    @Nested
    @DisplayName("of 工厂方法")
    class OfTests {

        /**
         * 执行 {@code shouldCreateWithValidPhoneNumber} 操作。
         */
        @Test
        @DisplayName("有效的11位手机号应创建成功")
        void shouldCreateWithValidPhoneNumber() {
            PhoneNumber phone = PhoneNumber.of("13812345678");
            assertThat(phone.getValue()).isEqualTo("13812345678");
            assertThat(phone.getNormalized()).isEqualTo("13812345678");
        }

        /**
         * 执行 {@code shouldCreateWithCountryCodePrefix} 操作。
         */
        @Test
        @DisplayName("带+86前缀的手机号应创建成功")
        void shouldCreateWithCountryCodePrefix() {
            PhoneNumber phone = PhoneNumber.of("+8613812345678");
            assertThat(phone.getNormalized()).isEqualTo("13812345678");
        }

        /**
         * 执行 {@code shouldCreateWith86Prefix} 操作。
         */
        @Test
        @DisplayName("带86前缀的手机号应创建成功")
        void shouldCreateWith86Prefix() {
            PhoneNumber phone = PhoneNumber.of("8613812345678");
            assertThat(phone.getNormalized()).isEqualTo("13812345678");
        }

        /**
         * 执行 {@code shouldCreateWithFormattingCharacters} 操作。
         */
        @Test
        @DisplayName("带空格或短横线的手机号应创建成功")
        void shouldCreateWithFormattingCharacters() {
            PhoneNumber phone = PhoneNumber.of("138-1234-5678");
            assertThat(phone.getNormalized()).isEqualTo("13812345678");
        }

        /**
         * 执行 {@code shouldThrowExceptionWhenNull} 操作。
         */
        @Test
        @DisplayName("null 手机号应抛出异常")
        void shouldThrowExceptionWhenNull() {
            assertThatThrownBy(() -> PhoneNumber.of(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("手机号不能为空");
        }

        /**
         * 执行 {@code shouldThrowExceptionWhenBlank} 操作。
         */
        @Test
        @DisplayName("空字符串手机号应抛出异常")
        void shouldThrowExceptionWhenBlank() {
            assertThatThrownBy(() -> PhoneNumber.of("   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("手机号不能为空");
        }

        /**
         * 执行 {@code shouldThrowExceptionWhenInvalidLength} 操作。
         */
        @Test
        @DisplayName("非11位数字应抛出异常")
        void shouldThrowExceptionWhenInvalidLength() {
            assertThatThrownBy(() -> PhoneNumber.of("1381234567"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("无效的手机号格式");
        }

        /**
         * 执行 {@code shouldThrowExceptionWhenNotStartingWithOne} 操作。
         */
        @Test
        @DisplayName("非1开头的手机号应抛出异常")
        void shouldThrowExceptionWhenNotStartingWithOne() {
            assertThatThrownBy(() -> PhoneNumber.of("23812345678"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("无效的手机号格式");
        }
    }

    @Nested
    @DisplayName("ofNullable 工厂方法")
    class OfNullableTests {

        /**
         * 执行 {@code shouldReturnPhoneWhenValid} 操作。
         */
        @Test
        @DisplayName("有效手机号应返回对象")
        void shouldReturnPhoneWhenValid() {
            assertThat(PhoneNumber.ofNullable("13812345678")).isNotNull();
        }

        /**
         * 执行 {@code shouldReturnNullWhenInvalid} 操作。
         */
        @Test
        @DisplayName("无效手机号应返回 null")
        void shouldReturnNullWhenInvalid() {
            assertThat(PhoneNumber.ofNullable("12345")).isNull();
        }

        /**
         * 执行 {@code shouldReturnNullWhenNullInput} 操作。
         */
        @Test
        @DisplayName("null 输入应返回 null")
        void shouldReturnNullWhenNullInput() {
            assertThat(PhoneNumber.ofNullable(null)).isNull();
        }
    }

    @Nested
    @DisplayName("isValid 静态方法")
    class IsValidTests {

        /**
         * 执行 {@code shouldReturnTrueForValidPhone} 操作。
         */
        @Test
        @DisplayName("有效手机号应返回 true")
        void shouldReturnTrueForValidPhone() {
            assertThat(PhoneNumber.isValid("13812345678")).isTrue();
        }

        /**
         * 执行 {@code shouldReturnFalseForNull} 操作。
         */
        @Test
        @DisplayName("null 应返回 false")
        void shouldReturnFalseForNull() {
            assertThat(PhoneNumber.isValid(null)).isFalse();
        }

        /**
         * 执行 {@code shouldReturnFalseForBlank} 操作。
         */
        @Test
        @DisplayName("空字符串应返回 false")
        void shouldReturnFalseForBlank() {
            assertThat(PhoneNumber.isValid("")).isFalse();
        }

        /**
         * 执行 {@code shouldReturnFalseForInvalidFormat} 操作。
         */
        @Test
        @DisplayName("格式不正确的手机号应返回 false")
        void shouldReturnFalseForInvalidFormat() {
            assertThat(PhoneNumber.isValid("12345")).isFalse();
        }
    }

    @Nested
    @DisplayName("getMasked 方法")
    class GetMaskedTests {

        /**
         * 执行 {@code shouldMaskPhoneNumber} 操作。
         */
        @Test
        @DisplayName("11位手机号应脱敏为 138****5678 格式")
        void shouldMaskPhoneNumber() {
            PhoneNumber phone = PhoneNumber.of("13812345678");
            assertThat(phone.getMasked()).isEqualTo("138****5678");
        }
    }

    @Nested
    @DisplayName("getFormatted 方法")
    class GetFormattedTests {

        /**
         * 执行 {@code shouldFormatPhoneNumber} 操作。
         */
        @Test
        @DisplayName("11位手机号应格式化为 138-1234-5678")
        void shouldFormatPhoneNumber() {
            PhoneNumber phone = PhoneNumber.of("13812345678");
            assertThat(phone.getFormatted()).isEqualTo("138-1234-5678");
        }
    }

    @Nested
    @DisplayName("getWithCountryCode 方法")
    class GetWithCountryCodeTests {

        /**
         * 执行 {@code shouldReturnWithCountryCode} 操作。
         */
        @Test
        @DisplayName("应返回带+86国家码的手机号")
        void shouldReturnWithCountryCode() {
            PhoneNumber phone = PhoneNumber.of("13812345678");
            assertThat(phone.getWithCountryCode()).isEqualTo("+86 13812345678");
        }
    }

    @Nested
    @DisplayName("equalsPhone 方法")
    class EqualsPhoneTests {

        /**
         * 执行 {@code shouldBeEqualWithSameNormalized} 操作。
         */
        @Test
        @DisplayName("相同归一化手机号应判断为相等")
        void shouldBeEqualWithSameNormalized() {
            PhoneNumber phone1 = PhoneNumber.of("13812345678");
            PhoneNumber phone2 = PhoneNumber.of("+8613812345678");
            assertThat(phone1.equalsPhone(phone2)).isTrue();
        }

        /**
         * 执行 {@code shouldNotBeEqualWithDifferentNumber} 操作。
         */
        @Test
        @DisplayName("不同手机号应判断为不相等")
        void shouldNotBeEqualWithDifferentNumber() {
            PhoneNumber phone1 = PhoneNumber.of("13812345678");
            PhoneNumber phone2 = PhoneNumber.of("13987654321");
            assertThat(phone1.equalsPhone(phone2)).isFalse();
        }

        /**
         * 执行 {@code shouldReturnFalseWhenCompareWithNull} 操作。
         */
        @Test
        @DisplayName("与 null 比较应返回 false")
        void shouldReturnFalseWhenCompareWithNull() {
            PhoneNumber phone = PhoneNumber.of("13812345678");
            assertThat(phone.equalsPhone(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("toString 方法")
    class ToStringTests {

        /**
         * 执行 {@code shouldReturnMaskedPhone} 操作。
         */
        @Test
        @DisplayName("toString 应返回脱敏后的手机号")
        void shouldReturnMaskedPhone() {
            PhoneNumber phone = PhoneNumber.of("13812345678");
            assertThat(phone.toString()).isEqualTo("138****5678");
        }
    }
}
