package com.apex.scaffold.license;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Conditional;

/**
 * 商业功能条件装配注解
 *
 * <p>标注在商业组件的配置类 / Bean 方法上，仅当当前 License 授权了对应功能时才装配。
 * 与 {@code @ConditionalOnProperty(apex.features.*)}（档位开关）叠加使用，
 * 形成「档位开关 × License 授权」双重门控：
 * <ul>
 *   <li>档位开关关闭 → 组件不装配（版本矩阵裁剪）</li>
 *   <li>档位开关开启但 License 未授权（含降级模式）→ 组件同样不装配（fork 无法白嫖）</li>
 * </ul>
 *
 * <p>示例：</p>
 * <pre>{@code
 * @ConditionalOnProperty(name = "apex.features.rbac", havingValue = "true")
 * @ConditionalOnLicenseFeature("rbac")
 * @Configuration
 * public class RbacConfig { ... }
 * }</pre>
 *
 * @author Apex Team
 * @version 2.2.0
 * @since 2.2.0
 * @see LicenseFeatureCondition
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Conditional(LicenseFeatureCondition.class)
public @interface ConditionalOnLicenseFeature {

    /**
     * 功能标识（与 License enabledFeatures 列表、@FeatureGate value 一致）
     */
    String value();
}
