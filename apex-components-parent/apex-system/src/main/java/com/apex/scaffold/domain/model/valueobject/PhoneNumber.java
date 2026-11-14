package com.apex.scaffold.domain.model.valueobject;

import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * 手机号值对象。
 *
 * <p>封装手机号的校验逻辑，确保手机号格式的有效性。
 * 值对象具有不可变性，一旦创建其值就不能改变。</p>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>手机号必须符合中国大陆手机号格式（11位数字）</li>
 *   <li>支持带+86前缀的手机号</li>
 *   <li>支持带空格或短横线分隔的手机号</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code * // 创建手机号
 * PhoneNumber phone = PhoneNumber.of("13812345678");
 *
 * // 获取格式化后的手机号
 * String formatted = phone.getFormatted();  // "138****5678"
 *
 * // 验证手机号
 * boolean valid = PhoneNumber.isValid("13812345678");
 * }</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Getter
@EqualsAndHashCode
public class PhoneNumber {

    /** 中国大陆手机号长度 */
    private static final int CHINA_PHONE_LENGTH = 11;

    /** 中国国家码前缀 */
    private static final String COUNTRY_CODE_PREFIX = "86";

    /** 脱敏掩码 */
    private static final String MASK = "****";

    /** 原始手机号 */
    private final String value;

    /** 纯数字格式的手机号 */
    private final String normalized;

    /**
     * 私有构造函数，禁止直接创建。
     *
     * @param value      原始手机号
     * @param normalized 标准化后的纯数字手机号
     */
    private PhoneNumber(String value, String normalized) {
        this.value = value;
        this.normalized = normalized;
    }

    /**
     * 创建手机号值对象。
     *
     * @param phoneNumber 手机号字符串
     * @return PhoneNumber值对象
     * @throws IllegalArgumentException 如果手机号格式无效
     */
    public static PhoneNumber of(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new IllegalArgumentException("手机号不能为空");
        }

        String normalized = normalize(phoneNumber);
        if (!isValidFormat(normalized)) {
            throw new IllegalArgumentException("无效的手机号格式: " + phoneNumber);
        }

        return new PhoneNumber(phoneNumber, normalized);
    }

    /**
     * 安全创建手机号值对象，不抛出异常。
     *
     * @param phoneNumber 手机号字符串
     * @return PhoneNumber值对象，如果格式无效返回null
     */
    public static PhoneNumber ofNullable(String phoneNumber) {
        try {
            return of(phoneNumber);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 验证手机号格式是否有效。
     *
     * @param phoneNumber 手机号字符串
     * @return true-有效，false-无效
     */
    public static boolean isValid(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return false;
        }
        String normalized = normalize(phoneNumber);
        return isValidFormat(normalized);
    }

    /**
     * 标准化手机号，移除所有非数字字符和前缀。
     *
     * @param phoneNumber 原始手机号
     * @return 标准化后的纯数字手机号
     */
    private static String normalize(String phoneNumber) {
        // 移除所有非数字字符
        String normalized = phoneNumber.replaceAll("[^\\d]", "");

        // 移除国家码前缀
        if (normalized.startsWith(COUNTRY_CODE_PREFIX)) {
            normalized = normalized.substring(COUNTRY_CODE_PREFIX.length());
        }

        return normalized;
    }

    /**
     * 验证标准化后的手机号格式。
     *
     * @param normalized 标准化后的手机号
     * @return true-有效，false-无效
     */
    private static boolean isValidFormat(String normalized) {
        // 中国大陆手机号规则：11位数字，以1开头
        return normalized.length() == CHINA_PHONE_LENGTH && normalized.startsWith("1");
    }

    /**
     * 获取脱敏后的手机号（如：138****5678）。
     *
     * @return 脱敏后的手机号
     */
    public String getMasked() {
        if (normalized.length() == CHINA_PHONE_LENGTH) {
            return normalized.substring(0, 3) + MASK + normalized.substring(7);
        }
        return MASK;
    }

    /**
     * 获取带格式的手机号（如：138-1234-5678）。
     *
     * @return 带格式的手机号
     */
    public String getFormatted() {
        if (normalized.length() == CHINA_PHONE_LENGTH) {
            return normalized.substring(0, 3) + "-" + normalized.substring(3, 7) + "-" + normalized.substring(7);
        }
        return normalized;
    }

    /**
     * 获取带国家码的手机号（如：+86 13812345678）。
     *
     * @return 带国家码的手机号
     */
    public String getWithCountryCode() {
        return "+86 " + normalized;
    }

    /**
     * 判断是否为同一手机号。
     *
     * @param other 另一个手机号
     * @return true-相同，false-不同
     */
    public boolean equalsPhone(PhoneNumber other) {
        if (other == null) {
            return false;
        }
        return this.normalized.equals(other.normalized);
    }

    /**
     * 返回脱敏后的手机号码字符串。
     *
     * @return 脱敏手机号
     */
    @Override
    public String toString() {
        return getMasked();
    }
}
