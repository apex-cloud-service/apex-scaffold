package com.apex.scaffold.license;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

/**
 * License 功能门控静态入口
 *
 * <p>Spring {@code Condition} 在 Bean 定义注册期执行，早于自动配置 Bean 的实例化，
 * 无法通过容器注入获取 {@link LicenseValidator}。本类以「配置指纹 → 验证器」的
 * 静态缓存为条件装配（{@link ConditionalOnLicenseFeature}）与运行期切面
 * （{@link FeatureGateAspect}）提供同一份授权状态：</p>
 *
 * <ul>
 *   <li>缓存键为 {@link LicenseProperties} 的全部字段值 —— 相同配置复用同一验证器，
 *       不同配置（如测试切换 License 路径）各自独立，互不污染</li>
 *   <li>验证失败或 {@code enabled=false} 时返回降级验证器（商业功能全拒绝）</li>
 * </ul>
 *
 * @author Apex Team
 * @version 2.2.0
 */
@Slf4j
public final class LicenseFeatureGate {

    private static final Map<String, LicenseValidator> CACHE = new ConcurrentHashMap<>();

    private LicenseFeatureGate() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * 获取（或创建）指定配置对应的验证器
     *
     * @param properties License 配置
     * @return 验证器（永不为 null；加载失败时为降级验证器）
     */
    public static LicenseValidator getOrCreate(LicenseProperties properties) {
        String key = fingerprint(properties);
        return CACHE.computeIfAbsent(key, k -> build(properties));
    }

    /**
     * 检查功能是否获得授权（条件装配期使用）
     *
     * @param properties License 配置
     * @param feature 功能标识
     * @return 是否授权
     */
    public static boolean isFeatureEnabled(LicenseProperties properties, String feature) {
        return getOrCreate(properties).isFeatureEnabled(feature);
    }

    /** 构建验证器：enabled=false 强制降级；加载/验证失败容错降级。 */
    private static LicenseValidator build(LicenseProperties properties) {
        if (!properties.isEnabled()) {
            log.warn("apex.license.enabled=false —— 强制降级模式：全部商业功能拒绝（该开关不可用于绕过授权）");
            return LicenseValidator.degraded();
        }
        try {
            LicenseValidator validator = LicenseLoader.load(properties);
            log.info(
                    "License 验证通过: 客户={}, 版本={}",
                    validator.getLicenseInfo().getCustomerName(),
                    validator.getLicenseInfo().getVersion());
            return validator;
        } catch (Exception e) {
            log.warn("License 加载或验证失败（{}），进入降级模式：商业功能不可用，基础功能不受影响", e.getMessage());
            return LicenseValidator.degraded();
        }
    }

    /** 配置指纹：所有影响验证结果的字段拼接。 */
    private static String fingerprint(LicenseProperties properties) {
        return properties.isEnabled()
                + "|" + properties.getFilePath()
                + "|" + properties.getPublicKey()
                + "|" + properties.getPublicKeyFile()
                + "|" + properties.isMachineFingerprintSkip()
                + "|" + properties.getMachineFingerprintOverride();
    }

    /** 清空缓存（仅测试使用）。 */
    static void resetForTest() {
        CACHE.clear();
    }
}
