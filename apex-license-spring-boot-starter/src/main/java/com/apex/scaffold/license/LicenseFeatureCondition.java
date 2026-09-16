package com.apex.scaffold.license;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * License商业功能条件装配实现类
 *
 * <p>本类实现Spring Boot的{@link Condition}接口,为{@code @ConditionalOnLicenseFeature}注解提供条件评估逻辑。
 * 在Spring容器Bean装配阶段,根据License授权状态决定是否装配标注了该注解的商业组件。</p>
 *
 * <h3>主要功能</h3>
 * <ul>
 *   <li><b>条件评估</b>:在Bean实例化前,从Spring Environment绑定{@code apex.license.*}配置,
 *       通过{@link LicenseFeatureGate}检查指定功能是否已授权</li>
 *   <li><b>商业组件门控</b>:未授权的功能对应的Bean不会被装配到Spring容器,从根源上防止商业功能被访问</li>
 *   <li><b>双层防护</b>:与运行时的{@link FeatureGateAspect}切面配合,形成"装配期+运行期"的双层授权检查机制</li>
 * </ul>
 *
 * <h3>工作原理</h3>
 * <p>条件评估发生在Spring容器启动的Bean定义注册阶段,早于Bean实例化:
 * <ol>
 *   <li>Spring扫描到标注了{@code @ConditionalOnLicenseFeature}的Bean定义</li>
 *   <li>调用本类的{@code matches}方法,传入注解元数据和Spring上下文</li>
 *   <li>从注解中提取{@code value}属性(功能标识符),从Environment绑定License配置</li>
 *   <li>通过{@link LicenseFeatureGate}单例检查功能授权状态</li>
 *   <li>返回{@code true}时Bean正常装配,返回{@code false}时Bean被跳过并记录日志</li>
 * </ol>
 *
 * <h3>与运行期切面的协同</h3>
 * <p>条件装配与{@link FeatureGateAspect}运行时切面共享同一个{@link LicenseValidator}实例(通过
 * {@link LicenseFeatureGate}静态缓存),保证装配期和运行期的授权判断一致。这种设计确保:
 * <ul>
 *   <li>商业基础设施Bean(如高级缓存、消息队列适配器)在未授权时不会装配</li>
 *   <li>即使通过反射等手段绕过装配检查,运行时切面仍会拦截标注了{@code @FeatureGate}的方法调用</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 标注在配置类上,整个配置类的所有Bean都受此功能授权控制
 * @Configuration
 * @ConditionalOnLicenseFeature("advanced-cache")
 * public class AdvancedCacheConfig {
 *     @Bean
 *     public RedisCacheManager cacheManager() {
 *         // 只有STANDARD或ENTERPRISE版License才会装配
 *     }
 * }
 *
 * // 标注在单个Bean方法上,仅控制该Bean的装配
 * @Configuration
 * public class MessagingConfig {
 *     @Bean
 *     @ConditionalOnLicenseFeature("message-queue")
 *     public RocketMQTemplate rocketMQTemplate() {
 *         // 只有ENTERPRISE版License才会装配
 *     }
 * }
 * }</pre>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 * @see ConditionalOnLicenseFeature
 * @see LicenseFeatureGate
 * @see FeatureGateAspect
 * @see org.springframework.context.annotation.Condition
 */
@Slf4j
public class LicenseFeatureCondition implements Condition {

    private static final String VALUE_ATTRIBUTE = "value";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        var attributes = metadata.getAnnotationAttributes(ConditionalOnLicenseFeature.class.getName());
        if (attributes == null || attributes.get(VALUE_ATTRIBUTE) == null) {
            return false;
        }
        String feature = (String) attributes.get("value");

        LicenseProperties properties = Binder.get(context.getEnvironment())
                .bind("apex.license", Bindable.of(LicenseProperties.class))
                .orElseGet(LicenseProperties::new);

        boolean enabled = LicenseFeatureGate.isFeatureEnabled(properties, feature);
        if (!enabled) {
            log.info("License 未授权功能 [{}]，跳过对应商业组件装配（降级模式或功能集不含该标识）", feature);
        }
        return enabled;
    }
}
