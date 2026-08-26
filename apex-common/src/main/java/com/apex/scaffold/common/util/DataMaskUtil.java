package com.apex.scaffold.common.util;

/**
 * 数据脱敏工具类
 *
 * <p>提供常见的敏感信息脱敏方法,符合数据安全规范:
 * <ul>
 *   <li>手机号: 138****8000</li>
 *   <li>身份证: 110***********1234</li>
 *   <li>邮箱: t***@example.com</li>
 *   <li>姓名: 张*</li>
 *   <li>银行卡: 6222 **** **** 1234</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public final class DataMaskUtil {

    private DataMaskUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * 手机号脱敏
     * <p>格式: 138****8000 (保留前3位和后4位)
     *
     * @param phone 手机号
     * @return 脱敏后的手机号
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.isEmpty()) {
            return phone;
        }

        // 去除空格和特殊字符
        String cleaned = phone.replaceAll("[\\s-]", "");

        if (cleaned.length() != 11) {
            return phone; // 非标准手机号,返回原值
        }

        return cleaned.substring(0, 3) + "****" + cleaned.substring(7);
    }

    /**
     * 身份证号脱敏
     * <p>格式: 110***********1234 (保留前3位和后4位)
     *
     * @param idCard 身份证号
     * @return 脱敏后的身份证号
     */
    public static String maskIdCard(String idCard) {
        if (idCard == null || idCard.isEmpty()) {
            return idCard;
        }

        String cleaned = idCard.trim();

        if (cleaned.length() != 15 && cleaned.length() != 18) {
            return idCard; // 非标准身份证,返回原值
        }

        return cleaned.substring(0, 3) + "*".repeat(cleaned.length() - 7) + cleaned.substring(cleaned.length() - 4);
    }

    /**
     * 邮箱脱敏
     * <p>格式: t***@example.com (保留首字母和域名)
     *
     * @param email 邮箱地址
     * @return 脱敏后的邮箱
     */
    public static String maskEmail(String email) {
        if (email == null || email.isEmpty()) {
            return email;
        }

        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return email; // 无效邮箱,返回原值
        }

        String localPart = email.substring(0, atIndex);
        String domain = email.substring(atIndex);

        if (localPart.length() <= 2) {
            return localPart.charAt(0) + "*" + domain;
        }

        return localPart.charAt(0)
                + "*".repeat(localPart.length() - 2)
                + localPart.charAt(localPart.length() - 1)
                + domain;
    }

    /**
     * 姓名脱敏
     * <p>格式: 张* (保留姓氏)
     *
     * @param name 姓名
     * @return 脱敏后的姓名
     */
    public static String maskName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }

        String trimmed = name.trim();

        if (trimmed.length() == 1) {
            return trimmed; // 单字名,不脱敏
        }

        if (trimmed.length() == 2) {
            return trimmed.charAt(0) + "*";
        }

        // 3字及以上: 张** 或 欧阳**
        return trimmed.charAt(0) + "*".repeat(trimmed.length() - 1);
    }

    /**
     * 银行卡号脱敏
     * <p>格式: 6222 **** **** 1234 (保留前4位和后4位)
     *
     * @param cardNo 银行卡号
     * @return 脱敏后的银行卡号
     */
    public static String maskBankCard(String cardNo) {
        if (cardNo == null || cardNo.isEmpty()) {
            return cardNo;
        }

        String cleaned = cardNo.replaceAll("[\\s-]", "");

        if (cleaned.length() < 8) {
            return cardNo; // 太短,返回原值
        }

        // 每4位一组显示
        StringBuilder result = new StringBuilder();
        result.append(cleaned.substring(0, 4));
        result.append(" ");

        int middleLength = cleaned.length() - 8;
        int groups = middleLength / 4;
        for (int i = 0; i < groups; i++) {
            result.append("**** ");
        }

        if (middleLength % 4 != 0) {
            result.append("**** ");
        }

        result.append(cleaned.substring(cleaned.length() - 4));

        return result.toString().trim();
    }

    /**
     * 地址脱敏
     * <p>格式: 北京市朝阳区**** (保留到区县级)
     *
     * @param address 地址
     * @return 脱敏后的地址
     */
    public static String maskAddress(String address) {
        if (address == null || address.isEmpty()) {
            return address;
        }

        // 简单策略: 保留前50%字符
        int keepLength = address.length() / 2;
        if (keepLength < 10) {
            keepLength = 10;
        }

        return address.substring(0, keepLength) + "****";
    }

    /**
     * IP地址脱敏
     * <p>格式: 192.168.*.* (保留前两段)
     *
     * @param ipAddress IP地址
     * @return 脱敏后的IP
     */
    public static String maskIpAddress(String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty()) {
            return ipAddress;
        }

        String[] parts = ipAddress.split("\\.");
        if (parts.length != 4) {
            return ipAddress; // 无效IP,返回原值
        }

        return parts[0] + "." + parts[1] + ".*.*";
    }

    /**
     * URL脱敏
     * <p>格式: https://example.com/path/**** (隐藏查询参数)
     *
     * @param url URL地址
     * @return 脱敏后的URL
     */
    public static String maskUrl(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }

        int questionMarkIndex = url.indexOf('?');
        if (questionMarkIndex == -1) {
            return url; // 无参数,返回原值
        }

        return url.substring(0, questionMarkIndex) + "?****";
    }

    /**
     * 通用字符串脱敏
     * <p>保留前30%和后20%,中间用*替代
     *
     * @param value 原始字符串
     * @return 脱敏后的字符串
     */
    public static String maskGeneral(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        int length = value.length();
        if (length <= 4) {
            return "*".repeat(length); // 太短,全部脱敏
        }

        int keepStart = Math.max(1, length * 30 / 100);
        int keepEnd = Math.max(1, length * 20 / 100);
        int maskLength = length - keepStart - keepEnd;

        return value.substring(0, keepStart) + "*".repeat(maskLength) + value.substring(length - keepEnd);
    }
}
