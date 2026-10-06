package com.apex.scaffold.authcenter.infrastructure.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

/**
 * 配置加密服务
 *
 * <p>用于加密应用配置中的敏感信息（数据库密码、API密钥等）。
 *
 * <h2>加密算法</h2>
 * <ul>
 *   <li>算法：AES-256-GCM（Galois/Counter Mode）</li>
 *   <li>密钥长度：256位</li>
 *   <li>IV长度：12字节（96位）</li>
 *   <li>认证标签长度：128位</li>
 * </ul>
 *
 * <h2>使用方式</h2>
 * <pre>{@code
 * # 1. 加密敏感配置
 * String encrypted = configEncryptionService.encrypt("my-secret-password");
 *
 * # 2. 在application.yml中使用加密后的值
 * spring:
 *   datasource:
 *     password: ENC(${encrypted})
 *
 * # 3. 程序启动时自动解密
 * }</pre>
 *
 * <h2>密钥管理建议</h2>
 * <ul>
 *   <li>生产环境：使用AWS KMS、HashiCorp Vault或Azure Key Vault</li>
 *   <li>开发环境：使用环境变量APEX_CONFIG_KEY</li>
 *   <li>密钥轮换：定期更新加密密钥并重新加密配置</li>
 * </ul>
 *
 * @author Apex Security Team
 * @since 2026-09-25
 */
@Slf4j
public class ConfigEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int AES_KEY_SIZE = 256;

    private final SecretKey secretKey;

    /**
     * 构造配置加密服务并加载Base64编码的AES密钥
     *
     * @param configKeyBase64 配置加密密钥（Base64编码的32字节密钥）
     * @throws IllegalArgumentException 密钥未配置、非有效Base64或长度不为32字节时抛出
     */
    public ConfigEncryptionService(@Value("${apex.security.config-encryption-key:}") String configKeyBase64) {

        if (configKeyBase64 == null || configKeyBase64.isBlank()) {
            throw new IllegalArgumentException("必须配置配置加密密钥");
        }
        byte[] decodedKey;
        try {
            decodedKey = Base64.getDecoder().decode(configKeyBase64);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("配置加密密钥必须为有效Base64");
        }
        if (decodedKey.length != AES_KEY_SIZE / Byte.SIZE) {
            throw new IllegalArgumentException("配置加密密钥必须是32字节（256位）");
        }
        this.secretKey = new SecretKeySpec(decodedKey, "AES");
        log.info("配置加密服务已初始化");
    }

    /**
     * 加密明文配置值
     *
     * @param plaintext 明文
     * @return 密文（Base64编码，包含IV）
     */
    public String encrypt(String plaintext) {
        try {
            // 生成随机IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);

            // 初始化Cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            // 加密
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // 将IV和密文合并：[IV(12字节) + 密文]
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            byteBuffer.put(iv);
            byteBuffer.put(ciphertext);

            // Base64编码
            return Base64.getEncoder().encodeToString(byteBuffer.array());

        } catch (Exception e) {
            log.error("配置加密失败", e);
            throw new RuntimeException("配置加密失败", e);
        }
    }

    /**
     * 解密密文配置值
     *
     * @param ciphertext 密文（Base64编码，包含IV）
     * @return 明文
     */
    public String decrypt(String ciphertext) {
        try {
            // Base64解码
            byte[] decoded = Base64.getDecoder().decode(ciphertext);

            // 分离IV和密文
            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);
            byte[] ciphertextBytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(ciphertextBytes);

            // 初始化Cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            // 解密
            byte[] plaintext = cipher.doFinal(ciphertextBytes);

            return new String(plaintext, StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("配置解密失败", e);
            throw new RuntimeException("配置解密失败", e);
        }
    }

    /**
     * 生成新的AES密钥
     *
     * @return AES密钥
     */
    public static SecretKey generateKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
            keyGenerator.init(AES_KEY_SIZE, new SecureRandom());
            return keyGenerator.generateKey();
        } catch (Exception e) {
            throw new RuntimeException("密钥生成失败", e);
        }
    }

    /**
     * 生成新的Base64编码的密钥（用于配置文件）
     *
     * @return Base64编码的密钥
     */
    public static String generateKeyBase64() {
        SecretKey key = generateKey();
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    /**
     * 判断字符串是否是加密值
     *
     * @param value 配置值
     * @return 如果是加密值返回true
     */
    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith("ENC(") && value.endsWith(")");
    }

    /**
     * 从加密值中提取密文
     *
     * @param encryptedValue 格式：ENC(密文)
     * @return 密文
     */
    public static String extractCiphertext(String encryptedValue) {
        if (!isEncrypted(encryptedValue)) {
            return encryptedValue;
        }
        return encryptedValue.substring(4, encryptedValue.length() - 1);
    }

    /**
     * 处理配置值（自动解密）
     *
     * @param value 配置值（可能是明文或密文）
     * @return 明文值
     */
    public String processConfigValue(String value) {
        if (isEncrypted(value)) {
            String ciphertext = extractCiphertext(value);
            return decrypt(ciphertext);
        }
        return value;
    }
}
