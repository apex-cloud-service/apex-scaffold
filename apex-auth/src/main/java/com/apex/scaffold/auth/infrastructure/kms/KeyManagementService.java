package com.apex.scaffold.auth.infrastructure.kms;

import reactor.core.publisher.Mono;

/**
 * 密钥管理服务接口
 *
 * 支持多种密钥管理系统：
 * - AWS KMS (Key Management Service)
 * - HashiCorp Vault
 * - Azure Key Vault
 * - 本地密钥管理
 * @author Apex Team
 * @since 1.2.0
 */
public interface KeyManagementService {

    /**
     * 加密数据
     *
     * @param plaintext 明文数据
     * @param keyId 密钥ID
     * @return 加密后的密文（Base64编码）
     */
    Mono<String> encrypt(String plaintext, String keyId);

    /**
     * 解密数据
     *
     * @param ciphertext 密文数据（Base64编码）
     * @param keyId 密钥ID
     * @return 解密后的明文
     */
    Mono<String> decrypt(String ciphertext, String keyId);

    /**
     * 生成数据密钥
     *
     * @param keyId 主密钥ID
     * @return 数据密钥对（明文密钥 + 加密后的密钥）
     */
    Mono<DataKey> generateDataKey(String keyId);

    /**
     * 轮换密钥
     *
     * @param keyId 密钥ID
     * @return 新密钥ID
     */
    Mono<String> rotateKey(String keyId);

    /**
     * 获取密钥元数据
     *
     * @param keyId 密钥ID
     * @return 密钥元数据
     */
    Mono<KeyMetadata> getKeyMetadata(String keyId);

    /**
     * 禁用密钥
     *
     * @param keyId 密钥ID
     * @return 是否成功
     */
    Mono<Boolean> disableKey(String keyId);

    /**
     * 启用密钥
     *
     * @param keyId 密钥ID
     * @return 是否成功
     */
    Mono<Boolean> enableKey(String keyId);

    /**
     * 数据密钥对
     */
    interface DataKey {
        /**
         * 获取明文密钥（用于加密数据）
         *
         * @return 明文数据密钥
         */
        String getPlaintextKey();

        /**
         * 获取加密后的密钥（用于存储）
         *
         * @return 经 KMS 封装的密文数据密钥
         */
        String getEncryptedKey();
    }

    /**
     * 密钥元数据
     */
    interface KeyMetadata {
        /**
         * 获取密钥ID
         *
         * @return KMS 密钥唯一标识
         */
        String getKeyId();

        /**
         * 获取密钥 ARN
         *
         * @return KMS 云端资源标识
         */
        String getKeyArn();

        /**
         * 获取密钥算法
         *
         * @return 算法标识（如 AES_256）
         */
        String getAlgorithm();

        /**
         * 获取密钥状态
         *
         * @return 状态标识（如 ENABLED/DISABLED）
         */
        String getKeyState();

        /**
         * 获取创建时间
         *
         * @return 密钥创建时间戳
         */
        java.time.Instant getCreationDate();

        /**
         * 检查密钥是否启用
         *
         * @return true 表示可用
         */
        boolean isEnabled();
    }
}
