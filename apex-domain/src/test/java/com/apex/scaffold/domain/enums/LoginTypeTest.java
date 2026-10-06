package com.apex.scaffold.domain.enums;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link LoginType} 的单元测试类
 *
 * <p>测试登录类型枚举的常量定义、name() 和 ordinal() 方法
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
class LoginTypeTest {

    @Nested
    @DisplayName("枚举常量定义")
    class EnumConstantsTest {

        /**
         *
         * <p>v2.2.0 起新增 {@link LoginType#PASSWORD}（账号密码登录）；
         * v3.3.0 起新增 {@link LoginType#WX_MINI}（微信小程序登录）与
         * {@link LoginType#WX_OAUTH}（微信公众号网页授权登录）。</p>
         */
        @Test
        @DisplayName("应包含 8 个枚举常量（含 PASSWORD、WX_MINI 与 WX_OAUTH）")
        void shouldHaveEightConstants() {
            assertThat(LoginType.values()).hasSize(8);
            assertThat(LoginType.valueOf("PASSWORD")).isEqualTo(LoginType.PASSWORD);
            assertThat(LoginType.valueOf("WX_MINI")).isEqualTo(LoginType.WX_MINI);
            assertThat(LoginType.valueOf("WX_OAUTH")).isEqualTo(LoginType.WX_OAUTH);
        }

        @Test
        @DisplayName("应包含 WX_CODE 常量")
        void shouldContainWxCode() {
            assertThat(LoginType.valueOf("WX_CODE")).isEqualTo(LoginType.WX_CODE);
        }

        @Test
        @DisplayName("应包含 WX_PHONE 常量")
        void shouldContainWxPhone() {
            assertThat(LoginType.valueOf("WX_PHONE")).isEqualTo(LoginType.WX_PHONE);
        }

        @Test
        @DisplayName("应包含 WX_SMS 常量")
        void shouldContainWxSms() {
            assertThat(LoginType.valueOf("WX_SMS")).isEqualTo(LoginType.WX_SMS);
        }

        @Test
        @DisplayName("应包含 CL_PHONE 常量")
        void shouldContainClPhone() {
            assertThat(LoginType.valueOf("CL_PHONE")).isEqualTo(LoginType.CL_PHONE);
        }

        @Test
        @DisplayName("应包含 CL_SMS 常量")
        void shouldContainClSms() {
            assertThat(LoginType.valueOf("CL_SMS")).isEqualTo(LoginType.CL_SMS);
        }
    }

    @Nested
    @DisplayName("name() 方法")
    class NameMethodTest {
        @Test
        @DisplayName("WX_CODE 的 name() 应返回 WX_CODE")
        void wxCodeNameShouldBeWX_CODE() {
            assertThat(LoginType.WX_CODE.name()).isEqualTo("WX_CODE");
        }

        @Test
        @DisplayName("WX_PHONE 的 name() 应返回 WX_PHONE")
        void wxPhoneNameShouldBeWX_PHONE() {
            assertThat(LoginType.WX_PHONE.name()).isEqualTo("WX_PHONE");
        }

        @Test
        @DisplayName("WX_SMS 的 name() 应返回 WX_SMS")
        void wxSmsNameShouldBeWX_SMS() {
            assertThat(LoginType.WX_SMS.name()).isEqualTo("WX_SMS");
        }

        @Test
        @DisplayName("CL_PHONE 的 name() 应返回 CL_PHONE")
        void clPhoneNameShouldBeCL_PHONE() {
            assertThat(LoginType.CL_PHONE.name()).isEqualTo("CL_PHONE");
        }

        @Test
        @DisplayName("CL_SMS 的 name() 应返回 CL_SMS")
        void clSmsNameShouldBeCL_SMS() {
            assertThat(LoginType.CL_SMS.name()).isEqualTo("CL_SMS");
        }
    }

    @Nested
    @DisplayName("ordinal() 方法")
    class OrdinalMethodTest {
        @Test
        @DisplayName("WX_CODE 的 ordinal 应为 0")
        void wxCodeOrdinalShouldBeZero() {
            assertThat(LoginType.WX_CODE.ordinal()).isZero();
        }

        @Test
        @DisplayName("WX_PHONE 的 ordinal 应为 1")
        void wxPhoneOrdinalShouldBeOne() {
            assertThat(LoginType.WX_PHONE.ordinal()).isEqualTo(1);
        }

        @Test
        @DisplayName("WX_SMS 的 ordinal 应为 2")
        void wxSmsOrdinalShouldBeTwo() {
            assertThat(LoginType.WX_SMS.ordinal()).isEqualTo(2);
        }

        @Test
        @DisplayName("CL_PHONE 的 ordinal 应为 3")
        void clPhoneOrdinalShouldBeThree() {
            assertThat(LoginType.CL_PHONE.ordinal()).isEqualTo(3);
        }

        @Test
        @DisplayName("CL_SMS 的 ordinal 应为 4")
        void clSmsOrdinalShouldBeFour() {
            assertThat(LoginType.CL_SMS.ordinal()).isEqualTo(4);
        }
    }

    @Nested
    @DisplayName("values() 方法")
    class ValuesMethodTest {
        @Test
        @DisplayName("values() 应返回按声明顺序排列的常量")
        void valuesShouldReturnConstantsInDeclarationOrder() {
            LoginType[] values = LoginType.values();
            assertThat(values[0]).isEqualTo(LoginType.WX_CODE);
            assertThat(values[1]).isEqualTo(LoginType.WX_PHONE);
            assertThat(values[2]).isEqualTo(LoginType.WX_SMS);
            assertThat(values[3]).isEqualTo(LoginType.CL_PHONE);
            assertThat(values[4]).isEqualTo(LoginType.CL_SMS);
        }
    }
}
