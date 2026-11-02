package com.apex.scaffold.integration.connector.domain.connector;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * webhook 签名工具（方案 M5 / 批次 E2）。
 *
 * <p>出站 {@code X-Signature} 与入站校验共用同一算法：HMAC-SHA256(secret, timestamp + "." + body)
 * 的十六进制小写；比较使用常量时间 {@link MessageDigest#isEqual} 防时序侧信道。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public final class WebhookSignatures {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private WebhookSignatures() {
        throw new UnsupportedOperationException("工具类不能实例化");
    }

    /**
     * 计算 HMAC-SHA256 签名（十六进制小写）。
     *
     * @param secret 签名密钥
     * @param data   签名原文
     * @return 十六进制签名
     */
    public static String hmacSha256Hex(String secret, String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return hex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 签名计算失败", e);
        }
    }

    /**
     * 常量时间校验签名。
     *
     * @param secret     签名密钥
     * @param data       签名原文
     * @param signature  投递方签名（十六进制，大小写不敏感）
     * @return true-签名一致
     */
    public static boolean verify(String secret, String data, String signature) {
        if (secret == null || data == null || signature == null || signature.isBlank()) {
            return false;
        }
        try {
            byte[] expected = hmacSha256Hex(secret, data).getBytes(StandardCharsets.US_ASCII);
            byte[] actual = signature.trim().toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 计算 SHA-256 摘要（十六进制小写），用于去重键。
     *
     * @param data 原文
     * @return 十六进制摘要
     */
    public static String sha256Hex(String data) {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 摘要计算失败", e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return builder.toString();
    }
}
