package com.apex.scaffold.infrastructure.encryption;

import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import lombok.Getter;
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

    @Getter
    private final SecretKey encryptionKey;

    /**
     * 将配置解析为 AES-256 密钥，在实例生命周期内复用，不生成随机替代值。
     *
     * @param base64Key Base64 编码的 32 字节密钥，优先读取通用加密配置，其次读取 Token 加密配置
     * @throws IllegalArgumentException 配置缺失、为空白、Base64 非法或解码长度不是 32 字节
     */
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
     * 将当前密钥原始字节导出为 Base64，编码不提供保密性。
     *
     * @return 包含完整密钥材料的敏感字符串，调用方不得写入日志或公开响应
     */
    public String exportKeyAsBase64() {
        return Base64.getEncoder().encodeToString(encryptionKey.getEncoded());
    }
}
