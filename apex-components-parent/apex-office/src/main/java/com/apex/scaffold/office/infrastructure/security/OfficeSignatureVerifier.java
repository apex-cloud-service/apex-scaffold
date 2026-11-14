package com.apex.scaffold.office.infrastructure.security;

/**
 * 办公平台回调签名校验端口。
 * @author Apex Team
 * @since 1.2.0
 */
public interface OfficeSignatureVerifier {
    /**
     * 使用 HMAC-SHA256 校验回调签名。
     * @param timestamp 时间戳
     * @param nonce 随机数
     * @param body 请求体
     * @param signature 期望签名
     * @param secret 签名密钥
     * @return 是否有效
     */
    boolean verify(String timestamp, String nonce, String body, String signature, String secret);
}
