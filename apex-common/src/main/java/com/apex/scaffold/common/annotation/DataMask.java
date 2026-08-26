package com.apex.scaffold.common.annotation;

import java.lang.annotation.*;

/**
 * 数据脱敏注解
 *
 * <p>用于标记需要脱敏的字段,配合AOP切面自动脱敏
 *
 * @author Apex Team
 * @version 1.0.0
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataMask {

    /**
     * 脱敏类型
     */
    MaskType type() default MaskType.GENERAL;

    /**
     * 自定义脱敏规则(当type=CUSTOM时使用)
     * <p>格式: "prefix,suffix" 例如 "3,4" 表示保留前3位和后4位
     */
    String customRule() default "";
}
