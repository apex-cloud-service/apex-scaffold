package com.apex.scaffold.integration.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apex.scaffold.integration.connector.domain.CredentialCipher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 凭据加密器测试（方案 M5 / 批次 E2）。
 *
 * <p>覆盖：AES-256-GCM 往返一致、随机 IV 唯一（密文/IV 分列可解）、
 * 错误密钥解密失败、密钥长度与缺失校验。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@DisplayName("凭据 AES-256-GCM 加密测试")
class CredentialCipherTest {

    private static final String KEY_32 = "0123456789abcdef0123456789abcdef";

    @Test
    @DisplayName("加密后可解密还原明文")
    void roundTrip() {
        CredentialCipher cipher = new CredentialCipher(KEY_32);
        String plaintext = "sk-live-abc123-密码~!@#";
        CredentialCipher.EncryptedSecret encrypted = cipher.encrypt(plaintext);
        assertThat(encrypted.cipherText()).isNotBlank().isNotEqualTo(plaintext);
        assertThat(encrypted.iv()).isNotBlank();
        assertThat(cipher.decrypt(encrypted.cipherText(), encrypted.iv())).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("同一明文两次加密 IV 与密文均不同（随机 IV）")
    void randomIvPerEncryption() {
        CredentialCipher cipher = new CredentialCipher(KEY_32);
        CredentialCipher.EncryptedSecret first = cipher.encrypt("same-secret");
        CredentialCipher.EncryptedSecret second = cipher.encrypt("same-secret");
        assertThat(first.iv()).isNotEqualTo(second.iv());
        assertThat(first.cipherText()).isNotEqualTo(second.cipherText());
        assertThat(cipher.decrypt(first.cipherText(), first.iv())).isEqualTo("same-secret");
        assertThat(cipher.decrypt(second.cipherText(), second.iv())).isEqualTo("same-secret");
    }

    @Test
    @DisplayName("错误密钥解密失败（GCM 认证标签校验）")
    void wrongKeyFails() {
        CredentialCipher cipher = new CredentialCipher(KEY_32);
        CredentialCipher.EncryptedSecret encrypted = cipher.encrypt("secret");
        CredentialCipher other = new CredentialCipher("ffffffffffffffffffffffffffffffff");
        assertThatThrownBy(() -> other.decrypt(encrypted.cipherText(), encrypted.iv()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("凭据解密失败");
    }

    @Test
    @DisplayName("密钥长度不足或缺失时加解密显式失败")
    void keyValidation() {
        assertThatThrownBy(() -> new CredentialCipher("short").encrypt("x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("credential-encryption-key");
        assertThatThrownBy(() -> new CredentialCipher(null).decrypt("a", "b"))
                .isInstanceOf(IllegalStateException.class);
    }
}
