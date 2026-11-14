package com.apex.scaffold.license;

import java.util.Locale;

/**
 * 操作系统家族枚举。
 *
 * <p>将 {@code os.name} 系统属性的字符串匹配逻辑收敛到单一职责枚举，供
 * {@link MachineFingerprintCollector} 按操作系统分派 CPU / 主板指纹采集策略。
 * 识别规则：</p>
 * <ul>
 *   <li>{@link #LINUX}：名称包含 {@code linux}</li>
 *   <li>{@link #MAC}：名称包含 {@code mac} 或 {@code darwin}</li>
 *   <li>{@link #WINDOWS}：名称包含 {@code windows}</li>
 *   <li>{@link #UNKNOWN}：其余未识别系统</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.2.1
 */
public enum OsFamily {
    /** Linux 系操作系统。 */
    LINUX,
    /** macOS 系操作系统。 */
    MAC,
    /** Windows 系操作系统。 */
    WINDOWS,
    /** 其他无法识别的操作系统。 */
    UNKNOWN;

    private static final String LINUX_NAME = "linux";
    private static final String MAC_NAME = "mac";
    private static final String DARWIN_NAME = "darwin";
    private static final String WINDOWS_NAME = "windows";

    /**
     * 根据 {@code os.name} 系统属性识别操作系统家族。
     *
     * <p>取值大小写不敏感；未设置系统属性或无法识别时返回 {@link #UNKNOWN}。</p>
     *
     * @return 识别出的操作系统家族，永不返回 {@code null}
     */
    public static OsFamily detect() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains(LINUX_NAME)) {
            return LINUX;
        }
        if (osName.contains(MAC_NAME) || osName.contains(DARWIN_NAME)) {
            return MAC;
        }
        if (osName.contains(WINDOWS_NAME)) {
            return WINDOWS;
        }
        return UNKNOWN;
    }
}
