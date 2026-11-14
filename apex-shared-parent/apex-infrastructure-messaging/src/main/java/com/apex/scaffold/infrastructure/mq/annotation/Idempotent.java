package com.apex.scaffold.infrastructure.mq.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 消息幂等性注解。
 *
 * <p>标注于 RocketMQ 消息消费者的处理方法上，通过 SpEL 表达式生成幂等键，
 * 与 {@link com.apex.scaffold.infrastructure.mq.aspect.IdempotentAspect} 配合防止重复消费。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    /**
     * 幂等性键（支持SpEL表达式）
     *
     * @return 键表达式
     */
    String key() default "";

    /**
     * 过期时间（秒）
     *
     * @return 过期时间
     */
    long expireTime() default 3600;
}
