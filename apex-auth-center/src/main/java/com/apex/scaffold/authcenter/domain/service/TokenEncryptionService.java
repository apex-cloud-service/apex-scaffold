package com.apex.scaffold.authcenter.domain.service;

/**
 * Token加密服务（领域端口）
 *
 * <p>使用AES-256-GCM加密Token，提供高强度加密保护。
 * 领域层仅定义端口，具体实现由基础设施层提供。
 *
 * <p>安全特性：
 * <ul>
 *   <li>AES-256-GCM认证加密模式</li>
 *   <li>每次加密使用唯一的IV</li>
 *   <li>SHA-256哈希用于快速查找</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.2.0
 */
public interface TokenEncryptionService {

    /**
     * 计算Token的SHA-256哈希
     * 用于数据库索引和快速查找，不可逆
     *
     * @param token 原始Token
     * @return Base64编码的SHA-256哈希值
     */
    String hashToken(String token);

    /**
     * 加密Token
     * 使用AES-256-GCM模式，提供机密性和完整性保护
     *
     * @param token 原始Token
     * @return EncryptedToken对象，包含加密数据和IV
     */
    EncryptedToken encrypt(String token);

    /**
     * 解密Token
     *
     * @param encryptedData Base64编码的加密数据
     * @param ivBase64 Base64编码的IV
     * @return 解密后的原始Token
     */
    String decrypt(String encryptedData, String ivBase64);

    /**
     * 加密Token并返回可存储的字符串格式
     *
     * <p>将IV和加密数据以 "iv:encryptedData" 格式拼接，
     * 便于单字段存储，解密时自动解析。
     *
     * @param token 原始Token
     * @return "iv:encryptedData" 格式的字符串
     */
    String encryptForStorage(String token);

    /**
     * 从存储格式解密Token
     *
     * <p>解析 "iv:encryptedData" 格式并解密。
     *
     * @param storedData "iv:encryptedData" 格式的字符串
     * @return 解密后的原始Token
     */
    String decrypt(String storedData);

    /**
     * 加密Token数据结构
     *
     * @param encryptedData Base64编码的加密数据
     * @param iv Base64编码的初始化向量
     */
    record EncryptedToken(String encryptedData, String iv) {}
}
