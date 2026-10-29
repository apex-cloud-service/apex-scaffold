package com.apex.scaffold.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 防重放攻击注解
 *
 * <p>用于接口防重放攻击（Anti-Replay Attack）的方法级别注解。
 * 通过校验请求的时间戳和随机数（Nonce），确保每个请求的唯一性和时效性，
 * 防止攻击者截获合法请求后再次发送以欺骗服务端。
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li><b>时间戳校验</b>：检查请求时间戳与服务器当前时间的偏差，
 *       若超过 {@link #maxTimeDiff} 秒则判定为无效请求</li>
 *   <li><b>随机数校验</b>：校验请求中的随机数（Nonce）是否已被使用过，
 *       已被使用的 Nonce 直接拒绝，确保请求的幂等性</li>
 * </ul>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>支付回调接口（防止重复扣款）</li>
 *   <li>用户关键信息修改接口（防止重复提交）</li>
 *   <li>短信、邮件验证码发送接口</li>
 *   <li>所有涉及资金、权益变动的关键接口</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <p>客户端需要在请求头中携带以下信息：
 * <pre>{@code * X-Timestamp: 1717200000        // Unix 时间戳（秒级）
 * X-Nonce: 550e8400-e29b-41d4-a716-446655440000  // UUID 或唯一随机字符串
 * X-Sign: 签名值                 // 可选，请求签名
 * }</pre>
 *
 * <h2>设计说明</h2>
 * <p>开启随机数校验（{@link #requireNonce}）时，需要在服务端维护 Nonce 的缓存
 * 以判断是否已使用，推荐使用 Redis 并设置与 {@link #maxTimeDiff} 一致的过期时间。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AntiReplay {

    /**
     * 允许的最大时间偏差（秒）
     *
     * <p>请求中的时间戳与服务器当前时间允许的最大偏差值。
     * 若请求时间戳与服务器时间的绝对值超过此值，则判定为无效请求。
     * 建议根据客户端与服务端之间的网络延迟情况适当调整。
     * <ul>
     *   <li>默认值：300 秒（5 分钟），适用于大多数场景</li>
     *   <li>弱网络环境可适当放宽至 600 秒</li>
     *   <li>对安全性要求高的场景可收紧至 60 秒</li>
     * </ul>
     *
     * @return 最大时间偏差，单位为秒
     */
    long maxTimeDiff() default 300;

    /**
     * 是否校验随机数（Nonce）
     *
     * <p>开启后将校验请求中的随机数是否已被使用过，防止同一请求被重复提交。
     * 关闭时仅校验时间戳，适用于对幂等性要求较低的场景。
     * <ul>
     *   <li>{@code true}：开启随机数校验（推荐），需要服务端维护 Nonce 缓存</li>
     *   <li>{@code false}：关闭随机数校验，仅依靠时间戳防重放</li>
     * </ul>
     *
     * @return 是否需要校验随机数
     */
    boolean requireNonce() default true;
}
