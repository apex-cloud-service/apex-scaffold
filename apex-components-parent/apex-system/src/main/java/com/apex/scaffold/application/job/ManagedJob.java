package com.apex.scaffold.application.job;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 动态任务白名单标记；只有显式标记的公开业务方法允许被任务配置调用。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ManagedJob {}
