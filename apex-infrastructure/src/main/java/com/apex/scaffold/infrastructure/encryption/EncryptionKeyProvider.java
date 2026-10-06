package com.apex.scaffold.infrastructure.encryption;

import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 加密密钥提供者
 * 负责管理和提供加密密钥
 *
 * 生产环境建议：
 * - 从AWS KMS、Azure Key Vault或HashiCorp Vault获取密钥
 * - 实现密钥轮换机制
 * - 使用HSM存储主密钥
 * @author Apex Team
 * @since 1.2.0
 */
@Slf4j
@Component
public class EncryptionKeyProvider {

    private static final String ALGORITHM = "AES";
    private static final int KEY_SIZE = 256;

    private final SecretKey encryptionKey;

    /** 从 Base64 配置解析 AES-256 密钥，缺失或非法时抛出 {@link IllegalArgumentException}。 */
    public EncryptionKeyProvider(
            @Value("${security.encryption.key:${security.token.encryption.key:}}") String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalArgumentException("必须配置Token加密密钥");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Token加密密钥必须为有效Base64");
        }
        if (keyBytes.length != KEY_SIZE / Byte.SIZE) {
            throw new IllegalArgumentException("Token加密密钥必须为32字节");
        }
        this.encryptionKey = new SecretKeySpec(keyBytes, ALGORITHM);
        log.info("使用配置的加密密钥");
    }

    /**
     * 获取加密密钥
     */
    public SecretKey getEncryptionKey() {
        return encryptionKey;
    }

    /**
     * 导出密钥为Base64字符串（仅用于初始化配置）
     */
    public String exportKeyAsBase64() {
        return Base64.getEncoder().encodeToString(encryptionKey.getEncoded());
    }
}
