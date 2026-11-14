package com.apex.scaffold.common.annotation;

import java.lang.annotation.*;

/**
 * 功能开关注解
 *
 *
 *<p>用于标记需要License授权的功能,配合AOP切面自动检查权限。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface FeatureGate {

    /**
     * 功能标识
     * <p>例如: "audit_log", "api_version", "monitoring"
     */
    String value();

    /**
     * 错误消息
     * <p>当功能未授权时返回的错误提示
     */
    String message() default "当前License未授权此功能,请升级版本";
}
