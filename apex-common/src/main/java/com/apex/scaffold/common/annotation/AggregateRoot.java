package com.apex.scaffold.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 聚合根标识注解。
 *
 * <p>用于标记领域驱动设计中的聚合根实体。聚合根是聚合的边界，负责维护聚合内的一致性规则，
 * 是聚合与外部进行交互的唯一入口。</p>
 *
 * <h2>DDD聚合设计原则</h2>
 * <ul>
 *   <li>聚合根拥有全局唯一标识</li>
 *   <li>聚合内的实体通过聚合根访问</li>
 *   <li>聚合之间通过聚合根ID引用</li>
 *   <li>事务边界以聚合为单位</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code * @AggregateRoot
 * public class UserEntity extends BaseEntity {
 *     // ...
 * }
 * }</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AggregateRoot {

    /**
     * 聚合名称，默认使用类名
     *
     * @return 聚合名称
     */
    String name() default "";

    /**
     * 是否为聚合的主根
     *
     * @return 是否为主根，默认为 true
     */
    boolean primary() default true;
}
