package com.apex.scaffold.infrastructure.cache.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 缓存锁注解
 *
 * <p>用于方法级别的分布式锁控制，基于 Redisson 实现。
 * 支持多种降级策略，在获取锁失败时可选择抛出异常、返回 null 或执行降级方法。
 *
 * <h2>锁命名规范</h2>
 * <pre>
 *   cache:lock:{prefix}:{key}
 * </pre>
 *
 * <h2>锁配置说明</h2>
 * <ul>
 *   <li><strong>waitTime</strong>：等待获取锁的最大时间，默认 3000 毫秒。
 *       在指定的时间内会持续尝试获取锁，超时则根据降级策略处理。</li>
 *   <li><strong>leaseTime</strong>：锁的租约时间，默认 -1 表示启用 Redisson Watchdog（看门狗）自动续期。
 *       设置为正值时，锁会在指定时间后自动释放，不会自动续期；
 *       设置为 -1 时，Redisson 的 Watchdog 机制会每隔 10 秒自动续期，
 *       防止业务执行时间过长导致锁提前释放。</li>
 * </ul>
 *
 * <h2>降级策略</h2>
 * <ul>
 * <li>{@link FallbackStrategy#THROW_EXCEPTION} - 获取锁失败时抛出异常（默认）</li>
 * <li>{@link FallbackStrategy#RETURN_NULL} - 获取锁失败时返回 null</li>
 * <li>{@link FallbackStrategy#RETURN_DEFAULT} - 获取锁失败时返回默认值（需要配置 fallbackMethod）</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code * // 默认策略：获取锁失败抛出异常
 * @CacheLock(prefix = "user", key = "#userId", waitTime = 3000)
 * public UserEntity getUserById(Long userId) { ... }
 *
 * // 获取锁失败返回 null
 * @CacheLock(prefix = "order", key = "#orderId", fallbackStrategy = FallbackStrategy.RETURN_NULL)
 * public OrderEntity getOrder(Long orderId) { ... }
 *
 * // 获取锁失败执行降级方法
 * @CacheLock(prefix = "product", key = "#productId",
 *            fallbackStrategy = FallbackStrategy.RETURN_DEFAULT,
 *            fallbackMethod = "getProductFromCache")
 * public ProductEntity getProduct(Long productId) { ... }
 *
 * public ProductEntity getProductFromCache(Long productId) { ... }
 * }</pre>
 *
 * @author Apex Team
 * @version 2.1.0
 * @since 1.0.0
 * @see com.apex.scaffold.infrastructure.cache.aspect.CacheLockAspect
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CacheLock {

    /**
     * 锁前缀
     *
     * <p>用于区分不同业务模块的锁，最终锁键格式为 {@code cache:lock:{prefix}:{key}}。
     *
     * @return 锁前缀字符串
     */
    String prefix() default "";

    /**
     * 锁键（支持 SpEL 表达式）
     *
     * <p>定义锁的唯一标识，支持 Spring EL 表达式动态解析方法参数。
     * 例如 {@code "#userId"} 表示使用方法参数中的 userId 值作为锁键。
     *
     * @return 锁键字符串
     */
    String key();

    /**
     * 等待获取锁的最大时间
     *
     * <p>超过此时间未获取到锁，将根据 {@link #fallbackStrategy()} 降级策略处理。
     * 默认值为 3000 毫秒（3 秒），避免因等待时间过短导致锁竞争失败。
     *
     * @return 等待时间（默认 3000 毫秒）
     */
    long waitTime() default 3000;

    /**
     * 锁自动释放时间（租约时间）
     *
     * <p>控制锁的最大持有时间。默认值为 -1，表示启用 Redisson Watchdog（看门狗）自动续期机制：
     * <ul>
     *   <li><strong>-1（默认）</strong>：启用 Watchdog 自动续期，每 10 秒续期一次，
     *       锁会一直持有直到业务方法执行完毕或进程崩溃</li>
     *   <li><strong>正值</strong>：锁在指定时间后自动释放，不自动续期。
     *       适用于执行时间可预期的场景，注意防止业务执行超时导致锁提前释放</li>
     * </ul>
     *
     * @return 租约时间（默认 -1，启用 Watchdog 自动续期）
     */
    long leaseTime() default -1;

    /**
     * 时间单位
     *
     * @return 时间单位
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /**
     * 获取锁失败时的降级策略
     *
     * @return 降级策略枚举
     */
    FallbackStrategy fallbackStrategy() default FallbackStrategy.THROW_EXCEPTION;

    /**
     * 降级方法名称（当 fallbackStrategy = RETURN_DEFAULT 时生效）
     *
     * <p>降级方法必须与原方法在同一个类中，且参数列表完全相同。
     *
     * @return 降级方法名称
     */
    String fallbackMethod() default "";

    /**
     * 获取锁失败时的错误消息
     *
     * @return 错误消息
     */
    String errorMessage() default "获取分布式锁失败";

    /**
     * 降级策略枚举
     */
    enum FallbackStrategy {
        /**
         * 获取锁失败时抛出异常
         */
        THROW_EXCEPTION,

        /**
         * 获取锁失败时返回 null
         */
        RETURN_NULL,

        /**
         * 获取锁失败时执行降级方法返回默认值
         */
        RETURN_DEFAULT
    }
}
