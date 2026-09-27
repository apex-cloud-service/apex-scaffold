package com.apex.scaffold.common.security;

import reactor.core.publisher.Mono;

/**
 * 加密服务接口
 * 
 * 提供数据加密和解密功能
 * @author Apex Team
 * @since 1.2.0
 */
public interface EncryptionService {

    /**
     * 加密数据
     * 
     * @param plaintext 明文
     * @return 密文
     */
    Mono<String> encrypt(String plaintext);

    /**
     * 解密数据
     * 
     * @param ciphertext 密文
     * @return 明文
     */
    Mono<String> decrypt(String ciphertext);

    /**
     * 计算哈希值
     * 
     * @param data 数据
     * @return 哈希值
     */
    String hash(String data);
}
