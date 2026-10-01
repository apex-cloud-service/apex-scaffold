package com.apex.scaffold.authcenter.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

@DisplayName("密码策略服务测试")
class PasswordPolicyServiceTest {

    private PasswordPolicyService passwordPolicyService;

    @BeforeEach
    void setUp() {
        passwordPolicyService = new PasswordPolicyService();
    }

    @Test
    @DisplayName("符合策略的强密码应验证通过")
    void testValidStrongPassword() {
        String validPassword = "StrongP@ssw0rd123";
        String username = "testuser";

        StepVerifier.create(passwordPolicyService.validatePassword(validPassword, username))
                .verifyComplete();
    }

    @Test
    @DisplayName("空密码应验证失败")
    void testNullPassword() {
        StepVerifier.create(passwordPolicyService.validatePassword(null, "testuser"))
                .expectError(BusinessException.class)
                .verify();
    }

    @Test
    @DisplayName("空字符串密码应验证失败")
    void testEmptyPassword() {
        StepVerifier.create(passwordPolicyService.validatePassword("", "testuser"))
                .expectError(BusinessException.class)
                .verify();
    }

    @Test
    @DisplayName("密码长度少于12位应验证失败")
    void testPasswordTooShort() {
        String shortPassword = "Short@1Aa";

        StepVerifier.create(passwordPolicyService.validatePassword(shortPassword, "testuser"))
                .expectError(BusinessException.class)
                .verify();
    }

    @Test
    @DisplayName("密码长度超过64位应验证失败")
    void testPasswordTooLong() {
        String longPassword = "A".repeat(65) + "a1@";

        StepVerifier.create(passwordPolicyService.validatePassword(longPassword, "testuser"))
                .expectError(BusinessException.class)
                .verify();
    }

    @Test
    @DisplayName("密码缺少大写字母应验证失败")
    void testPasswordWithoutUppercase() {
        String password = "password@123456";

        StepVerifier.create(passwordPolicyService.validatePassword(password, "testuser"))
                .expectError(BusinessException.class)
                .verify();
    }

    @Test
    @DisplayName("密码缺少小写字母应验证失败")
    void testPasswordWithoutLowercase() {
        String password = "PASSWORD@123456";

        StepVerifier.create(passwordPolicyService.validatePassword(password, "testuser"))
                .expectError(BusinessException.class)
                .verify();
    }

    @Test
    @DisplayName("密码缺少数字应验证失败")
    void testPasswordWithoutDigit() {
        String password = "Password@NoDigit";

        StepVerifier.create(passwordPolicyService.validatePassword(password, "testuser"))
                .expectError(BusinessException.class)
                .verify();
    }

    @Test
    @DisplayName("密码缺少特殊字符应验证失败")
    void testPasswordWithoutSpecialChar() {
        String password = "Password1234567";

        StepVerifier.create(passwordPolicyService.validatePassword(password, "testuser"))
                .expectError(BusinessException.class)
                .verify();
    }

    @Test
    @DisplayName("密码包含用户名应验证失败")
    void testPasswordContainsUsername() {
        String password = "MyUsername@123456";
        String username = "username";

        StepVerifier.create(passwordPolicyService.validatePassword(password, username))
                .expectError(BusinessException.class)
                .verify();
    }

    @Test
    @DisplayName("密码包含用户名（大小写不敏感）应验证失败")
    void testPasswordContainsUsernameCaseInsensitive() {
        String password = "MyTESTUSER@123456";
        String username = "testuser";

        StepVerifier.create(passwordPolicyService.validatePassword(password, username))
                .expectError(BusinessException.class)
                .verify();
    }

    @Test
    @DisplayName("密码为常见弱密码应验证失败")
    void testWeakPassword() {
        String weakPassword = "Password123456!";

        StepVerifier.create(passwordPolicyService.validatePassword(weakPassword, "testuser"))
                .expectError(BusinessException.class)
                .verify();
    }

    @Test
    @DisplayName("密码包含3个连续相同字符应验证失败")
    void testPasswordWithConsecutiveChars() {
        String password = "Paaaassword@123";

        StepVerifier.create(passwordPolicyService.validatePassword(password, "testuser"))
                .expectError(BusinessException.class)
                .verify();
    }

