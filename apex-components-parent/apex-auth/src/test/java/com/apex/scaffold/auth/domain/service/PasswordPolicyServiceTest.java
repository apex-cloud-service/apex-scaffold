package com.apex.scaffold.auth.domain.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveListOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("密码策略服务测试")
class PasswordPolicyServiceTest {

    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ReactiveListOperations<String, String> listOperations;

    private PasswordPolicyService service;

    @BeforeEach
    void setUp() {
        service = new PasswordPolicyService(redisTemplate, passwordEncoder);
    }

    @Test
    @DisplayName("密码验证 - 强密码通过（普通用户）")
    void testValidatePassword_StrongPassword_NormalUser() {
        String password = "MyP@ssw0rd2024!";
        String username = "john";

        StepVerifier.create(service.validatePassword(password, username, false))
                .assertNext(result -> {
                    assertTrue(result.isPassed());
                    assertTrue(result.getScore() >= 60);
                    assertTrue(result.getErrors().isEmpty());
                    assertNotEquals("弱", result.getStrengthLevel());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("密码验证 - 极强密码通过（管理员）")
    void testValidatePassword_VeryStrongPassword_Admin() {
        String password = "Secur3!P@ssw0rd#2024";
        String username = "admin";

        StepVerifier.create(service.validatePassword(password, username, true))
                .assertNext(result -> {
                    assertTrue(result.isPassed());
                    assertTrue(result.getScore() >= 80);
                    assertTrue(result.getErrors().isEmpty());
                    assertTrue(result.getScore() >= 71);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("密码验证 - 密码过短（普通用户）")
    void testValidatePassword_TooShort_NormalUser() {
        String password = "Short1!";
        String username = "john";

        StepVerifier.create(service.validatePassword(password, username, false))
                .assertNext(result -> {
                    assertFalse(result.isPassed());
                    assertEquals(0, result.getScore());
                    assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("密码长度不能少于12位")));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("密码验证 - 密码过短（管理员）")
    void testValidatePassword_TooShort_Admin() {
        String password = "Admin@Pass1!";
        String username = "admin";

        StepVerifier.create(service.validatePassword(password, username, true))
                .assertNext(result -> {
                    assertFalse(result.isPassed());
                    assertEquals(0, result.getScore());
                    assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("密码长度不能少于16位")));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("密码验证 - 字符类型不足")
    void testValidatePassword_InsufficientCharTypes() {
        String password = "onlylowercase123";
        String username = "john";

        StepVerifier.create(service.validatePassword(password, username, false))
                .assertNext(result -> {
                    assertFalse(result.isPassed());
                    assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("至少3种字符类型")));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("密码验证 - 管理员密码必须包含所有字符类型")
    void testValidatePassword_AdminMustHaveAllTypes() {
        String password = "AdminPassword123";
        String username = "admin";

        StepVerifier.create(service.validatePassword(password, username, true))
                .assertNext(result -> {
                    assertFalse(result.isPassed());
                    assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("管理员密码必须包含所有4种字符类型")));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("密码验证 - 包含用户名")
    void testValidatePassword_ContainsUsername() {
        String password = "John@Password123!";
        String username = "john";

        StepVerifier.create(service.validatePassword(password, username, false))
                .assertNext(result -> {
                    assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("密码不能包含用户名")));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("密码验证 - 包含常见弱密码")
    void testValidatePassword_ContainsWeakPassword() {
        String password = "MyPassword123!";
        String username = "john";

        StepVerifier.create(service.validatePassword(password, username, false))
                .assertNext(result -> {
                    assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("常见弱密码词汇")));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("密码验证 - 包含连续字符")
    void testValidatePassword_SequentialChars() {
        String password = "Pass123word!@#";
        String username = "john";

        StepVerifier.create(service.validatePassword(password, username, false))
                .assertNext(result -> {
                    assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("连续的字符序列")));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("密码验证 - 包含重复字符")
    void testValidatePassword_RepeatedChars() {
        String password = "Pass@word111!!!";
        String username = "john";

        StepVerifier.create(service.validatePassword(password, username, false))
                .assertNext(result -> {
                    assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("3个及以上连续重复的字符")));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("密码验证 - null密码")
    void testValidatePassword_NullPassword() {
        StepVerifier.create(service.validatePassword(null, "john", false))
                .assertNext(result -> {
                    assertFalse(result.isPassed());
                    assertEquals(0, result.getScore());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("检查密码历史 - 密码未使用过")
    void testIsPasswordInHistory_NotUsed() {
        String userId = "user-123";
        String newPassword = "NewP@ssw0rd123!";

        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.range("password:history:" + userId, 0, -1))
                .thenReturn(Flux.just("hash1", "hash2", "hash3"));
        when(passwordEncoder.matches(eq(newPassword), anyString())).thenReturn(false);

        StepVerifier.create(service.isPasswordInHistory(userId, newPassword))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("检查密码历史 - 密码已使用过")
    void testIsPasswordInHistory_AlreadyUsed() {
        String userId = "user-123";
        String newPassword = "OldP@ssw0rd123!";
        String matchingHash = "hash2";

        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.range("password:history:" + userId, 0, -1))
                .thenReturn(Flux.just("hash1", matchingHash, "hash3"));
        when(passwordEncoder.matches(newPassword, "hash1")).thenReturn(false);
        when(passwordEncoder.matches(newPassword, matchingHash)).thenReturn(true);

        StepVerifier.create(service.isPasswordInHistory(userId, newPassword))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("检查密码历史 - 历史为空")
    void testIsPasswordInHistory_EmptyHistory() {
        String userId = "user-123";
        String newPassword = "NewP@ssw0rd123!";

        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.range("password:history:" + userId, 0, -1)).thenReturn(Flux.empty());

        StepVerifier.create(service.isPasswordInHistory(userId, newPassword))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void shouldRejectPlaintextHistory() {
        StepVerifier.create(service.addPasswordToHistory("user-123", "PlaintextP@ssw0rd"))
                .expectError(IllegalArgumentException.class)
                .verify();
        org.mockito.Mockito.verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("添加密码到历史")
    void testAddPasswordToHistory() {
        String userId = "user-123";
        String passwordHash = com.apex.scaffold.common.util.PasswordUtil.encryptPassword("HistoryP@ssw0rd");
        String key = "password:history:" + userId;

        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.leftPush(key, passwordHash)).thenReturn(Mono.just(1L));
        when(listOperations.trim(key, 0, 4)).thenReturn(Mono.just(true));

        StepVerifier.create(service.addPasswordToHistory(userId, passwordHash)).verifyComplete();

        verify(listOperations).leftPush(key, passwordHash);
        verify(listOperations).trim(key, 0, 4);
    }

    @Test
    @DisplayName("检查密码过期 - 未过期（普通用户）")
    void testIsPasswordExpired_NotExpired_NormalUser() {
        LocalDateTime lastChange = LocalDateTime.now().minusDays(30);

        boolean expired = service.isPasswordExpired(lastChange, false);

        assertFalse(expired);
    }

    @Test
    @DisplayName("检查密码过期 - 已过期（普通用户）")
    void testIsPasswordExpired_Expired_NormalUser() {
        LocalDateTime lastChange = LocalDateTime.now().minusDays(100);

        boolean expired = service.isPasswordExpired(lastChange, false);

        assertTrue(expired);
    }

    @Test
    @DisplayName("检查密码过期 - 未过期（管理员）")
    void testIsPasswordExpired_NotExpired_Admin() {
        LocalDateTime lastChange = LocalDateTime.now().minusDays(30);

        boolean expired = service.isPasswordExpired(lastChange, true);

        assertFalse(expired);
    }

    @Test
    @DisplayName("检查密码过期 - 已过期（管理员）")
    void testIsPasswordExpired_Expired_Admin() {
        LocalDateTime lastChange = LocalDateTime.now().minusDays(70);

        boolean expired = service.isPasswordExpired(lastChange, true);

        assertTrue(expired);
    }

    @Test
    @DisplayName("检查密码过期 - 从未修改过")
    void testIsPasswordExpired_NeverChanged() {
        boolean expired = service.isPasswordExpired(null, false);

        assertTrue(expired);
    }

    @Test
    @DisplayName("获取密码剩余有效天数 - 普通用户")
    void testGetDaysUntilExpiry_NormalUser() {
        LocalDateTime lastChange = LocalDateTime.now().minusDays(30);

        long days = service.getDaysUntilExpiry(lastChange, false);

        assertTrue(days >= 59 && days <= 61);
    }

    @Test
    @DisplayName("获取密码剩余有效天数 - 管理员")
    void testGetDaysUntilExpiry_Admin() {
        LocalDateTime lastChange = LocalDateTime.now().minusDays(30);

        long days = service.getDaysUntilExpiry(lastChange, true);

        assertTrue(days >= 29 && days <= 31);
    }

    @Test
    @DisplayName("获取密码剩余有效天数 - 已过期")
    void testGetDaysUntilExpiry_AlreadyExpired() {
        LocalDateTime lastChange = LocalDateTime.now().minusDays(100);

        long days = service.getDaysUntilExpiry(lastChange, false);

        assertTrue(days < 0);
    }

    @Test
    @DisplayName("获取密码剩余有效天数 - 从未修改过")
    void testGetDaysUntilExpiry_NeverChanged() {
        long days = service.getDaysUntilExpiry(null, false);

        assertEquals(0, days);
    }

    @Test
    @DisplayName("生成安全密码 - 包含特殊字符")
    void testGenerateSecurePassword_WithSpecialChars() {
        String password = service.generateSecurePassword(16, true);

        assertNotNull(password);
        assertEquals(16, password.length());
        assertTrue(password.matches(".*[a-z].*"));
        assertTrue(password.matches(".*[A-Z].*"));
        assertTrue(password.matches(".*\\d.*"));
        assertTrue(password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{}|;:,.<>?].*"));
    }

    @Test
    @DisplayName("生成安全密码 - 不包含特殊字符")
    void testGenerateSecurePassword_WithoutSpecialChars() {
        String password = service.generateSecurePassword(16, false);

        assertNotNull(password);
        assertEquals(16, password.length());
        assertTrue(password.matches(".*[a-z].*"));
        assertTrue(password.matches(".*[A-Z].*"));
        assertTrue(password.matches(".*\\d.*"));
        assertFalse(password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{}|;:,.<>?].*"));
    }

    @Test
    @DisplayName("生成安全密码 - 最小长度")
    void testGenerateSecurePassword_MinLength() {
        String password = service.generateSecurePassword(12, false);

        assertNotNull(password);
        assertEquals(12, password.length());
    }

    @Test
    @DisplayName("生成安全密码 - 较长密码")
    void testGenerateSecurePassword_LongPassword() {
        String password = service.generateSecurePassword(32, true);

        assertNotNull(password);
        assertEquals(32, password.length());
    }

    @Test
    @DisplayName("生成安全密码 - 每次生成不同")
    void testGenerateSecurePassword_GeneratesDifferent() {
        String password1 = service.generateSecurePassword(16, true);
        String password2 = service.generateSecurePassword(16, true);

        assertNotEquals(password1, password2);
    }

    @Test
    @DisplayName("密码强度等级 - 极强")
    void testPasswordStrengthLevel_VeryStrong() {
        PasswordPolicyService.PasswordValidationResult result =
                new PasswordPolicyService.PasswordValidationResult(true, 95, List.of());

        assertEquals("极强", result.getStrengthLevel());
    }

    @Test
    @DisplayName("密码强度等级 - 强")
    void testPasswordStrengthLevel_Strong() {
        PasswordPolicyService.PasswordValidationResult result =
                new PasswordPolicyService.PasswordValidationResult(true, 80, List.of());

        assertEquals("强", result.getStrengthLevel());
    }

    @Test
    @DisplayName("密码强度等级 - 中等")
    void testPasswordStrengthLevel_Medium() {
        PasswordPolicyService.PasswordValidationResult result =
                new PasswordPolicyService.PasswordValidationResult(true, 60, List.of());

        assertEquals("中等", result.getStrengthLevel());
    }

    @Test
    @DisplayName("密码强度等级 - 弱")
    void testPasswordStrengthLevel_Weak() {
        PasswordPolicyService.PasswordValidationResult result =
                new PasswordPolicyService.PasswordValidationResult(false, 30, List.of("错误"));

        assertEquals("弱", result.getStrengthLevel());
    }
}
