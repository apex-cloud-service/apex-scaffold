package com.apex.scaffold.infrastructure.encryption;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Token加密服务
 * 使用AES-256-GCM加密Token，提供高强度加密保护
 *
 * 安全特性：
 * - AES-256-GCM认证加密模式
 * - 每次加密使用唯一的IV
 * - SHA-256哈希用于快速查找
 * @author Apex Team
 * @since 1.2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;
    private static final int KEY_LENGTH = 256;

    private final EncryptionKeyProvider keyProvider;

    /**
     * 计算Token的SHA-256哈希
     * 用于数据库索引和快速查找，不可逆
     *
     * @param token 原始Token
     * @return Base64编码的SHA-256哈希值
     */
    public String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            log.error("Token哈希计算失败", e);
            throw new RuntimeException("Token哈希计算失败", e);
        }
    }

    /**
     * 加密Token
     * 使用AES-256-GCM模式，提供机密性和完整性保护
     *
     * @param token 原始Token
     * @return EncryptedToken对象，包含加密数据和IV
     */
    public EncryptedToken encrypt(String token) {
        try {
            byte[] iv = generateIV();

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keyProvider.getEncryptionKey(), gcmSpec);

            byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));

            return new EncryptedToken(
                    Base64.getEncoder().encodeToString(encrypted),
                    Base64.getEncoder().encodeToString(iv));
        } catch (Exception e) {
            log.error("Token加密失败", e);
            throw new RuntimeException("Token加密失败", e);
        }
    }

    /**
     * 解密Token
     *
     * @param encryptedData Base64编码的加密数据
     * @param ivBase64 Base64编码的IV
     * @return 解密后的原始Token
     */
    public String decrypt(String encryptedData, String ivBase64) {
        try {
            byte[] encrypted = Base64.getDecoder().decode(encryptedData);
            byte[] iv = Base64.getDecoder().decode(ivBase64);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, keyProvider.getEncryptionKey(), gcmSpec);

            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Token解密失败", e);
            throw new RuntimeException("Token解密失败，可能Token已损坏或密钥错误", e);
        }
    }

    /**
     * 加密Token并返回可存储的字符串格式
     *
     * <p>将IV和加密数据以 "iv:encryptedData" 格式拼接，
     * 便于单字段存储，解密时自动解析。
     *
     * @param token 原始Token
     * @return "iv:encryptedData" 格式的字符串
     */
    public String encryptForStorage(String token) {
        EncryptedToken encrypted = encrypt(token);
        return encrypted.iv() + ":" + encrypted.encryptedData();
    }

    /**
     * 从存储格式解密Token
     *
     * <p>解析 "iv:encryptedData" 格式并解密。
     *
     * @param storedData "iv:encryptedData" 格式的字符串
     * @return 解密后的原始Token
     */
    public String decrypt(String storedData) {
        if (storedData == null || storedData.isEmpty()) {
            throw new IllegalArgumentException("存储数据不能为空");
        }
        int colonIndex = storedData.indexOf(':');
        if (colonIndex < 0) {
            throw new IllegalArgumentException("存储数据格式无效，缺少IV分隔符");
        }
        String ivBase64 = storedData.substring(0, colonIndex);
        String encryptedData = storedData.substring(colonIndex + 1);
        return decrypt(encryptedData, ivBase64);
    }

    /**
     * 生成随机IV（初始化向量）
     * 每次加密使用不同的IV，增强安全性
     */
    private byte[] generateIV() {
        byte[] iv = new byte[IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        return iv;
    }

    /**
     * 加密Token数据结构
     *
     * @param encryptedData Base64编码的加密数据
     * @param iv Base64编码的初始化向量
     */
    public record EncryptedToken(String encryptedData, String iv) {}
}
