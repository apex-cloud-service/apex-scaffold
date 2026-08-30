package com.apex.scaffold.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

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
