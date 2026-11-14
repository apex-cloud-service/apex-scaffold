package com.apex.scaffold.integration.device.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * 设备凭据哈希器（方案 M5 / 批次 E3）。
 *
 * <p>PBKDF2-SHA256（默认 12 万次迭代）对设备 secret 做单向哈希，存储格式：
 * {@code pbkdf2_sha256$<iterations>$<saltBase64>$<hashBase64>}。明文 secret 仅在
 * 注册响应出现一次，不落库、不落日志；MQTT 认证钩子以相同参数哈希入参后比对。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public final class SecretHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

    private static final int ITERATIONS = 120_000;

    private static final int SALT_BYTES = 16;

    private static final int KEY_BITS = 256;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private SecretHasher() {
        throw new UnsupportedOperationException("工具类不能实例化");
    }

    /**
     * 生成随机设备 secret（Base64URL 无填充，32 字节熵）。
     *
     * @return 明文 secret（仅创建响应出现一次）
     */
    public static String generateSecret() {
        byte[] secret = new byte[32];
        SECURE_RANDOM.nextBytes(secret);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
    }

    /**
     * 计算明文 secret 的哈希。
     *
     * @param rawSecret 明文 secret
     * @return 哈希存储串（含算法、迭代次数、盐）
     */
    public static String hash(String rawSecret) {
        byte[] salt = new byte[SALT_BYTES];
        SECURE_RANDOM.nextBytes(salt);
        byte[] derived = pbkdf2(rawSecret, salt, ITERATIONS);
        return "pbkdf2_sha256$" + ITERATIONS
                + "$" + Base64.getEncoder().encodeToString(salt)
                + "$" + Base64.getEncoder().encodeToString(derived);
    }

    /**
     * 校验明文 secret 是否匹配存储哈希。
     *
     * @param rawSecret   待校验明文
     * @param storedHash  存储哈希串
     * @return 是否匹配（存储格式非法时返回 false，认证失败不抛异常）
     */
    public static boolean matches(String rawSecret, String storedHash) {
        if (rawSecret == null || storedHash == null) {
            return false;
        }
        String[] parts = storedHash.split("\\$");
        if (parts.length != 4 || !"pbkdf2_sha256".equals(parts[0])) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = pbkdf2(rawSecret, salt, iterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] pbkdf2(String rawSecret, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(rawSecret.toCharArray(), salt, iterations, KEY_BITS);
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("设备凭据哈希失败", e);
        }
    }

    /** 供测试断言字符集一致性。 */
    static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
