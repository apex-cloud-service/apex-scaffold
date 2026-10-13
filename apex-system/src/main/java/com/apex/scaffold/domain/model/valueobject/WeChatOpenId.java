package com.apex.scaffold.domain.model.valueobject;

import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * 微信OpenID值对象。
 *
 * <p>封装微信OpenID的校验和处理逻辑。
 * OpenID是用户在微信开放平台的唯一标识，同一用户在不同应用下的OpenID不同。</p>
 *
 * <h2>业务规则</h2>
 * <ul>
 *   <li>OpenID不能为空</li>
 *   <li>OpenID应为32位长度的字符串（微信官方规范）</li>
 *   <li>OpenID由字母和数字组成</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code * // 创建OpenID
 * WeChatOpenId openId = WeChatOpenId.of("o1234567890abcdef1234567890abcdef");
 *
 * // 验证OpenID格式
 * boolean valid = WeChatOpenId.isValid("o1234567890abcdef1234567890abcdef");
 *
 * // 获取原始值
 * String value = openId.getValue();
 * }</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Getter
@EqualsAndHashCode
public class WeChatOpenId {

    /** OpenID值 */
    private final String value;

    /**
     * OpenID最小长度
     */
    private static final int MIN_LENGTH = 20;

    /**
     * OpenID最大长度
     */
    private static final int MAX_LENGTH = 64;

    /**
     * OpenID格式正则表达式
     */
    private static final String PATTERN = "^[a-zA-Z0-9_-]{20,64}$";

    /**
     * 脱敏最小长度阈值
     */
    private static final int MASK_MIN_LENGTH = 12;

    /**
     * 私有构造函数，禁止直接创建。
     *
     * @param value OpenID值
     */
    private WeChatOpenId(String value) {
        this.value = value;
    }

    /**
     * 创建微信OpenID值对象。
     *
     * @param openId OpenID字符串
     * @return WeChatOpenId值对象
     * @throws IllegalArgumentException 如果OpenID格式无效
     */
    public static WeChatOpenId of(String openId) {
        if (openId == null || openId.isBlank()) {
            throw new IllegalArgumentException("OpenID不能为空");
        }

        String trimmed = openId.trim();

        if (!isValidFormat(trimmed)) {
            throw new IllegalArgumentException("无效的OpenID格式: " + openId);
        }

        return new WeChatOpenId(trimmed);
    }

    /**
     * 安全创建OpenID值对象，不抛出异常。
     *
     * @param openId OpenID字符串
     * @return WeChatOpenId值对象，如果格式无效返回null
     */
    public static WeChatOpenId ofNullable(String openId) {
        try {
            return of(openId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 验证OpenID格式是否有效。
     *
     * @param openId OpenID字符串
     * @return true-有效，false-无效
     */
    public static boolean isValid(String openId) {
        if (openId == null || openId.isBlank()) {
            return false;
        }
        return isValidFormat(openId.trim());
    }

    /**
     * 验证OpenID格式。
     *
     * @param openId OpenID字符串
     * @return true-有效，false-无效
     */
    private static boolean isValidFormat(String openId) {
        return openId.length() >= MIN_LENGTH && openId.length() <= MAX_LENGTH && openId.matches(PATTERN);
    }

    /**
     * 获取脱敏后的OpenID。
     *
     * @return 脱敏后的OpenID（如：o1234567890**********ef）
     */
    public String getMasked() {
        if (value.length() <= MASK_MIN_LENGTH) {
            return "****";
        }
        return value.substring(0, 11) + "*".repeat(10) + value.substring(value.length() - 2);
    }

    /**
     * 判断是否为空。
     *
     * @return true-为空，false-不为空
     */
    public boolean isEmpty() {
        return value == null || value.isBlank();
    }

    /**
     * 返回脱敏后的微信 OpenID 字符串。
     *
     * @return 脱敏 OpenID
     */
    @Override
    public String toString() {
        return getMasked();
    }
}
