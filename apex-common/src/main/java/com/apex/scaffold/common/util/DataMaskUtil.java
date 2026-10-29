package com.apex.scaffold.common.util;

import com.apex.scaffold.common.annotation.MaskType;

/**
 * 统一本地脱敏规则引擎。保留长度按 Unicode 码点计算，非法格式与短输入采用全遮盖。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.0.0
 */
public final class DataMaskUtil {
    /** 自定义规则仅允许两段不超过四位的非负长度。 */
    private static final java.util.regex.Pattern CUSTOM_RULE_PATTERN =
            java.util.regex.Pattern.compile("[0-9]{1,4},[0-9]{1,4}");

    /** 邮箱只接受单一本地部分与常规 ASCII 域名。 */
    private static final java.util.regex.Pattern EMAIL_PATTERN =
            java.util.regex.Pattern.compile("[^\\s@]+@[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?\\.[A-Za-z]{2,}");

    /** IPv4 的固定分段数量。 */
    private static final int IPV4_SEGMENTS = 4;

    /** 禁止实例化工具类。 */
    private DataMaskUtil() {
        throw new UnsupportedOperationException("工具类不能实例化");
    }

    /**
     * 执行指定类型规则；自定义规则格式为前缀长度,后缀长度。
     * @param value 原值
     * @param type 规则类型
     * @param customRule 自定义保留区间
     * @return 脱敏结果
     */
    public static String mask(String value, MaskType type, String customRule) {
        if (type == null) {
            throw new IllegalArgumentException("脱敏类型不能为空");
        }
        return switch (type) {
            case PHONE -> maskPhone(value);
            case ID_CARD -> maskIdCard(value);
            case EMAIL -> maskEmail(value);
            case NAME -> maskName(value);
            case BANK_CARD -> maskBankCard(value);
            case ADDRESS -> maskAddress(value);
            case IP_ADDRESS -> maskIpAddress(value);
            case URL -> maskUrl(value);
            case GENERAL -> maskGeneral(value);
            case CUSTOM -> {
                int[] retained = parseCustomRule(customRule);
                yield maskRange(value, retained[0], retained[1]);
            }
        };
    }

    /**
     * 校验自定义规则，异常消息不携带原始配置。
     * @param rule 前缀,后缀
     * @return 两个保留长度
     */
    public static int[] parseCustomRule(String rule) {
        if (rule == null || !CUSTOM_RULE_PATTERN.matcher(rule).matches()) {
            throw new IllegalArgumentException("自定义规则必须为非负前缀,后缀长度");
        }
        String[] parts = rule.split(",");
        return new int[] {Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
    }

    /**
     * 保留首尾码点；首尾重叠或无可遮盖区域时全遮盖，避免短值泄漏。
     * @param value 原值
     * @param prefix 前缀长度
     * @param suffix 后缀长度
     * @return 脱敏结果
     */
    public static String maskRange(String value, int prefix, int suffix) {
        if (prefix < 0 || suffix < 0) {
            throw new IllegalArgumentException("保留长度不能为负数");
        }
        if (value == null || value.isEmpty()) {
            return value;
        }
        int length = value.codePointCount(0, value.length());
        if ((long) prefix + suffix >= length) {
            return "*".repeat(length);
        }
        int start = value.offsetByCodePoints(0, prefix);
        int end = value.offsetByCodePoints(0, length - suffix);
        return value.substring(0, start) + "*".repeat(length - prefix - suffix) + value.substring(end);
    }

    /**
     * 手机号脱敏，合法十一位数字保留前三后四。
     * @param phone 手机号
     * @return 脱敏结果
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.isEmpty()) {
            return phone;
        }
        String cleaned = phone.replaceAll("[\\s-]", "");
        return cleaned.matches("1[0-9]{10}") ? maskRange(cleaned, 3, 4) : maskRange(phone, 0, 0);
    }

    /**
     * 证件号脱敏，支持十五位数字和十八位数字末位 X。
     * @param idCard 证件号
     * @return 脱敏结果
     */
    public static String maskIdCard(String idCard) {
        if (idCard == null || idCard.isEmpty()) {
            return idCard;
        }
        String cleaned = idCard.trim();
        return cleaned.matches("(?:[0-9]{15}|[0-9]{17}[0-9Xx])") ? maskRange(cleaned, 3, 4) : maskRange(idCard, 0, 0);
    }

    /**
     * 邮箱脱敏，保留合法域名与多字符本地部分的首码点。
     * @param email 邮箱
     * @return 脱敏结果
     */
    public static String maskEmail(String email) {
        if (email == null || email.isEmpty()) {
            return email;
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            return maskRange(email, 0, 0);
        }
        int at = email.indexOf('@');
        return maskRange(email.substring(0, at), 1, 0) + email.substring(at);
    }

    /**
     * 姓名脱敏，单字符全遮盖。
     * @param name 姓名
     * @return 脱敏结果
     */
    public static String maskName(String name) {
        return maskRange(name == null ? null : name.trim(), 1, 0);
    }

    /**
     * 银行卡脱敏，九至十九位数字保留前四后四，不进行卡号有效性认证。
     * @param cardNo 卡号
     * @return 脱敏结果
     */
    public static String maskBankCard(String cardNo) {
        if (cardNo == null || cardNo.isEmpty()) {
            return cardNo;
        }
        String cleaned = cardNo.replaceAll("[\\s-]", "");
        return cleaned.matches("[0-9]{9,19}") ? maskRange(cleaned, 4, 4) : maskRange(cardNo, 0, 0);
    }

    /**
     * 地址按码点保留前半部分，不推断行政区划。
     * @param address 地址
     * @return 脱敏结果
     */
    public static String maskAddress(String address) {
        return address == null ? null : maskRange(address, address.codePointCount(0, address.length()) / 2, 0);
    }

    /**
     * IPv4 保留前两段，非法格式全遮盖。
     * @param ipAddress 地址
     * @return 脱敏结果
     */
    public static String maskIpAddress(String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty()) {
            return ipAddress;
        }
        String[] parts = ipAddress.split("\\.", -1);
        if (parts.length != IPV4_SEGMENTS) {
            return maskRange(ipAddress, 0, 0);
        }
        for (String part : parts) {
            if (!part.matches("[0-9]{1,3}") || Integer.parseInt(part) > 255) {
                return maskRange(ipAddress, 0, 0);
            }
        }
        return parts[0] + "." + parts[1] + ".*.*";
    }

    /**
     * URL 遮盖查询与片段，用户信息存在时全遮盖。
     * @param url 地址
     * @return 脱敏结果
     */
    public static String maskUrl(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        try {
            java.net.URI uri = java.net.URI.create(url);
            if (uri.getHost() == null || uri.getUserInfo() != null) {
                return maskRange(url, 0, 0);
            }
            int query = url.indexOf('?');
            int fragment = url.indexOf('#');
            int end = query < 0 ? fragment : fragment < 0 ? query : Math.min(query, fragment);
            return end < 0 ? url : url.substring(0, end) + "?****";
        } catch (IllegalArgumentException ignored) {
            return maskRange(url, 0, 0);
        }
    }

    /**
     * 通用脱敏保留前百分之三十、后百分之二十，四码点及以下全遮盖。
     * @param value 原值
     * @return 脱敏结果
     */
    public static String maskGeneral(String value) {
        if (value == null) {
            return null;
        }
        int length = value.codePointCount(0, value.length());
        return length <= 4 ? maskRange(value, 0, 0) : maskRange(value, length * 30 / 100, length * 20 / 100);
    }
}
