package com.apex.scaffold.common.util;

import java.security.SecureRandom;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码工具类
 *
 * <p>提供密码的加密和验证功能
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public class PasswordUtil {

    /** 私有构造函数，防止实例化 */
    private PasswordUtil() {
        throw new UnsupportedOperationException("工具类不能实例化");
    }

    /** BCrypt 密码编码器实例 */
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    /** 密码学安全的随机数生成器，用于生成不可预测的随机密码 */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** 密码必须包含的字符类别数量（大写、小写、数字、特殊字符） */
    private static final int PASSWORD_CHAR_CATEGORY_COUNT = 4;

    private static final String UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWERCASE = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGITS = "0123456789";
    private static final String SPECIAL = "!@#$%^&*()_+";
    private static final String ALL_CHARS = UPPERCASE + LOWERCASE + DIGITS + SPECIAL;

    public static boolean isBcryptHash(String value) {
        return value != null && value.matches("\\$2[aby]\\$(0[4-9]|[12][0-9]|3[01])\\$[./A-Za-z0-9]{53}");
    }

    /**
     * 校验密码字段必须是 BCrypt 哈希。
     *
     * @param value 待校验的密码字段；null 或空串跳过校验
     * @throws IllegalArgumentException 非 BCrypt 哈希格式时抛出
     */
    public static void requirePasswordHash(String value) {
        if (value != null && !value.isEmpty() && !isBcryptHash(value)) {
            throw new IllegalArgumentException("密码持久化仅接受BCrypt哈希");
        }
    }

    /**
     * 加密密码
     *
     * @param password 原始密码
     * @return 加密后的密码
     * @throws IllegalArgumentException 如果 password 为 null
     */
    public static String encryptPassword(String password) {
        if (password == null) {
            throw new IllegalArgumentException("password 不能为 null");
        }
        return PASSWORD_ENCODER.encode(password);
    }

    /**
     * 验证密码
     *
     * @param rawPassword 原始密码
     * @param encodedPassword 加密后的密码
     * @return 是否匹配
     * @throws IllegalArgumentException 如果 rawPassword 或 encodedPassword 为 null
     */
    public static boolean verifyPassword(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            throw new IllegalArgumentException("rawPassword 和 encodedPassword 不能为 null");
        }
        return PASSWORD_ENCODER.matches(rawPassword, encodedPassword);
    }

    /**
     * 生成随机密码
     *
     * <p>当 {@code length >= 4} 时，保证生成的密码至少包含一个大写字母、一个小写字母、一个数字和一个特殊字符。
     *
     * @param length 密码长度
     * @return 随机密码，长度小于等于0时返回空字符串
     */
    public static String generateRandomPassword(int length) {
        if (length <= 0) {
            return "";
        }
        // 从每个类别中至少选取一个字符
        StringBuilder password = new StringBuilder();
        if (length >= PASSWORD_CHAR_CATEGORY_COUNT) {
            password.append(UPPERCASE.charAt(SECURE_RANDOM.nextInt(UPPERCASE.length())));
            password.append(LOWERCASE.charAt(SECURE_RANDOM.nextInt(LOWERCASE.length())));
            password.append(DIGITS.charAt(SECURE_RANDOM.nextInt(DIGITS.length())));
            password.append(SPECIAL.charAt(SECURE_RANDOM.nextInt(SPECIAL.length())));
            for (int i = PASSWORD_CHAR_CATEGORY_COUNT; i < length; i++) {
                password.append(ALL_CHARS.charAt(SECURE_RANDOM.nextInt(ALL_CHARS.length())));
            }
        } else {
            for (int i = 0; i < length; i++) {
                password.append(ALL_CHARS.charAt(SECURE_RANDOM.nextInt(ALL_CHARS.length())));
            }
        }
        // 打乱顺序，避免前 4 位固定格式
        char[] chars = password.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = SECURE_RANDOM.nextInt(i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }
        return new String(chars);
    }
}
