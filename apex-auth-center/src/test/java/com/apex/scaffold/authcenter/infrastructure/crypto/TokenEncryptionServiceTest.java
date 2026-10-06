package com.apex.scaffold.authcenter.infrastructure.crypto;

import static org.junit.jupiter.api.Assertions.*;

import com.apex.scaffold.authcenter.domain.service.TokenEncryptionService;
import com.apex.scaffold.authcenter.domain.service.TokenEncryptionService.EncryptedToken;
import com.apex.scaffold.authcenter.infrastructure.encryption.TokenEncryptionServiceImpl;
import com.apex.scaffold.infrastructure.encryption.EncryptionKeyProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * TokenEncryptionServiceImpl 单元测试
 */
@DisplayName("Token加密服务测试")
class TokenEncryptionServiceTest {

    private TokenEncryptionService encryptionService;
    private static final String TEST_ENCRYPTION_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    @BeforeEach
    void setUp() {
        encryptionService = new TokenEncryptionServiceImpl(new EncryptionKeyProvider(TEST_ENCRYPTION_KEY));
    }

    @Test
    @DisplayName("测试Token加密和解密")
    void testEncryptAndDecrypt() {
        // Given
        String originalToken =
                "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U";

        // When
        EncryptedToken encryptedToken = encryptionService.encrypt(originalToken);
        String decryptedToken = encryptionService.decrypt(encryptedToken.encryptedData(), encryptedToken.iv());

        // Then
        assertNotNull(encryptedToken, "加密结果不应为空");
        assertNotNull(encryptedToken.encryptedData(), "加密后的Token不应为空");
        assertNotNull(encryptedToken.iv(), "加密IV不应为空");
        assertNotEquals(originalToken, encryptedToken.encryptedData(), "加密后的Token应与原始Token不同");
        assertEquals(originalToken, decryptedToken, "解密后的Token应与原始Token相同");
    }

    @Test
    @DisplayName("测试存储格式加密和解密")
    void testEncryptForStorageAndDecrypt() {
        // Given
        String originalToken = "storage-format-test-token";

        // When
        String storedToken = encryptionService.encryptForStorage(originalToken);
        String decryptedToken = encryptionService.decrypt(storedToken);

        // Then
        assertNotNull(storedToken, "存储格式密文不应为空");
        assertTrue(storedToken.contains(":"), "存储格式应包含IV分隔符");
        assertEquals(originalToken, decryptedToken, "解密后的Token应与原始Token相同");
    }

    @Test
    @DisplayName("测试空Token处理")
    void testEncryptNullOrEmpty() {
        // When & Then
        assertThrows(RuntimeException.class, () -> encryptionService.encrypt(null), "加密null应抛出异常");

        assertThrows(IllegalArgumentException.class, () -> encryptionService.decrypt((String) null), "解密null应抛出异常");
        assertThrows(IllegalArgumentException.class, () -> encryptionService.decrypt(""), "解密空字符串应抛出异常");
    }

    @Test
    @DisplayName("测试加密结果的唯一性（相同明文每次加密结果不同）")
    void testEncryptionUniqueness() {
        // Given
        String token = "test-token-12345";

        // When
        EncryptedToken encrypted1 = encryptionService.encrypt(token);
        EncryptedToken encrypted2 = encryptionService.encrypt(token);

        // Then
        assertNotEquals(encrypted1.encryptedData(), encrypted2.encryptedData(), "相同明文每次加密应产生不同的密文（因为IV随机）");

        // But both should decrypt to the same value
        assertEquals(token, encryptionService.decrypt(encrypted1.encryptedData(), encrypted1.iv()));
        assertEquals(token, encryptionService.decrypt(encrypted2.encryptedData(), encrypted2.iv()));
    }

    @Test
    @DisplayName("测试解密失败场景")
    void testDecryptionFailure() {
        // Given
        String invalidBase64Data = "aXY=:invalid-base64-!@#$%";

        // When & Then
        assertThrows(RuntimeException.class, () -> encryptionService.decrypt(invalidBase64Data), "解密无效Base64密文应抛出异常");

        assertThrows(
                IllegalArgumentException.class,
                () -> encryptionService.decrypt("no-colon-separator"),
                "缺少IV分隔符的存储数据应抛出异常");
    }

    @Test
    @DisplayName("测试Token哈希")
    void testHashToken() {
        // Given
        String token = "hash-test-token";

        // When
        String hash1 = encryptionService.hashToken(token);
        String hash2 = encryptionService.hashToken(token);

        // Then
        assertNotNull(hash1, "哈希值不应为空");
        assertEquals(hash1, hash2, "相同Token的哈希值应相同");
        assertNotEquals(token, hash1, "哈希值应与原始Token不同");
    }

    @Test
    @DisplayName("测试长Token加密")
    void testEncryptLongToken() {
        // Given
        String longToken = "a".repeat(1000); // 1000字符的Token

        // When
        EncryptedToken encrypted = encryptionService.encrypt(longToken);
        String decrypted = encryptionService.decrypt(encrypted.encryptedData(), encrypted.iv());

        // Then
        assertEquals(longToken, decrypted, "长Token加密解密后应保持一致");
    }

    @Test
    @DisplayName("测试特殊字符Token")
    void testEncryptSpecialCharacters() {
        // Given
        String specialToken = "token+with/special=characters&symbols!@#$%^&*()中文字符";

        // When
        EncryptedToken encrypted = encryptionService.encrypt(specialToken);
        String decrypted = encryptionService.decrypt(encrypted.encryptedData(), encrypted.iv());

        // Then
        assertEquals(specialToken, decrypted, "包含特殊字符的Token加密解密后应保持一致");
    }

    @Test
    @DisplayName("测试错误的密钥长度")
    void testInvalidKeyLength() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> new EncryptionKeyProvider("short-key"), "非法Base64密钥应抛出异常");

        assertThrows(
                IllegalArgumentException.class,
                () -> new EncryptionKeyProvider("this-key-is-way-too-long-and-exceeds-32-bytes"),
                "非法Base64密钥应抛出异常");
    }

    @Test
    void shouldRejectMissingAndIncorrectlySizedKeys() {
        for (String key :
                new String[] {null, "", " ", "AAAAAAAAAAAAAAAAAAAAAA==", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"}) {
            assertThrows(IllegalArgumentException.class, () -> new EncryptionKeyProvider(key));
        }
    }

    @Test
    void shouldDecryptAcrossInstancesWithSameConfiguredKey() {
        String stored = encryptionService.encryptForStorage("restart-token");
        TokenEncryptionService restarted =
                new TokenEncryptionServiceImpl(new EncryptionKeyProvider(TEST_ENCRYPTION_KEY));
        assertEquals("restart-token", restarted.decrypt(stored));
    }

    @Test
    @DisplayName("测试加密性能")
    void testEncryptionPerformance() {
        // Given
        String token = "performance-test-token-12345";
        int iterations = 1000;

        // When
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            String encrypted = encryptionService.encryptForStorage(token);
            encryptionService.decrypt(encrypted);
        }
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Then
        System.out.printf("加密解密%d次耗时: %d ms (平均: %.2f ms/次)%n", iterations, duration, (double) duration / iterations);

        assertTrue(duration < 5000, String.format("1000次加密解密应在5秒内完成，实际耗时: %d ms", duration));
    }
}
