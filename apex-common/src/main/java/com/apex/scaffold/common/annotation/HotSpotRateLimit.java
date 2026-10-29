package com.apex.scaffold.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 热点参数限流注解
 *
 * <p>用于对高频访问的热点参数（如热门商品 ID、高并发用户 ID）进行精细化限流，
 * 防止热点数据导致后端服务过载。通过在方法参数上标注此注解，
 * 系统自动检测该参数的访问频率，当达到热点阈值时启动参数级限流保护。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li><strong>热门商品限流</strong>：秒杀活动中对单个商品 ID 进行限流</li>
 *   <li><strong>热点用户限流</strong>：对高频访问 API 的用户进行速率限制</li>
 *   <li><strong>热点数据查询限流</strong>：对高频查询的数据 Key 进行限流</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ol>
 *   <li>拦截标注了 {@code @HotSpotRateLimit} 的方法，提取指定参数的值</li>
 *   <li>使用滑动窗口计数器统计该参数值在窗口内的访问频率</li>
 *   <li>当频率超过 {@link #hotThreshold} 时，该参数被判定为"热点"</li>
 *   <li>对热点参数应用 {@link #limit} 指定的严格限流阈值</li>
 *   <li>窗口滑动后自动恢复，不再热门的数据解除限流</li>
 * </ol>
 *
 * <h2>使用示例</h2>
 * <pre>{@code * // 对商品ID进行热点限流：当商品访问 QPS > 50 时触发热点保护，
 * // 热点商品自身限流至 10 QPS
 * @HotSpotRateLimit(
 *     prefix = "product",
 *     key = "#productId",
 *     hotThreshold = 50,
 *     limit = 10,
 *     window = 1
 * )
 * public Mono<Product> getProductDetail(Long productId) { ... }
 *
 * // 对用户ID进行热点限流
 * @HotSpotRateLimit(
 *     prefix = "user",
 *     key = "#userId",
 *     hotThreshold = 100,
 *     limit = 20,
 *     window = 1
 * )
 * public Mono<UserInfo> getUserInfo(Long userId) { ... }
 * }</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see java.lang.annotation.Target
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface HotSpotRateLimit {

    /**
     * 热点限流键前缀
     *
     * <p>用于区分不同业务场景的热点限流键，建议使用业务模块名称，
     * 如 {@code "product"}、{@code "user"}。最终限流键格式为 {@code hotspot:prefix:key}。
     *
     * @return 热点限流键前缀
     */
    String prefix() default "";

    /**
     * 热点参数键表达式（支持 SpEL）
     *
     * <p>指定方法参数中需要监控的参数，支持 SpEL 表达式。
     * 系统会统计此参数值的访问频率，判断是否为热点。
     *
     * @return SpEL 表达式，对应方法参数
     */
    String key() default "";

    /**
     * 热点判定阈值
     *
     * <p>在 {@link #window} 秒内，当某个参数值的访问次数超过此阈值时，
     * 该参数值被判定为"热点"，触发热点限流保护。
     *
     * @return 热点判定阈值（次/窗口）
     */
    int hotThreshold() default 100;

    /**
     * 热点参数限流阈值
     *
     * <p>当参数被判定为热点后，该参数在窗口内允许的最大请求次数。
     * 热点限流阈值通常应低于 {@link #hotThreshold}，起到"降温"作用。
     *
     * @return 热点限流阈值（次/窗口）
     */
    int limit() default 20;

    /**
     * 统计窗口大小（秒）
     *
     * <p>热点判定和限流的统计时间窗口。建议与接口整体限流窗口保持一致。
     *
     * @return 时间窗口大小（秒）
     */
    int window() default 1;

    /**
     * 限流触发时的提示消息
     *
     * <p>当热点参数被限流时，返回给客户端的错误提示信息。
     *
     * @return 限流提示消息
     */
    String message() default "当前访问过于集中，请稍后再试";
}