    @Test
    @DisplayName("密码包含4个连续递增字符应验证失败")
    void testPasswordWithSequentialChars() {
        String password = "Password1234!Abc";

        StepVerifier.create(passwordPolicyService.validatePassword(password, "testuser"))
                .expectError(BusinessException.class)
                .verify();
    }

    @Test
    @DisplayName("密码包含4个连续递减字符应验证失败")
    void testPasswordWithReverseSequentialChars() {
        String password = "Passdcba@123456";

        StepVerifier.create(passwordPolicyService.validatePassword(password, "testuser"))
                .expectError(BusinessException.class)
                .verify();
    }

    @Test
    @DisplayName("验证新密码与旧密码不同 - 不同应通过")
    void testPasswordNotReused() {
        String newPassword = "NewP@ssw0rd123456";
        String oldPasswordHash = "old_hash_value";

        StepVerifier.create(passwordPolicyService.validatePasswordNotReused(
                        newPassword, oldPasswordHash, pwd -> "new_hash_value"))
                .verifyComplete();
    }

    @Test
    @DisplayName("验证新密码与旧密码不同 - 相同应失败")
    void testPasswordReusedShouldFail() {
        String newPassword = "SameP@ssw0rd123";
        String oldPasswordHash = "same_hash";

        StepVerifier.create(passwordPolicyService.validatePasswordNotReused(
                        newPassword, oldPasswordHash, pwd -> "same_hash"))
                .expectError(BusinessException.class)
                .verify();
    }

    @Test
    @DisplayName("旧密码为空时应允许设置新密码")
    void testPasswordNotReusedWithNullOldPassword() {
        String newPassword = "NewP@ssw0rd123456";

        StepVerifier.create(passwordPolicyService.validatePasswordNotReused(newPassword, null, pwd -> "new_hash"))
                .verifyComplete();
    }

    @Test
    @DisplayName("检查弱密码强度")
    void testCheckPasswordStrengthWeak() {
        String weakPassword = "weak";

        StepVerifier.create(passwordPolicyService.checkPasswordStrength(weakPassword))
                .assertNext(strength -> assertThat(strength).isEqualTo(PasswordPolicyService.PasswordStrength.WEAK))
                .verifyComplete();
    }

    @Test
    @DisplayName("检查中等密码强度")
    void testCheckPasswordStrengthMedium() {
        String mediumPassword = "Pass1234word";

        StepVerifier.create(passwordPolicyService.checkPasswordStrength(mediumPassword))
                .assertNext(strength -> assertThat(strength)
                        .isIn(
                                PasswordPolicyService.PasswordStrength.WEAK,
                                PasswordPolicyService.PasswordStrength.MEDIUM))
                .verifyComplete();
    }

