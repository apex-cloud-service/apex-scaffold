package com.apex.scaffold.license;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 商业功能条件装配实现
 *
 * <p>条件评估期从 Environment 绑定 {@code apex.license.*} 配置，
 * 通过 {@link LicenseFeatureGate} 检查功能授权（详见其类注释中关于
 * 条件评估早于 Bean 实例化的说明）。</p>
 *
 * @author Apex Team
 * @version 2.2.0
 */
@Slf4j
public class LicenseFeatureCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        var attributes = metadata.getAnnotationAttributes(ConditionalOnLicenseFeature.class.getName());
        if (attributes == null || attributes.get("value") == null) {
            return false;
        }
        String feature = (String) attributes.get("value");

        LicenseProperties properties = Binder.get(context.getEnvironment())
                .bind("apex.license", Bindable.of(LicenseProperties.class))
                .orElseGet(LicenseProperties::new);

        boolean enabled = LicenseFeatureGate.isFeatureEnabled(properties, feature);
        if (!enabled) {
            log.info(
                    "License 未授权功能 [{}]，跳过对应商业组件装配（降级模式或功能集不含该标识）",
                    feature);
        }
        return enabled;
    }
}
