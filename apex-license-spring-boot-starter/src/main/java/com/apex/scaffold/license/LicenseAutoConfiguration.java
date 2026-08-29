package com.apex.scaffold.license;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * License自动配置
 *
 * <p><b>不可整体关闭</b>：本配置类无条件装配（v2.2 起）。
 * 验证器、门控切面与运行期重检调度器始终注册：
 * <ul>
 *   <li>{@code apex.license.enabled} 缺省为 {@code true}；显式设为 {@code false}
 *       仅意味着"更严格"——强制进入降级模式（全部商业功能拒绝），
 *       <b>不能</b>用于绕过 {@code @FeatureGate} 商业功能校验</li>
 *   <li>商业组件同时受 {@code @ConditionalOnLicenseFeature} 门控：即使宿主
 *       通过移除依赖等手段绕过本模块，商业基础设施（RBAC/缓存/MQ/追踪等）
 *       的装配条件同样不满足，无法静默开启</li>
 * </ul>
 *
 * <p><b>容错降级策略</b>：License 文件缺失、签名无效或已过期时，
 * 不阻塞应用启动，而是注册一个降级验证器 —— 基础功能放行，
 * 所有商业功能（{@code @FeatureGate} 标注 / {@code @ConditionalOnLicenseFeature}
 * 装配的商业组件）拒绝，并输出明确的 WARN 日志。</p>
 *
 * <p><b>装配说明</b>：本配置同时注册 {@link FeatureGateAspect}（功能门控切面）与
 * {@link LicenseRevalidationScheduler}（运行期重检），全部 Bean 均通过自动配置注册，
 * 不依赖宿主应用的组件扫描路径。</p>
 *
 * @author Apex Team
 * @version 2.2.0
 */
@Slf4j
@AutoConfiguration
@RequiredArgsConstructor
@EnableConfigurationProperties(LicenseProperties.class)
public class LicenseAutoConfiguration {

    private final LicenseProperties properties;

    /**
     * License 验证器。
     *
     * <p>验证逻辑经 {@link LicenseFeatureGate} 静态缓存 —— 条件装配期
     * （{@code @ConditionalOnLicenseFeature}）与运行期切面共享同一验证器实例，
     * 保证两条路径的授权判断一致。</p>
     *
     * @return 验证器（加载失败时为降级验证器）
     */
    @Bean
    public LicenseValidator licenseValidator() {
        LicenseValidator validator = LicenseFeatureGate.getOrCreate(properties);
        if (validator.isValidated()) {
            log.info(
                    "License 验证通过: 客户={}, 版本={}",
                    validator.getLicenseInfo().getCustomerName(),
                    validator.getLicenseInfo().getVersion());
        }
        return validator;
    }

    /**
     * 注册功能门控切面
     *
     * <p>由自动配置统一注册（而非依赖宿主应用的 {@code @ComponentScan} 路径），
     * 保证任意包名结构的宿主应用引入本模块后 {@code @FeatureGate} 均能生效。</p>
     *
     * @param licenseValidator License 验证器
     * @return 功能门控切面
     */
    @Bean
    public FeatureGateAspect featureGateAspect(LicenseValidator licenseValidator) {
        return new FeatureGateAspect(licenseValidator);
    }

    /**
     * License 运行期重检调度器（守护线程，随应用关闭终止）。
     *
     * <p>{@code apex.license.recheck-interval<=0} 时为空操作。</p>
     *
     * @param licenseValidator 共享验证器
     * @return 调度器
     */
    @Bean(destroyMethod = "close")
    public LicenseRevalidationScheduler licenseRevalidationScheduler(LicenseValidator licenseValidator) {
        return new LicenseRevalidationScheduler(licenseValidator, properties);
    }
}
