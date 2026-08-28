package com.apex.scaffold.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * HTTP 写接口幂等注解。
 *
 * <p>标注于写操作（POST/PUT/DELETE 等）的 Handler 方法上，配合
 * {@code com.apex.scaffold.interfaces.aspect.IdempotentApiAspect} 实现分布式幂等去重：
 * 同一幂等键（默认取自 {@code Idempotency-Key} 请求头）在 TTL 窗口内的重复请求
 * 直接返回 {@code REPEAT_SUBMIT}，业务方法不会被执行第二次。
 *
 * <h2>与防重放（{@link AntiReplay}）的区别</h2>
 * <ul>
 *   <li>{@link AntiReplay}：防重放攻击，校验 {@code X-Timestamp} 与 {@code X-Nonce}，
 *       防止攻击者截获并重放已发出的请求</li>
 *   <li>本注解：业务幂等，客户端在网络超时后携带同一 {@code Idempotency-Key} 重试，
 *       服务端识别为同一业务动作并拒绝重复执行，防止重复扣款/重复下单等副作用</li>
 * </ul>
 *
 * <h2>幂等键合成</h2>
 * <pre>
 *   apex:idem:{sha256Hex(请求路径 + "|" + 幂等键头值 + "|" + 客户端IP)}
 * </pre>
 * 路径维度天然隔离不同接口；IP 维度防止不同客户端复用同一键值；业务动作本身的唯一性
 * 由客户端为每个动作生成唯一 UUID 保证（与账号注销接口的 {@code Idempotency-Key} 约定一致）。
 *
 * <h2>渐进接入</h2>
 * <p>{@link #required()} 为 {@code true} 时缺少幂等键头直接拒绝（严格模式，适合新接口）；
 * 为 {@code false} 时缺头请求照常放行（兼容旧客户端渐进接入，带上头即获得幂等保护）。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.2.0
 * @see AntiReplay
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface IdempotentApi {

    /**
     * 幂等键请求头名称。
     *
     * <p>客户端为每个写操作生成唯一键值（推荐 UUID）放入该请求头，
     * 重试时复用同一值。默认 {@code Idempotency-Key}，与账号注销接口约定一致。</p>
     *
     * @return 请求头名称
     */
    String header() default "Idempotency-Key";

    /**
     * 幂等记录存活时间（秒）。
     *
     * <p>窗口内相同幂等键的重复请求被拒绝；窗口过后允许再次提交。
     * 建议覆盖一次完整业务重试周期（含网络超时与人工重试），默认 600 秒。</p>
     *
     * @return 过期时间（秒）
     */
    long expireTime() default 600;

    /**
     * 幂等键头是否必填。
     *
     * <ul>
     *   <li>{@code true}：缺少幂等键头直接返回 {@code IDEMPOTENCY_KEY_MISSING}（严格模式）</li>
     *   <li>{@code false}：缺少幂等键头时跳过幂等校验放行，兼容旧客户端渐进接入</li>
     * </ul>
     *
     * @return 是否必填
     */
    boolean required() default true;
}
