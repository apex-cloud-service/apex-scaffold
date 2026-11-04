package com.apex.scaffold.common.security;

import jakarta.annotation.PostConstruct;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 配置加密服务
 *
 * <p>用于加密和解密应用配置中的敏感信息，如密钥、密码、API密钥等。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>数据库密码加密存储</li>
 *   <li>第三方API密钥加密</li>
 *   <li>JWT签名密钥加密</li>
 *   <li>OAuth2 Client Secret加密</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>算法：AES-256-GCM（认证加密）</li>
 *   <li>密钥长度：256位</li>
 *   <li>IV长度：96位（推荐）</li>
 *   <li>Tag长度：128位</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>
 * # 加密配置值格式：ENC(base64EncodedCiphertext)
 * spring:
 *   datasource:
 *     password: ENC(AQIDBAUGBwgJCgsMDQ4PEBESExQV...)
 *
 * # 明文配置值（不推荐）
 * spring:
 *   datasource:
 *     password: ${DB_PASSWORD}
 * </pre>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>开发环境：使用默认密钥（仅用于测试）</li>
 *   <li>生产环境：通过环境变量 CONFIG_ENCRYPTION_KEY 注入</li>
 *   <li>密钥生成：openssl rand -base64 32</li>
 *   <li>密钥存储：AWS KMS / HashiCorp Vault / Azure Key Vault</li>
 * </ul>
 *
 * @author Apex Security Team
 * @version 1.0.0
 * @since 1.3.1
 */
@Slf4j
@Service
public class ConfigEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int KEY_LENGTH_BYTES = 32;
    private static final String ENC_PREFIX = "ENC(";
    private static final String ENC_SUFFIX = ")";

    private final SecretKey secretKey;
    private final SecureRandom secureRandom;

    /**
     * 构造配置加密服务。
     *
     * @param encryptionKey 配置加密密钥，必须恰好 32 字节
     * @throws IllegalArgumentException 密钥缺失或长度不是 32 字节时抛出
     */
    public ConfigEncryptionService(@Value("${security.config.encryption.key:}") String encryptionKey) {

        if (encryptionKey == null || encryptionKey.isBlank()) {
            throw new IllegalArgumentException("必须配置配置加密密钥");
        }

        byte[] keyBytes = encryptionKey.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length != KEY_LENGTH_BYTES) {
            throw new IllegalArgumentException("配置加密密钥必须是32字节（256位）");
        }

        this.secretKey = new SecretKeySpec(keyBytes, "AES");
        this.secureRandom = new SecureRandom();
    }

    /** 初始化完成回调，打印就绪日志。 */
    @PostConstruct
    public void init() {
        log.info("配置加密服务已初始化");
    }

    /**
     * 加密敏感配置值
     *
     * @param plaintext 明文配置值
     * @return 加密后的配置值（格式：ENC(base64)）
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }

        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // 组合：IV + 密文
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            byteBuffer.put(iv);
            byteBuffer.put(ciphertext);

            String encoded = Base64.getEncoder().encodeToString(byteBuffer.array());
            return ENC_PREFIX + encoded + ENC_SUFFIX;

        } catch (Exception e) {
            throw new RuntimeException("配置加密失败", e);
        }
    }

    /**
     * 解密加密的配置值
     *
     * @param encryptedValue 加密的配置值（格式：ENC(base64) 或明文）
     * @return 解密后的明文
     */
    public String decrypt(String encryptedValue) {
        if (encryptedValue == null || encryptedValue.isEmpty()) {
            return encryptedValue;
        }

        // 如果不是加密格式，直接返回
        if (!isEncrypted(encryptedValue)) {
            return encryptedValue;
        }

        try {
            // 提取Base64密文
            String base64Ciphertext =
                    encryptedValue.substring(ENC_PREFIX.length(), encryptedValue.length() - ENC_SUFFIX.length());

            byte[] combined = Base64.getDecoder().decode(base64Ciphertext);

            // 分离IV和密文
            ByteBuffer byteBuffer = ByteBuffer.wrap(combined);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);

            byte[] ciphertext = new byte[byteBuffer.remaining()];
            byteBuffer.get(ciphertext);

            // 解密
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);

        } catch (Exception e) {
            throw new RuntimeException("配置解密失败", e);
        }
    }

    /**
     * 判断配置值是否已加密
     *
     * @param value 配置值
     * @return true-已加密，false-明文
     */
    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(ENC_PREFIX) && value.endsWith(ENC_SUFFIX);
    }

    /**
     * 批量解密配置项
     *
     * @param encryptedValues 加密的配置值数组
     * @return 解密后的明文数组
     */
    public String[] decryptBatch(String... encryptedValues) {
        if (encryptedValues == null) {
            return new String[0];
        }

        String[] decrypted = new String[encryptedValues.length];
        for (int i = 0; i < encryptedValues.length; i++) {
            decrypted[i] = decrypt(encryptedValues[i]);
        }
        return decrypted;
    }
}
