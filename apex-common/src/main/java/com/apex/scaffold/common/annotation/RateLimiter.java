package com.apex.scaffold.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解
 *
 * <p>基于方法维度的请求频率限制注解，适用于需要对接口整体进行流量控制的场景。
 * 通过滑动窗口或令牌桶算法限制接口在指定时间窗口内的最大访问次数，
 * 防止突发流量冲垮后端服务。
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>公开 API 接口的访问频率控制</li>
 *   <li>登录接口暴力破解防护</li>
 *   <li>资源密集型接口的并发保护</li>
 *   <li>第三方服务调用的配额管理</li>
 * </ul>
 *
 * <h2>限流维度</h2>
 * <p>本注解以接口方法为粒度进行限流，所有请求共享同一限流阈值。
 * 若需要按用户维度进行精细化限流，请使用 {@link UserRateLimit} 注解。
 *
 * <h2>配置示例</h2>
 * <pre>{@code * // 每分钟最多处理 100 次请求
 * @RateLimiter(limit = 100, window = 1)
 * public Mono<Void> batchProcess() { ... }
 *
 * // 多维度限流组合
 * @RateLimiter(limit = 1000, window = 60, message = "API 调用过于频繁")
 * @UserRateLimit(prefix = "api", key = "#userId", rate = 10, rateInterval = 60)
 * public Mono<Void> queryData(String userId) { ... }
 * }</pre>
 *
 * <h2>与 UserRateLimit 的区别</h2>
 * <table border="1">
 *   <caption>限流维度对比</caption>
 *   <tr><th>注解</th><th>限流粒度</th><th>适用场景</th></tr>
 *   <tr><td>RateLimiter</td><td>接口级别</td><td>接口总体流量控制</td></tr>
 *   <tr><td>UserRateLimit</td><td>用户级别</td><td>单个用户频次控制</td></tr>
 * </table>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see UserRateLimit
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimiter {

    /**
     * 限流器名称
     *
     * <p>用于标识不同的限流规则，支持 SpEL 表达式。若为空，默认使用方法签名作为限流键。
     * 建议按业务模块命名，如 {@code "sendSms"}、{@code "userLogin"}。
     *
     * @return 限流器名称
     */
    String value() default "";

    /**
     * 时间窗口内允许的最大请求数
     *
     * <p>在 {@link #window} 秒内允许的最大请求次数。当请求数超过此阈值时，
     * 后续请求将被限流拦截。
     *
     * @return 最大请求数
     */
    int limit() default 100;

    /**
     * 限流时间窗口（秒）
     *
     * <p>限流统计的时间窗口大小。例如 {@code limit = 100, window = 1} 表示
     * 每秒最多允许 100 次请求；{@code limit = 1000, window = 60} 表示每分钟
     * 最多允许 1000 次请求。
     *
     * @return 时间窗口，单位为秒
     */
    int window() default 1;

    /**
     * 限流触发时的提示消息
     *
     * <p>当请求被限流拦截时，返回给客户端的错误提示信息。
     *
     * @return 限流提示消息
     */
    String message() default "请求过于频繁，请稍后再试";
}
