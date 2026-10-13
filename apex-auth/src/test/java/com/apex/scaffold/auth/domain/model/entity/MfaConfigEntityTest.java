package com.apex.scaffold.auth.domain.model.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * MfaConfigEntity 单元测试
 *
 * @author Apex Scaffold Team
 * @since 2026-09-21
 */
@DisplayName("MfaConfigEntity 单元测试")
class MfaConfigEntityTest {

    private MfaConfigEntity mfaConfig;

    @BeforeEach
    void setUp() {
        mfaConfig = MfaConfigEntity.builder()
                .id(1L)
                .userId(100L)
                .tenantId("test-tenant")
                .mfaType("TOTP")
                .enabled(false)
                .required(false)
                .priority(1)
                .metadata("{\"issuer\":\"Apex Scaffold\"}")
                .version(1)
                .deleted(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("enable() 方法测试")
    class EnableTest {

        @Test
        @DisplayName("启用 MFA 后 enabled 为 true")
        void testEnable() {
            mfaConfig.setEnabled(false);

            mfaConfig.enable();

            assertThat(mfaConfig.getEnabled()).isTrue();
            assertThat(mfaConfig.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("重复启用不会出错")
        void testEnableWhenAlreadyEnabled() {
            mfaConfig.setEnabled(true);

            mfaConfig.enable();

            assertThat(mfaConfig.getEnabled()).isTrue();
        }
    }

    @Nested
    @DisplayName("disable() 方法测试")
    class DisableTest {

        @Test
        @DisplayName("非必需的 MFA 可以禁用")
        void testDisableOptionalMfa() {
            mfaConfig.setEnabled(true);
            mfaConfig.setRequired(false);

            mfaConfig.disable();

            assertThat(mfaConfig.getEnabled()).isFalse();
            assertThat(mfaConfig.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("必需的 MFA 无法禁用，抛出异常")
        void testDisableRequiredMfa() {
            mfaConfig.setEnabled(true);
            mfaConfig.setRequired(true);

            assertThatThrownBy(() -> mfaConfig.disable())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot disable required MFA");
        }

        @Test
        @DisplayName("required 为 null 时可以禁用")
        void testDisableWhenRequiredIsNull() {
            mfaConfig.setEnabled(true);
            mfaConfig.setRequired(null);

            mfaConfig.disable();

            assertThat(mfaConfig.getEnabled()).isFalse();
        }

        @Test
        @DisplayName("required 为 false 时可以禁用")
        void testDisableWhenRequiredIsFalse() {
            mfaConfig.setEnabled(true);
            mfaConfig.setRequired(false);

            mfaConfig.disable();

            assertThat(mfaConfig.getEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("makeRequired() 方法测试")
    class MakeRequiredTest {

        @Test
        @DisplayName("设置为必需后，required 和 enabled 都为 true")
        void testMakeRequired() {
            mfaConfig.setRequired(false);
            mfaConfig.setEnabled(false);

            mfaConfig.makeRequired();

            assertThat(mfaConfig.getRequired()).isTrue();
            assertThat(mfaConfig.getEnabled()).isTrue();
            assertThat(mfaConfig.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("设置为必需会自动启用 MFA")
        void testMakeRequiredAutoEnables() {
            mfaConfig.setEnabled(false);

            mfaConfig.makeRequired();

            assertThat(mfaConfig.getEnabled()).isTrue();
        }
    }

    @Nested
    @DisplayName("makeOptional() 方法测试")
    class MakeOptionalTest {

        @Test
        @DisplayName("取消必需后，required 为 false")
        void testMakeOptional() {
            mfaConfig.setRequired(true);

            mfaConfig.makeOptional();

            assertThat(mfaConfig.getRequired()).isFalse();
            assertThat(mfaConfig.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("取消必需不会自动禁用 MFA")
        void testMakeOptionalDoesNotDisable() {
            mfaConfig.setRequired(true);
            mfaConfig.setEnabled(true);

            mfaConfig.makeOptional();

            assertThat(mfaConfig.getEnabled()).isTrue();
        }
    }

    @Nested
    @DisplayName("isEnabled() 方法测试")
    class IsEnabledTest {

        @Test
        @DisplayName("enabled 为 true 时返回 true")
        void testIsEnabledWhenTrue() {
            mfaConfig.setEnabled(true);

            assertThat(mfaConfig.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("enabled 为 false 时返回 false")
        void testIsEnabledWhenFalse() {
            mfaConfig.setEnabled(false);

            assertThat(mfaConfig.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("enabled 为 null 时返回 false")
        void testIsEnabledWhenNull() {
            mfaConfig.setEnabled(null);

            assertThat(mfaConfig.isEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("isRequired() 方法测试")
    class IsRequiredTest {

        @Test
        @DisplayName("required 为 true 时返回 true")
        void testIsRequiredWhenTrue() {
            mfaConfig.setRequired(true);

            assertThat(mfaConfig.isRequired()).isTrue();
        }

        @Test
        @DisplayName("required 为 false 时返回 false")
        void testIsRequiredWhenFalse() {
            mfaConfig.setRequired(false);

            assertThat(mfaConfig.isRequired()).isFalse();
        }

        @Test
        @DisplayName("required 为 null 时返回 false")
        void testIsRequiredWhenNull() {
            mfaConfig.setRequired(null);

            assertThat(mfaConfig.isRequired()).isFalse();
        }
    }

    @Nested
    @DisplayName("isMfaType(String) 方法测试")
    class IsMfaTypeStringTest {

        @Test
        @DisplayName("MFA 类型匹配时返回 true（大小写不敏感）")
        void testIsMfaTypeMatches() {
            mfaConfig.setMfaType("TOTP");

            assertThat(mfaConfig.isMfaType("TOTP")).isTrue();
            assertThat(mfaConfig.isMfaType("totp")).isTrue();
            assertThat(mfaConfig.isMfaType("Totp")).isTrue();
        }

        @Test
        @DisplayName("MFA 类型不匹配时返回 false")
        void testIsMfaTypeDoesNotMatch() {
            mfaConfig.setMfaType("TOTP");

            assertThat(mfaConfig.isMfaType("SMS")).isFalse();
            assertThat(mfaConfig.isMfaType("EMAIL")).isFalse();
        }

        @Test
        @DisplayName("mfaType 为 null 时返回 false")
        void testIsMfaTypeWhenNull() {
            mfaConfig.setMfaType(null);

            assertThat(mfaConfig.isMfaType("TOTP")).isFalse();
        }

        @Test
        @DisplayName("参数为 null 时返回 false")
        void testIsMfaTypeWithNullParameter() {
            mfaConfig.setMfaType("TOTP");

            assertThat(mfaConfig.isMfaType((String) null)).isFalse();
        }
    }

    @Nested
    @DisplayName("isMfaType(MfaType) 方法测试")
    class IsMfaTypeEnumTest {

        @Test
        @DisplayName("MFA 类型枚举匹配时返回 true")
        void testIsMfaTypeEnumMatches() {
            mfaConfig.setMfaType("TOTP");

            assertThat(mfaConfig.isMfaType(MfaConfigEntity.MfaType.TOTP)).isTrue();
        }

        @Test
        @DisplayName("MFA 类型枚举不匹配时返回 false")
        void testIsMfaTypeEnumDoesNotMatch() {
            mfaConfig.setMfaType("TOTP");

            assertThat(mfaConfig.isMfaType(MfaConfigEntity.MfaType.SMS)).isFalse();
            assertThat(mfaConfig.isMfaType(MfaConfigEntity.MfaType.EMAIL)).isFalse();
            assertThat(mfaConfig.isMfaType(MfaConfigEntity.MfaType.WEBAUTHN)).isFalse();
        }

        @Test
        @DisplayName("mfaType 为 null 时返回 false")
        void testIsMfaTypeEnumWhenNull() {
            mfaConfig.setMfaType(null);

            assertThat(mfaConfig.isMfaType(MfaConfigEntity.MfaType.TOTP)).isFalse();
        }

        @Test
        @DisplayName("大小写不同但内容相同时匹配成功")
        void testIsMfaTypeEnumCaseInsensitive() {
            mfaConfig.setMfaType("totp");

            assertThat(mfaConfig.isMfaType(MfaConfigEntity.MfaType.TOTP)).isTrue();
        }
    }

    @Nested
    @DisplayName("MfaType 枚举测试")
    class MfaTypeEnumTest {

        @Test
        @DisplayName("MfaType 枚举包含所有预期的类型")
        void testMfaTypeEnumValues() {
            MfaConfigEntity.MfaType[] types = MfaConfigEntity.MfaType.values();

            assertThat(types)
                    .containsExactlyInAnyOrder(
                            MfaConfigEntity.MfaType.TOTP,
                            MfaConfigEntity.MfaType.SMS,
                            MfaConfigEntity.MfaType.EMAIL,
                            MfaConfigEntity.MfaType.WEBAUTHN);
        }

        @Test
        @DisplayName("MfaType 枚举名称正确")
        void testMfaTypeEnumNames() {
            assertThat(MfaConfigEntity.MfaType.TOTP.name()).isEqualTo("TOTP");
            assertThat(MfaConfigEntity.MfaType.SMS.name()).isEqualTo("SMS");
            assertThat(MfaConfigEntity.MfaType.EMAIL.name()).isEqualTo("EMAIL");
            assertThat(MfaConfigEntity.MfaType.WEBAUTHN.name()).isEqualTo("WEBAUTHN");
        }
    }
}
