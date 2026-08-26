package com.apex.scaffold.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 角色控制注解
 *
 * <p>用于快速标注接口所需角色，简化权限配置
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRole {

    /**
     * 所需角色
     *
     * <p>用户必须拥有其中任意一个角色才能访问
     *
     * @return 角色数组
     */
    String[] value();

    /**
     * 逻辑关系
     *
     * <p>true - 必须拥有所有角色
     *
     * <p>false - 拥有任意一个角色即可
     *
     * @return 是否需要所有角色
     */
    boolean requireAll() default false;
}
