package com.apex.scaffold.infrastructure.encryption;

import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import javax.crypto.KeyGenerator;
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

    public EncryptionKeyProvider(@Value("${security.encryption.key:}") String base64Key) {
        if (base64Key != null && !base64Key.isEmpty()) {
            // 从配置加载密钥（生产环境应从KMS获取）
            byte[] keyBytes = Base64.getDecoder().decode(base64Key);
            this.encryptionKey = new SecretKeySpec(keyBytes, ALGORITHM);
            log.info("使用配置的加密密钥");
        } else {
            // 开发环境：生成临时密钥
            log.warn("未配置加密密钥，生成临时密钥（仅用于开发环境）");
            this.encryptionKey = generateKey();
        }
    }

    /**
     * 获取加密密钥
     */
    public SecretKey getEncryptionKey() {
        return encryptionKey;
    }

    /**
     * 生成新的AES-256密钥
     * 仅用于开发环境
     */
    private SecretKey generateKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
            keyGen.init(KEY_SIZE);
            return keyGen.generateKey();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("无法生成加密密钥", e);
        }
    }

    /**
     * 导出密钥为Base64字符串（仅用于初始化配置）
     */
    public String exportKeyAsBase64() {
        return Base64.getEncoder().encodeToString(encryptionKey.getEncoded());
    }
}
