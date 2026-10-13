package com.apex.scaffold.auth.infrastructure.security;

import static org.junit.jupiter.api.Assertions.*;

import com.apex.scaffold.auth.domain.service.TokenEncryptionService;
import com.apex.scaffold.auth.domain.service.TokenEncryptionService.EncryptedToken;
import com.apex.scaffold.auth.infrastructure.encryption.TokenEncryptionServiceImpl;
import com.apex.scaffold.infrastructure.encryption.EncryptionKeyProvider;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Token加密服务单元测试
 *
 * @author Apex Security Team
 */
class TokenEncryptionServiceTest {

    private TokenEncryptionService encryptionService;
    private static final String TEST_KEY = "dGVzdC1rZXktZm9yLXVuaXQtdGVzdC0zMi1ieXRlcyE="; // 32字节Base64

    @BeforeEach
    void setUp() {
        encryptionService = new TokenEncryptionServiceImpl(new EncryptionKeyProvider(TEST_KEY));
    }

    @Test
    void testEncryptAndDecrypt() {
        String plainToken =
                "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ";

        EncryptedToken encrypted = encryptionService.encrypt(plainToken);
        assertNotNull(encrypted);
        assertNotNull(encrypted.encryptedData());
        assertNotNull(encrypted.iv());
        assertNotEquals(plainToken, encrypted.encryptedData());

        String decrypted = encryptionService.decrypt(encrypted.encryptedData(), encrypted.iv());
        assertEquals(plainToken, decrypted);
    }

    @Test
    void testEncryptProducesDifferentCiphertexts() {
        String plainToken = "test-token-123";

        EncryptedToken encrypted1 = encryptionService.encrypt(plainToken);
        EncryptedToken encrypted2 = encryptionService.encrypt(plainToken);

        // 由于每次使用不同的IV，相同明文应产生不同密文
        assertNotEquals(encrypted1.encryptedData(), encrypted2.encryptedData());
        assertNotEquals(encrypted1.iv(), encrypted2.iv());

        // 但解密后应该相同
        assertEquals(plainToken, encryptionService.decrypt(encrypted1.encryptedData(), encrypted1.iv()));
        assertEquals(plainToken, encryptionService.decrypt(encrypted2.encryptedData(), encrypted2.iv()));
    }

    @Test
    void testDecryptWithTamperedData() {
        String plainToken = "test-token";
        EncryptedToken encrypted = encryptionService.encrypt(plainToken);

        // 篡改加密数据
        byte[] encryptedBytes = Base64.getDecoder().decode(encrypted.encryptedData());
        encryptedBytes[encryptedBytes.length - 1] ^= 1; // 修改最后一个字节
        String tampered = Base64.getEncoder().encodeToString(encryptedBytes);

        // 解密应该失败
        assertThrows(RuntimeException.class, () -> encryptionService.decrypt(tampered, encrypted.iv()));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "   ",
                "short",
                "very-long-token-that-exceeds-typical-jwt-length-to-test-encryption-with-large-payloads"
            })
    void testEncryptVariousTokenLengths(String token) {
        EncryptedToken encrypted = encryptionService.encrypt(token);
        String decrypted = encryptionService.decrypt(encrypted.encryptedData(), encrypted.iv());
        assertEquals(token, decrypted);
    }

    @Test
    void testDecryptInvalidBase64() {
        // 密文部分不是合法Base64
        assertThrows(RuntimeException.class, () -> encryptionService.decrypt("aXY=:not-valid-base64!@#$"));
        // 缺少IV分隔符
        assertThrows(IllegalArgumentException.class, () -> encryptionService.decrypt("not-valid-base64"));
    }

    @Test
    void testDecryptTooShort() {
        // 小于GCM认证标签长度的密文无法解密
        String shortData = Base64.getEncoder().encodeToString(new byte[5]);
        assertThrows(RuntimeException.class, () -> encryptionService.decrypt(shortData, "AAAAAAAAAAAA"));
    }

    @Test
    void testNullInputs() {
        assertThrows(RuntimeException.class, () -> encryptionService.encrypt(null));
        assertThrows(IllegalArgumentException.class, () -> encryptionService.decrypt((String) null));
    }

    @Test
    void testEncryptionWithSpecialCharacters() {
        String plainToken = "token-with-special-chars-!@#$%^&*()_+-=[]|\\:\";<>?,./";

        EncryptedToken encrypted = encryptionService.encrypt(plainToken);
        String decrypted = encryptionService.decrypt(encrypted.encryptedData(), encrypted.iv());

        assertEquals(plainToken, decrypted);
    }

    @Test
    void testEncryptionWithUnicode() {
        String plainToken = "token-with-unicode-中文-日本語-한글-🔐";

        EncryptedToken encrypted = encryptionService.encrypt(plainToken);
        String decrypted = encryptionService.decrypt(encrypted.encryptedData(), encrypted.iv());

        assertEquals(plainToken, decrypted);
    }

    @Test
    void testConcurrentEncryption() throws InterruptedException {
        String plainToken = "concurrent-test-token";
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        String[] results = new String[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                String encrypted = encryptionService.encryptForStorage(plainToken);
                results[index] = encryptionService.decrypt(encrypted);
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        // 所有线程应该得到正确的解密结果
        for (String result : results) {
            assertEquals(plainToken, result);
        }
    }
}
