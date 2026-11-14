package com.apex.scaffold.common.util.ratelimit.algorithm;

/**
 * 限流算法类型枚举
 *
 * <p>定义系统支持的三种限流算法，供 {@link RateLimitAlgorithmFactory} 创建对应的算法实例。
 * 每种算法有不同的适用场景和流量特性：
 *
 * <ul>
 *   <li>{@link #TOKEN_BUCKET}：令牌桶，允许突发流量，控制平均速率</li>
 *   <li>{@link #LEAKY_BUCKET}：漏桶，严格平滑输出，不允许突发</li>
 *   <li>{@link #SLIDING_WINDOW}：滑动窗口，精确控制窗口内请求数</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.0
 * @see TokenBucket
 * @see LeakyBucket
 * @see SlidingWindow
 */
public enum RateLimitAlgorithm {

    /**
     * 令牌桶算法
     *
     * <p>以固定速率补充令牌，请求消耗令牌。支持突发流量（受桶容量限制），
     * 适用于 API 限流、缓存重建保护等允许短时突发的场景。
     */
    TOKEN_BUCKET,

    /**
     * 漏桶算法
     *
     * <p>请求注入桶中，桶以固定速率漏水。输出速率恒定，不允许突发，
     * 适用于 MQ 消费流控、下游服务保护等需要严格平滑输出的场景。
     */
    LEAKY_BUCKET,

    /**
     * 滑动窗口算法
     *
     * <p>基于桶分片的滑动窗口，精确控制时间窗口内的请求数。
     * 适用于 IP 限流、热点参数限流等需要精确窗口计数的场景。
     */
    SLIDING_WINDOW
}
