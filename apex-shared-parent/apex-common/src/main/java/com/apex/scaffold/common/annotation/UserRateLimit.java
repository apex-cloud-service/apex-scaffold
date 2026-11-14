package com.apex.scaffold.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 用户级别限流注解
 *
 * <p>基于用户维度的请求频率限制注解，适用于需要对单个用户进行精细化流量控制的场景。
 * 与 {@link RateLimiter} 的区别在于，本注解以用户身份作为限流粒度，
 * 通过 {@link #prefix} 和 {@link #key} 组合生成唯一的限流键，实现对特定用户
 * 在指定时间窗口内的请求频率控制。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>短信验证码发送频率限制（如：同一手机号每分钟最多发送 1 次）</li>
 *   <li>API 接口的用户级调用频率控制</li>
 *   <li>敏感操作（如修改密码、提现）的频次限制</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <p>采用令牌桶或滑动窗口算法实现，限流键的生成规则为 {@code prefix + ":" + key}。
 * 当用户在 {@link #rateInterval} 秒内的请求速率超过 {@link #rate} 时，请求被拒绝
 * 并返回 {@link #message} 中配置的提示信息。
 *
 * <h2>使用示例</h2>
 * <pre>{@code * @UserRateLimit(prefix = "sms:send", key = "#phone", rate = 1.0, rateInterval = 60)
 * public Mono<Void> sendSms(String phone) { ... }
 * }</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see RateLimiter
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface UserRateLimit {

    /**
     * 限流键前缀
     *
     * <p>用于区分不同业务场景的限流键前缀，建议使用业务模块名称作为前缀，
     * 如 {@code "sms:send"}、{@code "api:login"}。最终限流键格式为 {@code prefix:key}。
     *
     * @return 限流键前缀
     */
    String prefix() default "";

    /**
     * 限流键
     *
     * <p>用于标识限流维度的键值，通常为用户的唯一标识（如用户 ID、手机号、IP 地址）。
     * 支持 SpEL 表达式从方法参数中动态取值。
     *
     * @return 限流键
     */
    String key() default "";

    /**
     * 限流速率
     *
     * <p>在 {@link #rateInterval} 秒内允许的最大请求次数。例如 {@code rate = 1.0}
     * 表示每 {@code rateInterval} 秒内最多允许 1 次请求。
     *
     * @return 限流速率
     */
    double rate() default 1.0;

    /**
     * 限流时间窗口（秒）
     *
     * <p>限流统计的时间窗口大小，与 {@link #rate} 共同决定限流阈值。
     * 例如 {@code rate = 5.0, rateInterval = 60} 表示每分钟最多 5 次请求。
     *
     * @return 限流时间窗口，单位为秒
     */
    long rateInterval() default 1;

    /**
     * 限流触发时的提示消息
     *
     * <p>当请求被限流拦截时，返回给客户端的错误提示信息。
     *
     * @return 限流提示消息
     */
    String message() default "操作过于频繁，请稍后再试";
}
