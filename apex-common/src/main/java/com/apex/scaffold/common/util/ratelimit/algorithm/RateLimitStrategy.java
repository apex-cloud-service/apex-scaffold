package com.apex.scaffold.common.util.ratelimit.algorithm;

/**
 * 限流算法策略接口
 *
 * <p>统一抽象令牌桶、漏桶、滑动窗口三种算法的调用接口，
 * 使上层代码可透明切换限流算法实现。
 *
 * <h2>设计模式</h2>
 * <p>策略模式：上层依赖此接口而非具体实现，通过
 * {@link RateLimitAlgorithmFactory} 注入不同算法实例。</p>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * RateLimitStrategy limiter = RateLimitAlgorithmFactory.create(
 *     RateLimitAlgorithm.TOKEN_BUCKET, 100, 10, 1000);
 *
 * if (limiter.tryAcquire()) {
 *     // 处理请求
 * }
 * }</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.0
 * @see TokenBucket
 * @see LeakyBucket
 * @see SlidingWindow
 * @see RateLimitAlgorithmFactory
 */
public interface RateLimitStrategy {

    /**
     * 尝试获取 1 个许可（非阻塞）。
     *
     * @return 获取成功返回 true，限流拒绝返回 false
     */
    boolean tryAcquire();

    /**
     * 尝试获取指定数量的许可（非阻塞）。
     *
     * @param permits 需要获取的许可数
     * @return 获取成功返回 true，限流拒绝返回 false
     */
    boolean tryAcquire(int permits);

    /**
     * 获取当前窗口内已使用的许可数。
     *
     * @return 已使用的许可数
     */
    long getCurrentCount();

    /**
     * 获取许可上限。
     *
     * @return 许可上限
     */
    long getMaxPermits();

    /**
     * 获取算法类型。
     *
     * @return 算法类型枚举
     */
    RateLimitAlgorithm getAlgorithmType();
}
