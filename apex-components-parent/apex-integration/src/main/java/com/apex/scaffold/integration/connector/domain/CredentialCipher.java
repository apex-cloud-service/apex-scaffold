package com.apex.scaffold.integration.connector.domain;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 集成域凭据加密器（方案 M5 / 批次 E2）。
 *
 * <p>AES-256-GCM 认证加密；密文与 IV 分列存储（对应 {@code int_credential.secret_cipher}
 * 与 {@code secret_iv}），每次加密使用随机 96 位 IV，认证标签 128 位。
 *
 * <p>密钥经 {@code apex.security.credential-encryption-key} 注入（沿项目既有
 * {@code apex.security.*} 密钥配置模式，须为 32 字节）。密钥校验延迟到首次加解密：
 * 未配置密钥的宿主仅在不使用集成域凭据功能时方可正常启动。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public class CredentialCipher {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int KEY_LENGTH_BYTES = 32;

    private final byte[] keyBytes;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 构造凭据加密器。
     *
     * @param encryptionKey 配置注入的加密密钥原文（须恰好 32 字节；可为空，延迟到首次使用时校验）
     */
    public CredentialCipher(String encryptionKey) {
        this.keyBytes = encryptionKey == null || encryptionKey.isBlank()
                ? null
                : encryptionKey.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 加密明文。
     *
     * @param plaintext 明文（不可为空）
     * @return 密文与 IV（均为 Base64）
     */
    public EncryptedSecret encrypt(String plaintext) {
        byte[] key = requireKey();
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return new EncryptedSecret(
                    Base64.getEncoder().encodeToString(cipherText),
                    Base64.getEncoder().encodeToString(iv));
        } catch (Exception e) {
            throw new IllegalStateException("凭据加密失败", e);
        }
    }

    /**
     * 解密密文。
     *
     * @param cipherTextBase64 密文（Base64，不含 IV）
     * @param ivBase64         IV（Base64）
     * @return 明文
     */
    public String decrypt(String cipherTextBase64, String ivBase64) {
        byte[] key = requireKey();
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_LENGTH_BITS, Base64.getDecoder().decode(ivBase64)));
            byte[] plain = cipher.doFinal(Base64.getDecoder().decode(cipherTextBase64));
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("凭据解密失败", e);
        }
    }

    private byte[] requireKey() {
        if (keyBytes == null || keyBytes.length != KEY_LENGTH_BYTES) {
            throw new IllegalStateException("必须配置 apex.security.credential-encryption-key（32 字节）");
        }
        return keyBytes;
    }

    /**
     * 加密结果。
     *
     * @param cipherText 密文（Base64）
     * @param iv         IV（Base64）
     */
    public record EncryptedSecret(String cipherText, String iv) {}
}
