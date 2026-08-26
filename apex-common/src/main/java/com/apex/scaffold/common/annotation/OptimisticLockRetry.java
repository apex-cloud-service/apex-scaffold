package com.apex.scaffold.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据库乐观锁重试注解
 *
 * <p>用于方法级别的乐观锁冲突自动重试。当 MyBatis-Plus 执行更新操作时，
 * 如果检测到 {@code version} 字段已被其他事务修改（乐观锁冲突），
 * 自动重试目标方法，直至成功或达到最大重试次数。
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li><strong>库存扣减</strong>：秒杀场景下对商品库存的并发扣减</li>
 *   <li><strong>余额更新</strong>：并发转账或充值操作</li>
 *   <li><strong>计数器更新</strong>：点赞数、收藏数等计数器的高频更新</li>
 *   <li><strong>状态变更</strong>：订单状态等需要并发安全更新的场景</li>
 * </ul>
 *
 * <h2>使用方式</h2>
 * <pre>{@code * @OptimisticLockRetry(maxRetries = 3, retryDelay = 50)
 * @Transactional
 * public boolean deductStock(Long productId, int quantity) {
 *     Product product = productMapper.selectById(productId);
 *     if (product.getStock() < quantity) {
 *         return false;
 *     }
 *     product.setStock(product.getStock() - quantity);
 *     return productMapper.updateById(product) > 0;
 * }
 * }</pre>
 *
 * <h2>注意事项</h2>
 * <ul>
 *   <li>被标注的方法必须具有幂等性或可重复执行，重试不应产生副作用</li>
 *   <li>目标方法需使用 MyBatis-Plus 的 {@code updateById} 操作，
 *       且对应持久化对象继承含 {@code @Version} 字段的 {@code BasePO}</li>
 *   <li>建议结合 {@code @Transactional} 使用，确保事务完整性</li>
 *   <li>返回 {@code Mono}/{@code Flux} 的响应式方法应在流内使用 Reactor 重试；
 *       本注解只处理方法调用期间同步抛出的冲突异常</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OptimisticLockRetry {

    /**
     * 最大重试次数
     *
     * <p>当乐观锁冲突时，最多重试的次数。达到最大重试次数后，
     * 抛出乐观锁冲突异常，由调用方处理。
     *
     * @return 最大重试次数（默认 3）
     */
    int maxRetries() default 3;

    /**
     * 重试间隔（毫秒）
     *
     * <p>每次重试之间等待的毫秒数，避免在冲突持续时进行无效的重试。
     * 建议设置 20-100ms 的短暂等待。
     *
     * @return 重试间隔（毫秒，默认 50）
     */
    long retryDelay() default 50;

    /**
     * 幂等方法标识
     *
     * <p>标记被注解的方法是否是幂等的（即重复执行不会产生副作用）。
     * 建议在方法具有幂等性时设置为 true，以获得更高的重试安全性。
     *
     * @return 是否幂等（默认 false）
     */
    boolean idempotent() default false;

    /**
     * 乐观锁冲突时的提示消息
     *
     * @return 提示消息
     */
    String message() default "操作过于频繁，请稍后重试";
}
