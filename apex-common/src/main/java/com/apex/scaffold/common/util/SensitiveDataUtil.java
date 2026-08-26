package com.apex.scaffold.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 敏感数据日志脱敏工具。
 *
 * <p>该工具仅生成可关联但不可还原的日志标识，禁止将返回值用于认证、加密或业务主键。
 * 手机号保留前三后四位，其余标识使用 SHA-256 指纹的前十二个十六进制字符。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.2.0
 */
public final class SensitiveDataUtil {

    /** 日志指纹展示的十六进制字符数。 */
    private static final int FINGERPRINT_LENGTH = 12;

    /** 可安全保留前三后四位的最短长度。 */
    private static final int MIN_MASKABLE_LENGTH = 7;

    /** 手机号脱敏保留的前缀长度。 */
    private static final int PHONE_PREFIX_LENGTH = 3;

    /** 手机号脱敏保留的后缀长度。 */
    private static final int PHONE_SUFFIX_LENGTH = 4;

    /**
     * 禁止实例化工具类。
     *
     */
    private SensitiveDataUtil() {
        throw new UnsupportedOperationException("工具类不能实例化");
    }

    /**
     * 脱敏手机号。
     *
     * @param phone 原始手机号
     * @return 脱敏后的手机号；空值返回 {@code ***}
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return "***";
        }
        String normalized = phone.trim();
        if (normalized.length() < MIN_MASKABLE_LENGTH) {
            return "***";
        }
        return normalized.substring(0, PHONE_PREFIX_LENGTH)
                + "****"
                + normalized.substring(normalized.length() - PHONE_SUFFIX_LENGTH);
    }

    /**
     * 为 Token、OpenID 等标识生成不可逆日志指纹。
     *
     * @param value 原始敏感标识
     * @return 带 {@code sha256:} 前缀的短指纹；空值返回 {@code sha256:empty}
     */
    public static String fingerprint(String value) {
        if (value == null || value.isBlank()) {
            return "sha256:empty";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(hash).substring(0, FINGERPRINT_LENGTH);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("JDK 缺少 SHA-256 算法", error);
        }
    }
}
