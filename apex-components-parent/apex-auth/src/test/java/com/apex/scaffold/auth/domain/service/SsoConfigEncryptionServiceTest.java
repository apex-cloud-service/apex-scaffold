package com.apex.scaffold.auth.domain.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SSO配置加密服务测试")
class SsoConfigEncryptionServiceTest {

    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    private SsoConfigEncryptionService service;

    @BeforeEach
    void setUp() {
        service = new SsoConfigEncryptionService(redisTemplate, null);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    /** 以显式主密钥重建被测服务（等价于配置 apex.security.sso.encryption.master-key）。 */
    private void useMasterKey(String masterKey) {
        service = new SsoConfigEncryptionService(redisTemplate, masterKey);
    }

    @Test
    @DisplayName("加密和解密 - 成功往返")
    void testEncryptAndDecrypt_SuccessfulRoundTrip() {
        String masterKey = "YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWE=";
        useMasterKey(masterKey);

        String plainText = "my-client-secret-12345";

        StepVerifier.create(service.encrypt(plainText).flatMap(encrypted -> {
                    assertNotNull(encrypted);
                    assertTrue(encrypted.contains(":"));
                    return service.decrypt(encrypted);
                }))
                .assertNext(decrypted -> {
                    assertEquals(plainText, decrypted);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("加密空字符串 - 返回空字符串")
    void testEncrypt_EmptyString() {
        StepVerifier.create(service.encrypt("")).expectNext("").verifyComplete();
    }

    @Test
    @DisplayName("加密null - 返回空字符串")
    void testEncrypt_Null() {
        StepVerifier.create(service.encrypt(null)).expectNext("").verifyComplete();
    }

    @Test
    @DisplayName("解密空字符串 - 返回空字符串")
    void testDecrypt_EmptyString() {
        StepVerifier.create(service.decrypt("")).expectNext("").verifyComplete();
    }

    @Test
    @DisplayName("解密null - 返回空字符串")
    void testDecrypt_Null() {
        StepVerifier.create(service.decrypt(null)).expectNext("").verifyComplete();
    }

    @Test
    @DisplayName("加密 - 从环境变量获取主密钥")
    void testEncrypt_WithMasterKeyFromEnv() {
        String masterKey = Base64.getEncoder().encodeToString(new byte[32]);
        useMasterKey(masterKey);

        String plainText = "sensitive-data";

        StepVerifier.create(service.encrypt(plainText))
                .assertNext(encrypted -> {
                    assertNotNull(encrypted);
                    assertTrue(encrypted.contains(":"));
                    String[] parts = encrypted.split(":");
                    assertEquals(2, parts.length);
                })
                .verifyComplete();

        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    @DisplayName("加密 - 从Redis获取主密钥")
    void testEncrypt_WithMasterKeyFromRedis() {
        String masterKey = Base64.getEncoder().encodeToString(new byte[32]);

        when(valueOperations.get("sso:encryption:master_key")).thenReturn(Mono.just(masterKey));

        String plainText = "secret-value";

        StepVerifier.create(service.encrypt(plainText))
                .assertNext(encrypted -> {
                    assertNotNull(encrypted);
                    assertTrue(encrypted.contains(":"));
                })
                .verifyComplete();

        verify(valueOperations).get("sso:encryption:master_key");
    }

    @Test
    @DisplayName("加密 - 生成新主密钥")
    void testEncrypt_GenerateNewMasterKey() {
        when(valueOperations.get("sso:encryption:master_key")).thenReturn(Mono.empty());
        when(valueOperations.set(eq("sso:encryption:master_key"), anyString())).thenReturn(Mono.just(true));

        String plainText = "test-secret";

        StepVerifier.create(service.encrypt(plainText))
                .assertNext(encrypted -> {
                    assertNotNull(encrypted);
                    assertTrue(encrypted.contains(":"));
                })
                .verifyComplete();

        verify(valueOperations).get("sso:encryption:master_key");
        verify(valueOperations).set(eq("sso:encryption:master_key"), anyString());
    }

    @Test
    @DisplayName("解密 - 成功")
    void testDecrypt_Success() {
        String masterKey = "YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWE=";
        useMasterKey(masterKey);

        String plainText = "original-secret";

        StepVerifier.create(service.encrypt(plainText).flatMap(service::decrypt))
                .expectNext(plainText)
                .verifyComplete();
    }

    @Test
    @DisplayName("解密 - 无效格式")
    void testDecrypt_InvalidFormat() {
        String masterKey = "YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWE=";
        useMasterKey(masterKey);

        String invalidEncrypted = "invalid-format-without-colon";

        StepVerifier.create(service.decrypt(invalidEncrypted))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    @DisplayName("解密 - 无效Base64")
    void testDecrypt_InvalidBase64() {
        String masterKey = "YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWE=";
        useMasterKey(masterKey);

        String invalidEncrypted = "invalid!!!:base64!!!";

        StepVerifier.create(service.decrypt(invalidEncrypted)).expectError().verify();
    }

    @Test
    @DisplayName("轮换主密钥 - 成功")
    void testRotateMasterKey_Success() {
        when(valueOperations.set(eq("sso:encryption:master_key"), anyString())).thenReturn(Mono.just(true));

        StepVerifier.create(service.rotateMasterKey())
                .assertNext(newKey -> {
                    assertNotNull(newKey);
                    assertFalse(newKey.isEmpty());
                })
                .verifyComplete();

        verify(valueOperations).set(eq("sso:encryption:master_key"), anyString());
    }

    @Test
    @DisplayName("轮换主密钥 - Redis存储失败")
    void testRotateMasterKey_RedisFailure() {
        when(valueOperations.set(eq("sso:encryption:master_key"), anyString()))
                .thenReturn(Mono.error(new RuntimeException("Redis connection failed")));

        StepVerifier.create(service.rotateMasterKey())
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    @DisplayName("检查敏感字段 - clientSecret")
    void testIsSensitiveField_ClientSecret() {
        assertTrue(service.isSensitiveField("clientSecret"));
        assertTrue(service.isSensitiveField("ClientSecret"));
        assertTrue(service.isSensitiveField("CLIENTSECRET"));
    }

    @Test
    @DisplayName("检查敏感字段 - privateKey")
    void testIsSensitiveField_PrivateKey() {
        assertTrue(service.isSensitiveField("privateKey"));
        assertTrue(service.isSensitiveField("PrivateKey"));
    }

    @Test
    @DisplayName("检查敏感字段 - certificate")
    void testIsSensitiveField_Certificate() {
        assertTrue(service.isSensitiveField("certificate"));
        assertTrue(service.isSensitiveField("Certificate"));
    }

    @Test
    @DisplayName("检查敏感字段 - bindPassword")
    void testIsSensitiveField_BindPassword() {
        assertTrue(service.isSensitiveField("bindPassword"));
        assertTrue(service.isSensitiveField("BindPassword"));
    }

    @Test
    @DisplayName("检查敏感字段 - apiKey")
    void testIsSensitiveField_ApiKey() {
        assertTrue(service.isSensitiveField("apiKey"));
        assertTrue(service.isSensitiveField("ApiKey"));
    }

    @Test
    @DisplayName("检查敏感字段 - token")
    void testIsSensitiveField_Token() {
        assertTrue(service.isSensitiveField("token"));
        assertTrue(service.isSensitiveField("Token"));
    }

    @Test
    @DisplayName("检查敏感字段 - 包含secret关键字")
    void testIsSensitiveField_ContainsSecret() {
        assertTrue(service.isSensitiveField("oauth2Secret"));
        assertTrue(service.isSensitiveField("secretKey"));
        assertTrue(service.isSensitiveField("mySecret"));
    }

    @Test
    @DisplayName("检查敏感字段 - 包含password关键字")
    void testIsSensitiveField_ContainsPassword() {
        assertTrue(service.isSensitiveField("userPassword"));
        assertTrue(service.isSensitiveField("dbPassword"));
        assertTrue(service.isSensitiveField("adminPassword"));
    }

    @Test
    @DisplayName("检查敏感字段 - 包含key关键字")
    void testIsSensitiveField_ContainsKey() {
        assertTrue(service.isSensitiveField("encryptionKey"));
        assertTrue(service.isSensitiveField("signingKey"));
        assertTrue(service.isSensitiveField("masterKey"));
    }

    @Test
    @DisplayName("检查敏感字段 - 非敏感字段")
    void testIsSensitiveField_NonSensitive() {
        assertFalse(service.isSensitiveField("username"));
        assertFalse(service.isSensitiveField("clientId"));
        assertFalse(service.isSensitiveField("tenantId"));
        assertFalse(service.isSensitiveField("email"));
        assertFalse(service.isSensitiveField("redirectUri"));
    }

    @Test
    @DisplayName("检查敏感字段 - null")
    void testIsSensitiveField_Null() {
        assertFalse(service.isSensitiveField(null));
    }

    @Test
    @DisplayName("加密长文本 - 成功")
    void testEncrypt_LongText() {
        String masterKey = "YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWE=";
        useMasterKey(masterKey);

        String longText = "A".repeat(1000);

        StepVerifier.create(service.encrypt(longText).flatMap(service::decrypt))
                .expectNext(longText)
                .verifyComplete();
    }

    @Test
    @DisplayName("加密特殊字符 - 成功")
    void testEncrypt_SpecialCharacters() {
        String masterKey = "YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWE=";
        useMasterKey(masterKey);

        String specialChars = "!@#$%^&*()_+-=[]{}|;:'\",.<>?/~`中文字符";

        StepVerifier.create(service.encrypt(specialChars).flatMap(service::decrypt))
                .expectNext(specialChars)
                .verifyComplete();
    }

    @Test
    @DisplayName("加密UTF-8字符 - 成功")
    void testEncrypt_Utf8Characters() {
        String masterKey = "YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWE=";
        useMasterKey(masterKey);

        String utf8Text = "你好世界 🌍 こんにちは Привет";

        StepVerifier.create(service.encrypt(utf8Text).flatMap(service::decrypt))
                .expectNext(utf8Text)
                .verifyComplete();
    }

    @Test
    @DisplayName("加密多次 - 每次生成不同密文")
    void testEncrypt_MultipleTimes_DifferentCiphertext() {
        String masterKey = "YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWE=";
        useMasterKey(masterKey);

        String plainText = "same-plain-text";

        StepVerifier.create(Mono.zip(service.encrypt(plainText), service.encrypt(plainText)))
                .assertNext(tuple -> {
                    String encrypted1 = tuple.getT1();
                    String encrypted2 = tuple.getT2();
                    assertNotEquals(encrypted1, encrypted2);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("生成主密钥后加密 - 成功")
    void testEncrypt_AfterKeyGeneration() {
        when(valueOperations.get("sso:encryption:master_key")).thenReturn(Mono.empty());
        when(valueOperations.set(eq("sso:encryption:master_key"), anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(1);
            when(valueOperations.get("sso:encryption:master_key")).thenReturn(Mono.just(key));
            return Mono.just(true);
        });

        String plainText = "test-data";

        StepVerifier.create(service.encrypt(plainText))
                .assertNext(encrypted -> {
                    assertNotNull(encrypted);
                    assertTrue(encrypted.contains(":"));
                })
                .verifyComplete();
    }
}
