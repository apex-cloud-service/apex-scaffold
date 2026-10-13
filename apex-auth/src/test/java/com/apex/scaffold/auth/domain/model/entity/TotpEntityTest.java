package com.apex.scaffold.auth.domain.model.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * TotpEntity 单元测试
 *
 * @author Apex Scaffold Team
 * @since 2026-09-21
 */
@DisplayName("TotpEntity 单元测试")
class TotpEntityTest {

    private TotpEntity totpEntity;

    @BeforeEach
    void setUp() {
        totpEntity = TotpEntity.builder()
                .id(1L)
                .userId(100L)
                .tenantId("test-tenant")
                .secret("JBSWY3DPEHPK3PXP")
                .verified(false)
                .enabled(false)
                .backupCodes("12345678,23456789,34567890,45678901,56789012,67890123,78901234,89012345")
                .usedBackupCodes(null)
                .version(1)
                .deleted(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("verify() 方法测试")
    class VerifyTest {

        @Test
        @DisplayName("验证码为 null 时返回 false")
        void testVerifyWithNullCode() {
            boolean result = totpEntity.verify(null);
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("验证码长度不是 6 位时返回 false")
        void testVerifyWithInvalidLength() {
            assertThat(totpEntity.verify("12345")).isFalse();
            assertThat(totpEntity.verify("1234567")).isFalse();
            assertThat(totpEntity.verify("")).isFalse();
        }

        @Test
        @DisplayName("验证码长度为 6 位时返回 true（简化逻辑）")
        void testVerifyWithValidLength() {
            // 注意：实际验证逻辑在 TotpService 中实现，这里只检查格式
            boolean result = totpEntity.verify("123456");
            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("markAsVerified() 方法测试")
    class MarkAsVerifiedTest {

        @Test
        @DisplayName("标记为已验证后，verified 为 true")
        void testMarkAsVerified() {
            assertThat(totpEntity.getVerified()).isFalse();

            totpEntity.markAsVerified();

            assertThat(totpEntity.getVerified()).isTrue();
            assertThat(totpEntity.getLastVerifiedAt()).isNotNull();
            assertThat(totpEntity.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("标记为已验证后，lastVerifiedAt 被更新")
        void testMarkAsVerifiedUpdatesLastVerifiedAt() {
            LocalDateTime beforeMark = LocalDateTime.now().minusSeconds(1);

            totpEntity.markAsVerified();

            assertThat(totpEntity.getLastVerifiedAt()).isAfter(beforeMark);
        }
    }

    @Nested
    @DisplayName("enable() 方法测试")
    class EnableTest {

        @Test
        @DisplayName("未验证时启用抛出 IllegalStateException")
        void testEnableWithoutVerification() {
            totpEntity.setVerified(false);

            assertThatThrownBy(() -> totpEntity.enable())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("TOTP must be verified before enabling");
        }

        @Test
        @DisplayName("已验证时可以成功启用")
        void testEnableAfterVerification() {
            totpEntity.setVerified(true);

            totpEntity.enable();

            assertThat(totpEntity.getEnabled()).isTrue();
            assertThat(totpEntity.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("verified 为 null 时启用抛出异常")
        void testEnableWithNullVerified() {
            totpEntity.setVerified(null);

            assertThatThrownBy(() -> totpEntity.enable()).isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("disable() 方法测试")
    class DisableTest {

        @Test
        @DisplayName("禁用 TOTP 后 enabled 为 false")
        void testDisable() {
            totpEntity.setEnabled(true);

            totpEntity.disable();

            assertThat(totpEntity.getEnabled()).isFalse();
            assertThat(totpEntity.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("无论之前是否启用，禁用后都为 false")
        void testDisableRegardlessOfPreviousState() {
            totpEntity.setEnabled(false);
            totpEntity.disable();
            assertThat(totpEntity.getEnabled()).isFalse();

            totpEntity.setEnabled(true);
            totpEntity.disable();
            assertThat(totpEntity.getEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("useBackupCode() 方法测试")
    class UseBackupCodeTest {

        @Test
        @DisplayName("备份码为 null 时返回 false")
        void testUseNullBackupCode() {
            boolean result = totpEntity.useBackupCode(null);
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("backupCodes 为 null 时返回 false")
        void testUseBackupCodeWhenBackupCodesIsNull() {
            totpEntity.setBackupCodes(null);

            boolean result = totpEntity.useBackupCode("12345678");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("备份码不在列表中时返回 false")
        void testUseInvalidBackupCode() {
            boolean result = totpEntity.useBackupCode("99999999");

            assertThat(result).isFalse();
            assertThat(totpEntity.getUsedBackupCodes()).isNull();
        }

        @Test
        @DisplayName("使用有效备份码成功")
        void testUseValidBackupCode() {
            boolean result = totpEntity.useBackupCode("12345678");

            assertThat(result).isTrue();
            assertThat(totpEntity.getUsedBackupCodes()).isEqualTo("12345678");
            assertThat(totpEntity.getLastVerifiedAt()).isNotNull();
            assertThat(totpEntity.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("重复使用同一备份码返回 false")
        void testUseBackupCodeTwice() {
            // 第一次使用成功
            boolean firstUse = totpEntity.useBackupCode("12345678");
            assertThat(firstUse).isTrue();

            // 第二次使用同一个码失败
            boolean secondUse = totpEntity.useBackupCode("12345678");
            assertThat(secondUse).isFalse();
        }

        @Test
        @DisplayName("使用多个不同的备份码")
        void testUseMultipleBackupCodes() {
            totpEntity.useBackupCode("12345678");
            assertThat(totpEntity.getUsedBackupCodes()).isEqualTo("12345678");

            totpEntity.useBackupCode("23456789");
            assertThat(totpEntity.getUsedBackupCodes()).isEqualTo("12345678,23456789");

            totpEntity.useBackupCode("34567890");
            assertThat(totpEntity.getUsedBackupCodes()).isEqualTo("12345678,23456789,34567890");
        }

        @Test
        @DisplayName("已使用的备份码不能再次使用")
        void testCannotReuseUsedBackupCode() {
            totpEntity.setUsedBackupCodes("12345678,23456789");

            boolean result1 = totpEntity.useBackupCode("12345678");
            assertThat(result1).isFalse();

            boolean result2 = totpEntity.useBackupCode("23456789");
            assertThat(result2).isFalse();

            // 未使用的备份码可以使用
            boolean result3 = totpEntity.useBackupCode("34567890");
            assertThat(result3).isTrue();
        }
    }

    @Nested
    @DisplayName("isEnabled() 方法测试")
    class IsEnabledTest {

        @Test
        @DisplayName("enabled 和 verified 都为 true 时返回 true")
        void testIsEnabledWhenBothTrue() {
            totpEntity.setEnabled(true);
            totpEntity.setVerified(true);

            assertThat(totpEntity.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("enabled 为 true 但 verified 为 false 时返回 false")
        void testIsEnabledWhenEnabledButNotVerified() {
            totpEntity.setEnabled(true);
            totpEntity.setVerified(false);

            assertThat(totpEntity.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("verified 为 true 但 enabled 为 false 时返回 false")
        void testIsEnabledWhenVerifiedButNotEnabled() {
            totpEntity.setEnabled(false);
            totpEntity.setVerified(true);

            assertThat(totpEntity.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("enabled 和 verified 都为 false 时返回 false")
        void testIsEnabledWhenBothFalse() {
            totpEntity.setEnabled(false);
            totpEntity.setVerified(false);

            assertThat(totpEntity.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("enabled 为 null 时返回 false")
        void testIsEnabledWhenEnabledIsNull() {
            totpEntity.setEnabled(null);
            totpEntity.setVerified(true);

            assertThat(totpEntity.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("verified 为 null 时返回 false")
        void testIsEnabledWhenVerifiedIsNull() {
            totpEntity.setEnabled(true);
            totpEntity.setVerified(null);

            assertThat(totpEntity.isEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("getRemainingBackupCodes() 方法测试")
    class GetRemainingBackupCodesTest {

        @Test
        @DisplayName("backupCodes 为 null 时返回 0")
        void testGetRemainingBackupCodesWhenNull() {
            totpEntity.setBackupCodes(null);

            int remaining = totpEntity.getRemainingBackupCodes();

            assertThat(remaining).isZero();
        }

        @Test
        @DisplayName("未使用任何备份码时返回总数")
        void testGetRemainingBackupCodesWhenNoneUsed() {
            totpEntity.setUsedBackupCodes(null);

            int remaining = totpEntity.getRemainingBackupCodes();

            assertThat(remaining).isEqualTo(8);
        }

        @Test
        @DisplayName("使用部分备份码后返回剩余数量")
        void testGetRemainingBackupCodesWhenSomeUsed() {
            totpEntity.setUsedBackupCodes("12345678,23456789");

            int remaining = totpEntity.getRemainingBackupCodes();

            assertThat(remaining).isEqualTo(6);
        }

        @Test
        @DisplayName("使用所有备份码后返回 0")
        void testGetRemainingBackupCodesWhenAllUsed() {
            totpEntity.setUsedBackupCodes("12345678,23456789,34567890,45678901,56789012,67890123,78901234,89012345");

            int remaining = totpEntity.getRemainingBackupCodes();

            assertThat(remaining).isZero();
        }
    }
}
