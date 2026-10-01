package com.apex.scaffold.infrastructure.office.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * 基于 HMAC-SHA256 的办公平台回调签名校验实现。
 * @author Apex Team
 * @since 1.16.0
 */
@Component
public class HmacOfficeSignatureVerifier implements OfficeSignatureVerifier {

    private static final int HEX_RADIX = 2;

    /**
     * 校验规范化回调内容的 HMAC 签名。
     * @param timestamp 时间戳
     * @param nonce 随机数
     * @param body 请求体
     * @param signature 期望签名
     * @param secret 签名密钥
     * @return 是否有效
     */
    @Override
    public boolean verify(String timestamp, String nonce, String body, String signature, String secret) {
        if (timestamp == null || nonce == null || body == null || signature == null || secret == null) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] actual = mac.doFinal((timestamp + "\n" + nonce + "\n" + body).getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(actual, hex(signature));
        } catch (Exception exception) {
            return false;
        }
    }

    private byte[] hex(String value) {
        if (value.length() % HEX_RADIX != 0) {
            return new byte[0];
        }
        byte[] result = new byte[value.length() / HEX_RADIX];
        for (int index = 0; index < result.length; index++) {
            result[index] =
                    (byte) Integer.parseInt(value.substring(index * HEX_RADIX, index * HEX_RADIX + HEX_RADIX), 16);
        }
        return result;
    }
}
