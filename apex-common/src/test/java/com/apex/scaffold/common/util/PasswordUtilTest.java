package com.apex.scaffold.common.util;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * PasswordUtil 单元测试
 *
 * <p>测试密码加密、验证和随机生成功能，覆盖正常输入、边界值和异常情况。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("PasswordUtil 单元测试")
class PasswordUtilTest {

    @Test
    void shouldAcceptOnlyBcryptOrAbsentPasswordAtPersistenceBoundary() {
        String hash = PasswordUtil.encryptPassword("boundary-secret");
        assertThat(PasswordUtil.isBcryptHash(hash)).isTrue();
        assertThatCode(() -> PasswordUtil.requirePasswordHash(hash)).doesNotThrowAnyException();
        assertThatCode(() -> PasswordUtil.requirePasswordHash(null)).doesNotThrowAnyException();
        assertThatCode(() -> PasswordUtil.requirePasswordHash("")).doesNotThrowAnyException();
        for (String invalid : java.util.List.of("plaintext", " ", hash.substring(1), hash.replace("$10$", "$03$"))) {
            assertThat(PasswordUtil.isBcryptHash(invalid)).isFalse();
            assertThatIllegalArgumentException().isThrownBy(() -> PasswordUtil.requirePasswordHash(invalid));
        }
    }

    @Nested
    @DisplayName("encryptPassword 加密密码测试")
    class EncryptPasswordTests {
        @Test
        @DisplayName("正常密码加密后应返回非空且与原密码不同的BCrypt密文")
        void shouldEncryptNormalPassword() {
            String rawPassword = "MySecurePass123!";

            String encrypted = PasswordUtil.encryptPassword(rawPassword);

            assertThat(encrypted).isNotNull();
            assertThat(encrypted).isNotEqualTo(rawPassword);
            assertThat(encrypted).startsWith("$2a$");
        }

        @Test
        @DisplayName("相同密码两次加密结果应不同（BCrypt盐值随机）")
        void shouldProduceDifferentHashesForSamePassword() {
            String rawPassword = "SamePassword";

            String encrypted1 = PasswordUtil.encryptPassword(rawPassword);
            String encrypted2 = PasswordUtil.encryptPassword(rawPassword);

            assertThat(encrypted1).isNotEqualTo(encrypted2);
        }

        @Test
        @DisplayName("空字符串密码加密后应为非空BCrypt密文")
        void shouldEncryptEmptyPassword() {
            String encrypted = PasswordUtil.encryptPassword("");

            assertThat(encrypted).isNotNull();
            assertThat(encrypted).startsWith("$2a$");
        }

        @Test
        @DisplayName("null密码加密应抛出IllegalArgumentException")
        void shouldThrowExceptionForNullPassword() {
            assertThatIllegalArgumentException().isThrownBy(() -> PasswordUtil.encryptPassword(null));
        }
    }

    @Nested
    @DisplayName("verifyPassword 验证密码测试")
    class VerifyPasswordTests {
        @Test
        @DisplayName("正确密码验证应返回true")
        void shouldVerifyCorrectPassword() {
            String rawPassword = "CorrectPass123!";
            String encrypted = PasswordUtil.encryptPassword(rawPassword);

            boolean result = PasswordUtil.verifyPassword(rawPassword, encrypted);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("错误密码验证应返回false")
        void shouldRejectWrongPassword() {
            String rawPassword = "CorrectPass123!";
            String encrypted = PasswordUtil.encryptPassword(rawPassword);

            boolean result = PasswordUtil.verifyPassword("WrongPassword", encrypted);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("null原始密码验证应抛出IllegalArgumentException")
        void shouldThrowExceptionForNullRawPassword() {
            String encrypted = PasswordUtil.encryptPassword("test");

            assertThatIllegalArgumentException().isThrownBy(() -> PasswordUtil.verifyPassword(null, encrypted));
        }

        @Test
        @DisplayName("null加密密码验证应抛出IllegalArgumentException")
        void shouldReturnFalseForNullEncodedPassword() {
            assertThatIllegalArgumentException().isThrownBy(() -> PasswordUtil.verifyPassword("test", null));
        }

        @Test
        @DisplayName("空字符串原始密码验证空字符串加密结果应返回true")
        void shouldVerifyEmptyPasswordAgainstEmptyEncoded() {
            String encrypted = PasswordUtil.encryptPassword("");

            boolean result = PasswordUtil.verifyPassword("", encrypted);

            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("generateRandomPassword 生成随机密码测试")
    class GenerateRandomPasswordTests {
        @Test
        @DisplayName("生成长度为16的随机密码应包含大写、小写、数字和特殊字符")
        void shouldGeneratePasswordWithSpecifiedLength() {
            String password = PasswordUtil.generateRandomPassword(16);

            assertThat(password).isNotNull();
            assertThat(password).hasSize(16);
            assertThat(password).containsPattern("[A-Z]");
            assertThat(password).containsPattern("[a-z]");
            assertThat(password).containsPattern("[0-9]");
        }

        @Test
        @DisplayName("两次生成的随机密码应不相同")
        void shouldGenerateDifferentPasswordsEachTime() {
            String password1 = PasswordUtil.generateRandomPassword(32);
            String password2 = PasswordUtil.generateRandomPassword(32);

            assertThat(password1).isNotEqualTo(password2);
        }

        @Test
        @DisplayName("生成长度为1的随机密码应返回单字符")
        void shouldGenerateSingleCharacterPassword() {
            String password = PasswordUtil.generateRandomPassword(1);

            assertThat(password).isNotNull();
            assertThat(password).hasSize(1);
        }

        @Test
        @DisplayName("生成长度为0的随机密码应返回空字符串")
        void shouldReturnEmptyStringForZeroLength() {
            String password = PasswordUtil.generateRandomPassword(0);

            assertThat(password).isNotNull();
            assertThat(password).isEmpty();
        }

        @Test
        @DisplayName("生成长度为64的长随机密码应正常返回")
        void shouldGenerateLongPassword() {
            String password = PasswordUtil.generateRandomPassword(64);

            assertThat(password).isNotNull();
            assertThat(password).hasSize(64);
        }

        @Test
        @DisplayName("负数长度应返回空字符串")
        void shouldReturnEmptyStringForNegativeLength() {
            String password = PasswordUtil.generateRandomPassword(-1);

            assertThat(password).isNotNull();
            assertThat(password).isEmpty();
        }
    }
}
