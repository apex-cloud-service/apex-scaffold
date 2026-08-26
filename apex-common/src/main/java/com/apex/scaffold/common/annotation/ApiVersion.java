package com.apex.scaffold.common.annotation;

import java.lang.annotation.*;

/**
 * API版本注解
 *
 * <p>用于标记API的版本号,支持多版本共存和灰度发布。
 * 配合ApiVersionConfig实现基于URL路径的版本控制。
 *
 * @author Apex Team
 * @version 1.0.0
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiVersion {

    /**
     * 版本号
     * <p>例如: "v1", "v2", "v3"
     */
    String value() default "v1";

    /**
     * 是否废弃
     * <p>标记为废弃的版本将在文档中标注,但仍可访问
     */
    boolean deprecated() default false;

    /**
     * 废弃说明
     * <p>当deprecated=true时,提供迁移建议
     */
    String deprecationNote() default "";

    /**
     * 最低支持版本
     * <p>用于向后兼容检查
     */
    String minVersion() default "v1";
}
