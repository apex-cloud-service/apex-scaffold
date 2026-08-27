package com.apex.scaffold.domain.gateway;

import reactor.core.publisher.Mono;

/**
 * 断路器网关端口（Port）。
 *
 * <p>定义领域层对外部调用故障隔离能力的抽象，由基础设施层（{@code apex-infrastructure}）
 * 基于 Resilience4j 提供实现。接口层（{@code apex-interfaces}）通过本端口面向抽象编程，
 * 消除对基础设施实现类的直接依赖（依赖倒置 DIP）。</p>
 *
 * <p>该端口定义了外部 API 调用的断路器语义：断路器打开时请求快速失败（短路），
 * 半开状态自动探测恢复，防止级联故障；调用方通过 {@code fallback} 降级。</p>
 *
 * <p>设计约束：本端口仅暴露与 Reactor 流式调用相关的核心能力，不暴露
 * Resilience4j 具体类型，保持领域层零第三方框架依赖。</p>
 *
 * <h2>实现约定</h2>
 * <ul>
 *   <li>基础设施层实现类需标注 {@code @Service} 并由 Spring 装配</li>
 *   <li>接口层过滤器/切面应注入本端口而非具体实现</li>
 *   <li>断路器管理类操作（创建/重置/强制开关）属运维能力，由基础设施实现类直接提供</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.2.0
 */
public interface CircuitBreakerPort {

    /**
     * 使用断路器保护 Mono 流。
     *
     * <p>若指定名称的断路器不存在，则使用默认配置创建。断路器打开期间
     * 所有请求直接失败（短路），由调用方通过 {@code onErrorResume} 降级。</p>
     *
     * @param <T>  返回类型
     * @param name 断路器名称（如外部服务标识）
     * @param mono 受保护的 Mono 流
     * @return 受断路器保护的 Mono 流
     */
    <T> Mono<T> withCircuitBreaker(String name, Mono<T> mono);

    /**
     * 使用断路器保护 Mono 流（带降级函数）。
     *
     * <p>断路器打开或调用失败时执行 {@code fallback} 返回降级结果，
     * 避免异常向上传播导致请求失败。</p>
     *
     * @param <T>      返回类型
     * @param name     断路器名称
     * @param mono     受保护的 Mono 流
     * @param fallback 降级结果（Mono）
     * @return 受保护的 Mono 流，失败时返回降级结果
     */
    <T> Mono<T> withCircuitBreaker(String name, Mono<T> mono, Mono<T> fallback);
}
