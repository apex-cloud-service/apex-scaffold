package com.apex.scaffold.common.security;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ConfigEncryptionService 单元测试
 */
@DisplayName("配置加密服务测试")
class ConfigEncryptionServiceTest {

    private ConfigEncryptionService encryptionService;
    private static final String TEST_KEY = "01234567890123456789012345678901"; // 32字节

    @BeforeEach
    void setUp() {
        encryptionService = new ConfigEncryptionService(TEST_KEY);
    }

    @Test
    @DisplayName("测试配置值加密和解密")
    void testEncryptAndDecrypt() {
        // Given
        String plainValue = "my-secret-password-123";

        // When
        String encrypted = encryptionService.encrypt(plainValue);
        String decrypted = encryptionService.decrypt(encrypted);

        // Then
        assertNotNull(encrypted);
        assertTrue(encrypted.startsWith("ENC("), "加密值应以ENC(开头");
        assertTrue(encrypted.endsWith(")"), "加密值应以)结尾");
        assertEquals(plainValue, decrypted, "解密后应恢复原值");
    }

    @Test
    @DisplayName("测试isEncrypted方法")
    void testIsEncrypted() {
        // Given
        String plainValue = "plain-text";
        String encryptedValue = encryptionService.encrypt(plainValue);

        // Then
        assertFalse(encryptionService.isEncrypted(plainValue), "明文应识别为未加密");
        assertTrue(encryptionService.isEncrypted(encryptedValue), "密文应识别为已加密");
        assertFalse(encryptionService.isEncrypted(null), "null应识别为未加密");
        assertFalse(encryptionService.isEncrypted(""), "空字符串应识别为未加密");
    }

    @Test
    @DisplayName("测试解密明文配置值（幂等性）")
    void testDecryptPlainValue() {
        // Given
        String plainValue = "not-encrypted-value";

        // When
        String result = encryptionService.decrypt(plainValue);

        // Then
        assertEquals(plainValue, result, "解密明文应返回原值");
    }

    @Test
    @DisplayName("测试加密唯一性")
    void testEncryptionUniqueness() {
        // Given
        String value = "test-value";

        // When
        String encrypted1 = encryptionService.encrypt(value);
        String encrypted2 = encryptionService.encrypt(value);

        // Then
        assertNotEquals(encrypted1, encrypted2, "相同值每次加密应产生不同密文");
        assertEquals(value, encryptionService.decrypt(encrypted1));
        assertEquals(value, encryptionService.decrypt(encrypted2));
    }

    @Test
    @DisplayName("测试批量解密")
    void testDecryptBatch() {
        // Given
        String plain1 = "password1";
        String plain2 = "password2";
        String plain3 = "password3";

        String encrypted1 = encryptionService.encrypt(plain1);
        String encrypted2 = encryptionService.encrypt(plain2);

        // When
        String[] decrypted = encryptionService.decryptBatch(
                encrypted1, encrypted2, plain3 // 混合加密和明文
                );

        // Then
        assertEquals(3, decrypted.length);
        assertEquals(plain1, decrypted[0]);
        assertEquals(plain2, decrypted[1]);
        assertEquals(plain3, decrypted[2]);
    }

    @Test
    @DisplayName("测试空值和null处理")
    void testNullAndEmpty() {
        // When & Then
        assertNull(encryptionService.encrypt(null));
        assertEquals("", encryptionService.encrypt(""));

        assertNull(encryptionService.decrypt(null));
        assertEquals("", encryptionService.decrypt(""));

        String[] emptyBatch = encryptionService.decryptBatch((String[]) null);
        assertEquals(0, emptyBatch.length);
    }

    @Test
    @DisplayName("测试长配置值")
    void testLongValue() {
        // Given
        String longValue = "very-long-secret-".repeat(100); // 1700+ 字符

        // When
        String encrypted = encryptionService.encrypt(longValue);
        String decrypted = encryptionService.decrypt(encrypted);

        // Then
        assertEquals(longValue, decrypted);
    }

    @Test
    @DisplayName("测试特殊字符")
    void testSpecialCharacters() {
        // Given
        String specialValue = "password!@#$%^&*()_+-=[]{}|;':\",./<>?中文字符";

        // When
        String encrypted = encryptionService.encrypt(specialValue);
        String decrypted = encryptionService.decrypt(encrypted);

        // Then
        assertEquals(specialValue, decrypted);
    }

    @Test
    @DisplayName("测试解密无效密文")
    void testDecryptInvalidCiphertext() {
        // Given
        String invalidEncrypted = "ENC(invalid-base64-data!@#$)";

        // When & Then
        assertThrows(RuntimeException.class, () -> encryptionService.decrypt(invalidEncrypted));
    }

    @Test
    @DisplayName("测试数据库密码加密场景")
    void testDatabasePasswordScenario() {
        // Given - 模拟application.yml中的数据库密码
        String dbPassword = "MyS3cureP@ssw0rd!";

        // When - 加密密码
        String encryptedPassword = encryptionService.encrypt(dbPassword);
        System.out.println("加密后的数据库密码: " + encryptedPassword);

        // Then - 应用启动时解密使用
        String decryptedPassword = encryptionService.decrypt(encryptedPassword);
        assertEquals(dbPassword, decryptedPassword);

        // 验证可以正常使用（模拟数据库连接）
        assertTrue(decryptedPassword.length() > 0);
    }

    @Test
    @DisplayName("测试API密钥加密场景")
    void testApiKeyScenario() {
        // Given
        String apiKey = "sk_live_51H7bKcL2KGCbGwu8YWQK9fXxHzVj3mN";

        // When
        String encrypted = encryptionService.encrypt(apiKey);

        // Then
        assertNotEquals(apiKey, encrypted);
        assertTrue(encryptionService.isEncrypted(encrypted));
        assertEquals(apiKey, encryptionService.decrypt(encrypted));
    }

    @Test
    @DisplayName("测试JWT密钥加密场景")
    void testJwtSecretScenario() {
        // Given
        String jwtSecret = "super-secret-jwt-signing-key-must-be-kept-secure";

        // When
        String encrypted = encryptionService.encrypt(jwtSecret);
        System.out.println("JWT密钥(加密): " + encrypted);

        // Then
        String decrypted = encryptionService.decrypt(encrypted);
        assertEquals(jwtSecret, decrypted);
    }

    @Test
    @DisplayName("测试加密性能")
    void testPerformance() {
        // Given
        String value = "performance-test-config-value";
        int iterations = 1000;

        // When
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            String encrypted = encryptionService.encrypt(value);
            encryptionService.decrypt(encrypted);
        }
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Then
        System.out.printf("配置加密解密%d次耗时: %d ms (平均: %.2f ms/次)%n", iterations, duration, (double) duration / iterations);

        assertTrue(duration < 5000, String.format("1000次加密解密应在5秒内完成，实际: %d ms", duration));
    }

    @Test
    @DisplayName("测试错误的密钥长度")
    void testInvalidKeyLength() {
        assertThrows(IllegalArgumentException.class, () -> new ConfigEncryptionService("short"));

        assertThrows(
                IllegalArgumentException.class,
                () -> new ConfigEncryptionService("this-key-is-too-long-more-than-32-bytes"));
    }
}
