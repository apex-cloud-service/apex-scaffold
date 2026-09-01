package com.apex.scaffold.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 方法级自动重试注解，用于封装固定次数 + 指数退避的同步重试逻辑。
 *
 * <p>仅在业务方法本身幂等、或重试失败可由上层兜底回滚时使用。</p>
 *
 * @author apex-scaffold
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AutoRetry {
    int maxAttempts() default 3;

    long retryDelayMs() default 100;

    long maxDelayMs() default 5000;

    double backoffMultiplier() default 2.0;

    Class<? extends Throwable>[] retryOn() default {RuntimeException.class};
}