    @Test
    @DisplayName("检查强密码强度")
    void testCheckPasswordStrengthStrong() {
        String strongPassword = "StrongPass@123";

        StepVerifier.create(passwordPolicyService.checkPasswordStrength(strongPassword))
                .assertNext(strength -> {
                    assertThat(strength)
                            .isIn(
                                    PasswordPolicyService.PasswordStrength.STRONG,
                                    PasswordPolicyService.PasswordStrength.VERY_STRONG);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("检查极强密码强度")
    void testCheckPasswordStrengthVeryStrong() {
        String veryStrongPassword = "V3ry$tr0ng!P@ssw0rd#2024";

        StepVerifier.create(passwordPolicyService.checkPasswordStrength(veryStrongPassword))
                .assertNext(strength -> assertThat(strength)
                        .isIn(
                                PasswordPolicyService.PasswordStrength.STRONG,
                                PasswordPolicyService.PasswordStrength.VERY_STRONG))
                .verifyComplete();
    }

    @Test
    @DisplayName("空密码强度应为弱")
    void testCheckPasswordStrengthNull() {
        StepVerifier.create(passwordPolicyService.checkPasswordStrength(null))
                .assertNext(strength -> assertThat(strength).isEqualTo(PasswordPolicyService.PasswordStrength.WEAK))
                .verifyComplete();
    }

    @Test
    @DisplayName("获取密码策略描述")
    void testGetPasswordPolicyDescription() {
        String description = passwordPolicyService.getPasswordPolicyDescription();

        assertThat(description).isNotNull();
        assertThat(description).contains("12-64位");
        assertThat(description).contains("大写字母");
        assertThat(description).contains("小写字母");
        assertThat(description).contains("数字");
        assertThat(description).contains("特殊字符");
    }

    @Test
    @DisplayName("PasswordStrength枚举应包含所有强度级别")
    void testPasswordStrengthEnum() {
        assertThat(PasswordPolicyService.PasswordStrength.WEAK.getDescription()).isEqualTo("弱");
        assertThat(PasswordPolicyService.PasswordStrength.MEDIUM.getDescription())
                .isEqualTo("中等");
        assertThat(PasswordPolicyService.PasswordStrength.STRONG.getDescription())
                .isEqualTo("强");
        assertThat(PasswordPolicyService.PasswordStrength.VERY_STRONG.getDescription())
                .isEqualTo("极强");
    }

    @Test
    @DisplayName("边界长度密码 - 12位应通过")
    void testMinimumLengthPassword() {
        String password = "ValidP@ss123";

        StepVerifier.create(passwordPolicyService.validatePassword(password, "testuser"))
                .verifyComplete();
    }

    @Test
    @DisplayName("边界长度密码 - 64位应通过")
    void testMaximumLengthPassword() {
        String password = "Aa1@Bb2#Cc3$Dd4%Ee5^Ff6&Gg7*Hh8(Ii9)Jj0!Kk1@Ll2#Mm3$Nn4%Oo5^Pp6";

        StepVerifier.create(passwordPolicyService.validatePassword(password, "testuser"))
                .verifyComplete();
    }

    @Test
    @DisplayName("所有特殊字符应被接受")
    void testAllSpecialCharacters() {
        String[] specialChars = {
            "!", "@", "#", "$", "%", "^", "&", "*", "(", ")", "_", "+", "-", "=", "[", "]", "{", "}", ";", "'", ":",
            "\"", "\\", "|", ",", ".", "<", ">", "/", "?"
        };

        for (String specialChar : specialChars) {
            String password = "ValidP@ss123" + specialChar;
            StepVerifier.create(passwordPolicyService.validatePassword(password, "testuser"))
                    .verifyComplete();
        }
    }

    @Test
    @DisplayName("username为null时不应影响密码验证")
    void testValidationWithNullUsername() {
        String validPassword = "StrongP@ssw0rd123";

        StepVerifier.create(passwordPolicyService.validatePassword(validPassword, null))
                .verifyComplete();
    }

    @Test
    @DisplayName("username为空字符串时不应影响密码验证")
    void testValidationWithEmptyUsername() {
        String validPassword = "StrongP@ssw0rd123";

        StepVerifier.create(passwordPolicyService.validatePassword(validPassword, ""))
                .verifyComplete();
    }

    @Test
    @DisplayName("包含常见弱密码片段应失败")
    void testPasswordContainsWeakPasswordFragment() {
        String[] weakPasswords = {"123456", "qwerty", "letmein", "monkey"};

        for (String weak : weakPasswords) {
            String password = "MyP@ss" + weak + "Secure";
            StepVerifier.create(passwordPolicyService.validatePassword(password, "testuser"))
                    .expectError(BusinessException.class)
                    .verify();
        }
    }

    @Test
    @DisplayName("2个连续相同字符应允许")
    void testTwoConsecutiveCharsAllowed() {
        String password = "Good!P@ssw0rd12";

        StepVerifier.create(passwordPolicyService.validatePassword(password, "testuser"))
                .verifyComplete();
    }

    @Test
    @DisplayName("3个连续递增字符应允许")
    void testThreeSequentialCharsAllowed() {
        String password = "Passwor12d@ABXC";

        StepVerifier.create(passwordPolicyService.validatePassword(password, "testuser"))
                .verifyComplete();
    }
}
