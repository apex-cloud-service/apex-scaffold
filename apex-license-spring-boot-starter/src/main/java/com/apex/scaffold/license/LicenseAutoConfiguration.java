package com.apex.scaffold.license;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * License授权自动配置类
 *
 * <p>本配置类负责装配License授权验证所需的全部核心组件,包括验证器、功能门控切面和运行期重检调度器。
 * 作为Spring Boot自动配置的一部分,无条件注册所有授权检查基础设施,确保商业功能的访问控制始终生效。</p>
 *
 * <h3>主要功能</h3>
 * <ul>
 *   <li><b>License验证器注册</b>:创建并缓存{@link LicenseValidator}实例,负责License文件的加载、
 *       签名校验、过期检查和功能授权判断</li>
 *   <li><b>功能门控切面</b>:自动注册{@link FeatureGateAspect},拦截所有{@code @FeatureGate}标注的方法,
 *       在运行时检查功能授权状态</li>
 *   <li><b>运行期重检调度器</b>:创建{@link LicenseRevalidationScheduler}守护线程,定期重新加载License文件,
 *       实现过期即时失效和续期自动生效</li>
 * </ul>
 *
 * <h3>不可关闭的设计</h3>
 * <p>本配置类使用{@code @AutoConfiguration}无条件装配(v2.2起),所有授权检查组件始终注册:
 * <ul>
 *   <li>{@code apex.license.enabled}缺省为{@code true};显式设为{@code false}仅意味着"更严格"——
 *       强制进入降级模式(全部商业功能拒绝),<b>不能</b>用于绕过{@code @FeatureGate}商业功能校验</li>
 *   <li>商业组件同时受{@code @ConditionalOnLicenseFeature}门控:即使宿主通过移除依赖等手段绕过本模块,
 *       商业基础设施(RBAC/缓存/MQ/追踪等)的装配条件同样不满足,无法静默开启</li>
 * </ul>
 *
 * <h3>容错降级策略</h3>
 * <p>License文件缺失、签名无效或已过期时,不阻塞应用启动,而是注册一个降级验证器——基础功能放行,
 * 所有商业功能({@code @FeatureGate}标注 / {@code @ConditionalOnLicenseFeature}装配的商业组件)拒绝,
 * 并输出明确的WARN日志。这种设计确保应用在授权问题下仍可正常启动,同时商业功能访问得到有效控制。</p>
 *
 * <h3>装配说明</h3>
 * <p>全部Bean均通过{@code @Bean}方法显式注册,不依赖宿主应用的{@code @ComponentScan}路径。
 * 这意味着无论宿主应用主类位于什么包名下,只要引入本模块且未关闭{@code apex.license.enabled},
 * 授权检查机制即自动生效。</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // application.yml配置
 * apex:
 *   license:
 *     enabled: true
 *     location: classpath:license.lic
 *     public-key: classpath:license-public.key
 *     recheck-interval: 1h
 *
 * // 宿主应用无需任何配置,引入依赖即可
 * <dependency>
 *   <groupId>com.apex.scaffold</groupId>
 *   <artifactId>apex-license-spring-boot-starter</artifactId>
 * </dependency>
 * }</pre>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 * @see LicenseValidator
 * @see FeatureGateAspect
 * @see LicenseRevalidationScheduler
 * @see LicenseProperties
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
