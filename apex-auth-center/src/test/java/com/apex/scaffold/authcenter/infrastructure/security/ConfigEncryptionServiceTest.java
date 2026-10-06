package com.apex.scaffold.authcenter.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link ConfigEncryptionService} 的单元测试类
 *
 * <p>测试配置加密服务的核心逻辑：加解密往返、密钥生成、ENC()标记解析、自动解密。
 *
 * @author Apex Team
 * @since 1.0.0
 */
@DisplayName("ConfigEncryptionService 单元测试")
class ConfigEncryptionServiceTest {

    @Test
    void shouldRejectMissingAndInvalidKeys() {
        for (String key : new String[] {null, "", " ", "invalid-secret!"}) {
            assertThatThrownBy(() -> new ConfigEncryptionService(key)).isInstanceOf(IllegalArgumentException.class);
        }
        for (int length : new int[] {0, 16, 24, 31, 33}) {
            String key = Base64.getEncoder().encodeToString(new byte[length]);
            assertThatThrownBy(() -> new ConfigEncryptionService(key)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("使用显式生成密钥")
    class WithGeneratedKeyTests {

        private ConfigEncryptionService service;

        @BeforeEach
        void setUp() {
            service = new ConfigEncryptionService(ConfigEncryptionService.generateKeyBase64());
        }

        @Test
        @DisplayName("加密后解密应还原出原始明文")
        void shouldRoundTripEncryptDecrypt() {
            String plaintext = "my-secret-password";

            String ciphertext = service.encrypt(plaintext);
            String decrypted = service.decrypt(ciphertext);

            assertThat(decrypted).isEqualTo(plaintext);
            assertThat(ciphertext).isNotEqualTo(plaintext);
        }

        @Test
        @DisplayName("每次加密同一明文应生成不同密文（随机IV）")
        void shouldGenerateDifferentCiphertextEachTime() {
            String plaintext = "same-secret";

            String ciphertext1 = service.encrypt(plaintext);
            String ciphertext2 = service.encrypt(plaintext);

            assertThat(ciphertext1).isNotEqualTo(ciphertext2);
            assertThat(service.decrypt(ciphertext1)).isEqualTo(plaintext);
            assertThat(service.decrypt(ciphertext2)).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("解密被篡改的密文应抛出RuntimeException")
        void shouldThrowWhenCiphertextTampered() {
            String ciphertext = service.encrypt("secret-value");
            byte[] raw = Base64.getDecoder().decode(ciphertext);
            raw[raw.length - 1] ^= 0x01;
            String tampered = Base64.getEncoder().encodeToString(raw);

            assertThatThrownBy(() -> service.decrypt(tampered))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("配置解密失败");
        }

        @Test
        @DisplayName("解密非法Base64应抛出RuntimeException")
        void shouldThrowWhenCiphertextInvalid() {
            assertThatThrownBy(() -> service.decrypt("not-a-valid-base64!@#"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("配置解密失败");
        }
    }

    @Nested
    @DisplayName("使用配置提供的密钥")
    class WithProvidedKeyTests {

        @Test
        @DisplayName("提供Base64密钥时应正常加解密")
        void shouldWorkWithProvidedKey() {
            String keyBase64 = ConfigEncryptionService.generateKeyBase64();
            ConfigEncryptionService service = new ConfigEncryptionService(keyBase64);

            String ciphertext = service.encrypt("provided-key-secret");
            String decrypted = service.decrypt(ciphertext);

            assertThat(decrypted).isEqualTo("provided-key-secret");
        }

        @Test
        @DisplayName("不同实例使用相同密钥应能互相解密")
        void shouldDecryptAcrossInstancesWithSameKey() {
            String keyBase64 = ConfigEncryptionService.generateKeyBase64();
            ConfigEncryptionService encryptor = new ConfigEncryptionService(keyBase64);
            ConfigEncryptionService decryptor = new ConfigEncryptionService(keyBase64);

            String ciphertext = encryptor.encrypt("shared-key-secret");

            assertThat(decryptor.decrypt(ciphertext)).isEqualTo("shared-key-secret");
        }
    }

    @Nested
    @DisplayName("generateKey / generateKeyBase64 方法")
    class GenerateKeyTests {

        @Test
        @DisplayName("生成的密钥应为256位AES密钥")
        void shouldGenerate256BitAesKey() {
            SecretKey key = ConfigEncryptionService.generateKey();

            assertThat(key.getAlgorithm()).isEqualTo("AES");
            assertThat(key.getEncoded()).hasSize(32);
        }

        @Test
        @DisplayName("生成的Base64密钥应可被正确解码为32字节")
        void shouldGenerateValidBase64Key() {
            String base64Key = ConfigEncryptionService.generateKeyBase64();

            byte[] decoded = Base64.getDecoder().decode(base64Key);
            assertThat(decoded).hasSize(32);
        }

        @Test
        @DisplayName("多次生成密钥应互不相同")
        void shouldGenerateDifferentKeysEachTime() {
            String key1 = ConfigEncryptionService.generateKeyBase64();
            String key2 = ConfigEncryptionService.generateKeyBase64();

            assertThat(key1).isNotEqualTo(key2);
        }
    }

    @Nested
    @DisplayName("isEncrypted 方法")
    class IsEncryptedTests {

        @Test
        @DisplayName("符合ENC(...)格式应返回true")
        void shouldReturnTrueForEncFormat() {
            assertThat(ConfigEncryptionService.isEncrypted("ENC(abc123)")).isTrue();
        }

        @Test
        @DisplayName("普通明文应返回false")
        void shouldReturnFalseForPlaintext() {
            assertThat(ConfigEncryptionService.isEncrypted("plain-password")).isFalse();
        }

        @Test
        @DisplayName("null值应返回false")
        void shouldReturnFalseForNull() {
            assertThat(ConfigEncryptionService.isEncrypted(null)).isFalse();
        }

        @Test
        @DisplayName("只有前缀无后缀括号应返回false")
        void shouldReturnFalseWhenMissingClosingParen() {
            assertThat(ConfigEncryptionService.isEncrypted("ENC(abc123")).isFalse();
        }
    }

    @Nested
    @DisplayName("extractCiphertext 方法")
    class ExtractCiphertextTests {

        @Test
        @DisplayName("应正确提取ENC()包裹的密文")
        void shouldExtractCiphertextFromEncFormat() {
            assertThat(ConfigEncryptionService.extractCiphertext("ENC(abc123)")).isEqualTo("abc123");
        }

        @Test
        @DisplayName("非加密格式应原样返回")
        void shouldReturnAsIsWhenNotEncrypted() {
            assertThat(ConfigEncryptionService.extractCiphertext("plain-value")).isEqualTo("plain-value");
        }
    }

    @Nested
    @DisplayName("processConfigValue 方法")
    class ProcessConfigValueTests {

        private ConfigEncryptionService service;

        @BeforeEach
        void setUp() {
            service = new ConfigEncryptionService(ConfigEncryptionService.generateKeyBase64());
        }

        @Test
        @DisplayName("加密格式的值应自动解密返回明文")
        void shouldDecryptWhenValueIsEncrypted() {
            String plaintext = "db-password-123";
            String ciphertext = service.encrypt(plaintext);
            String configValue = "ENC(" + ciphertext + ")";

            assertThat(service.processConfigValue(configValue)).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("明文值应原样返回，不做解密")
        void shouldReturnPlaintextAsIs() {
            assertThat(service.processConfigValue("plain-config-value")).isEqualTo("plain-config-value");
        }
    }
}
