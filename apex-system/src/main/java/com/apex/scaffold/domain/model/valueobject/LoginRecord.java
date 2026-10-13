package com.apex.scaffold.domain.model.valueobject;

import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * 登录记录值对象。
 *
 * <p>封装用户登录记录信息，包括登录时间和登录IP地址。
 * 值对象具有不可变性，一旦创建其值就不能改变。</p>
 *
 * <h2>业务规则</h2>
 * <ul>
 *   <li>登录时间不能为空</li>
 *   <li>登录IP地址可以为空（内网登录场景）</li>
 *   <li>IP地址格式应符合IPv4或IPv6规范</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code * // 创建登录记录
 * LoginRecord login = LoginRecord.of(LocalDateTime.now(), "192.168.1.1");
 *
 * // 获取登录时间
 * LocalDateTime time = login.getLoginTime();
 *
 * // 获取登录IP
 * String ip = login.getLoginIp();
 *
 * // 判断是否为同一天登录
 * boolean sameDay = login.isSameDay(anotherLogin);
 * }</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Getter
@EqualsAndHashCode
public class LoginRecord {

    /** 登录时间 */
    private final LocalDateTime loginTime;

    /** 登录IP地址 */
    private final String loginIp;

    /**
     * 私有构造函数，禁止直接创建。
     *
     * @param loginTime 登录时间
     * @param loginIp   登录IP地址
     */
    private LoginRecord(LocalDateTime loginTime, String loginIp) {
        this.loginTime = loginTime;
        this.loginIp = loginIp;
    }

    /**
     * 创建登录记录值对象。
     *
     * @param loginTime 登录时间
     * @param loginIp   登录IP地址（可为空）
     * @return LoginRecord值对象
     * @throws IllegalArgumentException 如果登录时间为空
     */
    public static LoginRecord of(LocalDateTime loginTime, String loginIp) {
        if (loginTime == null) {
            throw new IllegalArgumentException("登录时间不能为空");
        }

        String normalizedIp = loginIp == null || loginIp.isBlank() ? null : loginIp.trim();

        // 如果IP地址不为空，验证格式
        if (normalizedIp != null && !isValidIp(normalizedIp)) {
            throw new IllegalArgumentException("无效的IP地址格式: " + loginIp);
        }

        return new LoginRecord(loginTime, normalizedIp);
    }

    /**
     * 创建当前时间的登录记录。
     *
     * @param loginIp 登录IP地址
     * @return LoginRecord值对象
     */
    public static LoginRecord now(String loginIp) {
        return of(LocalDateTime.now(), loginIp);
    }

    /**
     * 验证IP地址格式是否有效。
     *
     * @param ip IP地址字符串
     * @return true-有效，false-无效
     */
    private static boolean isValidIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return true;
        }

        // IPv4格式验证
        String ipv4Pattern = "^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$";

        // IPv6格式验证（简化版）
        String ipv6Pattern = "^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$";

        // IPv6简写格式验证
        String ipv6ShortPattern = "^([0-9a-fA-F]{1,4}:){1,7}:$";

        return ip.matches(ipv4Pattern) || ip.matches(ipv6Pattern) || ip.matches(ipv6ShortPattern);
    }

    /**
     * 判断是否为内网IP地址。
     *
     * @return true-内网IP，false-公网IP
     */
    public boolean isInternalIp() {
        if (loginIp == null || loginIp.isBlank()) {
            return false;
        }

        // 常见内网IP段
        return loginIp.startsWith("10.")
                || loginIp.startsWith("172.16.")
                || loginIp.startsWith("172.17.")
                || loginIp.startsWith("172.18.")
                || loginIp.startsWith("172.19.")
                || loginIp.startsWith("172.20.")
                || loginIp.startsWith("172.21.")
                || loginIp.startsWith("172.22.")
                || loginIp.startsWith("172.23.")
                || loginIp.startsWith("172.24.")
                || loginIp.startsWith("172.25.")
                || loginIp.startsWith("172.26.")
                || loginIp.startsWith("172.27.")
                || loginIp.startsWith("172.28.")
                || loginIp.startsWith("172.29.")
                || loginIp.startsWith("172.30.")
                || loginIp.startsWith("172.31.")
                || loginIp.startsWith("192.168.")
                || "127.0.0.1".equals(loginIp);
    }

    /**
     * 判断是否与另一个登录记录同一天。
     *
     * @param other 另一个登录记录
     * @return true-同一天，false-不同天
     */
    public boolean isSameDay(LoginRecord other) {
        if (other == null || other.loginTime == null) {
            return false;
        }

        return loginTime.toLocalDate().equals(other.loginTime.toLocalDate());
    }

    /**
     * 判断是否与另一个登录记录同一IP。
     *
     * @param other 另一个登录记录
     * @return true-同一IP，false-不同IP
     */
    public boolean isSameIp(LoginRecord other) {
        if (other == null) {
            return false;
        }

        if (loginIp == null && other.loginIp == null) {
            return true;
        }

        if (loginIp == null || other.loginIp == null) {
            return false;
        }

        return loginIp.equals(other.loginIp);
    }

    /**
     * 判断登录时间是否在指定分钟数内。
     *
     * @param minutes 分钟数
     * @return true-在指定分钟内，false-超过指定分钟
     */
    public boolean isRecent(int minutes) {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(minutes);
        return loginTime.isAfter(threshold);
    }

    /**
     * 获取登录时间的字符串表示。
     *
     * @return 格式化的登录时间字符串（yyyy-MM-dd HH:mm:ss）
     */
    public String getFormattedTime() {
        return String.format(
                "%d-%02d-%02d %02d:%02d:%02d",
                loginTime.getYear(),
                loginTime.getMonthValue(),
                loginTime.getDayOfMonth(),
                loginTime.getHour(),
                loginTime.getMinute(),
                loginTime.getSecond());
    }

    /**
     * 返回登录记录的字符串表示。
     *
     * @return 包含登录时间和 IP 地址的字符串
     */
    @Override
    public String toString() {
        return String.format("LoginRecord{time=%s, ip=%s}", getFormattedTime(), loginIp != null ? loginIp : "unknown");
    }
}
