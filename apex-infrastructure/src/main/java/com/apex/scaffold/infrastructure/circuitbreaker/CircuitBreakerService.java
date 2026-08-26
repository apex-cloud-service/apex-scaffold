package com.apex.scaffold.infrastructure.circuitbreaker;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 断路器服务
 *
 * <p>基于 Resilience4j 实现的断路器服务，用于保护外部API调用，防止级联故障。
 * 支持多种断路器配置策略，可根据业务需求灵活调整。
 *
 * <h2>断路器状态</h2>
 * <ul>
 *   <li><strong>CLOSED（关闭状态）</strong>：正常状态，所有请求正常通过</li>
 *   <li><strong>OPEN（打开状态）</strong>：断路器打开，所有请求直接失败，不调用外部服务</li>
 *   <li><strong>HALF_OPEN（半开状态）</strong>：尝试恢复，允许部分请求通过以测试服务是否恢复</li>
 * </ul>
 *
 * <h2>设计目标</h2>
 * <ul>
 *   <li><strong>故障隔离</strong>：防止单个服务故障影响整个系统</li>
 *   <li><strong>快速失败</strong>：断路器打开时立即返回降级结果，避免资源浪费</li>
 *   <li><strong>自动恢复</strong>：自动尝试恢复连接</li>
 *   <li><strong>可配置性</strong>：支持多种配置策略</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code * // 使用默认断路器保护外部API调用
 * circuitBreakerService.withCircuitBreaker("chuanglan-api", apiCallMono)
 *     .onErrorResume(e -> Mono.just(fallbackResult))
 *     .subscribe();
 *
 * // 使用自定义配置创建断路器
 * circuitBreakerService.createCircuitBreaker("custom-api",
 *     CircuitBreakerConfig.custom()
 *         .failureRateThreshold(50)
 *         .waitDurationInOpenState(Duration.ofSeconds(30))
 *         .build());
 * }</pre>
 *
 * @author Apex Team
 * @version 1.1.0
 * @since 1.0.0
 * @see CircuitBreaker
 * @see CircuitBreakerConfig
 */
@Service
public class CircuitBreakerService {
    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerService.class);

    /** 断路器注册表 */
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    /** 熔断器配置属性 */
    private final CircuitBreakerProperties properties;

    /**
     * 构造断路器服务。
     *
     * @param circuitBreakerRegistry 断路器注册表
     * @param properties 熔断器配置属性
     */
    public CircuitBreakerService(CircuitBreakerRegistry circuitBreakerRegistry, CircuitBreakerProperties properties) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.properties = properties;
    }

    /**
     * 使用断路器保护 Mono 流
     *
     * <p>如果断路器不存在，则使用默认配置创建。
     *
     * @param <T>           返回值类型
     * @param name          断路器名称
     * @param mono          受保护的 Mono 流
     * @return 受保护的 Mono 流
     */
    public <T> Mono<T> withCircuitBreaker(String name, Mono<T> mono) {
        CircuitBreaker circuitBreaker = getOrCreateCircuitBreaker(name);
        return mono.transform(CircuitBreakerOperator.of(circuitBreaker))
                .doOnError(e -> log.warn("断路器触发降级, name: {}, error: {}", name, e.getMessage()))
                .doOnSuccess(result -> log.debug("断路器正常通过, name: {}", name));
    }

    /**
     * 使用断路器保护 Mono 流（带降级函数）
     *
     * <p>当断路器打开或调用失败时，执行降级函数返回降级结果。
     *
     * @param <T>           返回值类型
     * @param name          断路器名称
     * @param mono          受保护的 Mono 流
     * @param fallback      降级函数
     * @return 受保护的 Mono 流，失败时返回降级结果
     */
    public <T> Mono<T> withCircuitBreaker(String name, Mono<T> mono, Mono<T> fallback) {
        return withCircuitBreaker(name, mono).onErrorResume(e -> {
            log.info("执行降级逻辑, name: {}", name);
            return fallback;
        });
    }

    /**
     * 获取或创建断路器
     *
     * <p>如果断路器已存在则返回，否则使用默认配置创建新的断路器。
     *
     * @param name 断路器名称
     * @return 断路器实例
     */
    public CircuitBreaker getOrCreateCircuitBreaker(String name) {
        return circuitBreakerRegistry.circuitBreaker(name, this::createDefaultConfig);
    }

    /**
     * 创建自定义配置的断路器
     *
     * @param name   断路器名称
     * @param config 断路器配置
     * @return 断路器实例
     */
    public CircuitBreaker createCircuitBreaker(String name, CircuitBreakerConfig config) {
        log.info("创建自定义断路器, name: {}", name);
        return circuitBreakerRegistry.circuitBreaker(name, config);
    }

    /**
     * 获取已存在的断路器
     *
     * @param name 断路器名称
     * @return 断路器实例，如果不存在则返回null
     */
    public CircuitBreaker getCircuitBreaker(String name) {
        return circuitBreakerRegistry.find(name).orElse(null);
    }

    /**
     * 重置指定断路器
     *
     * <p>将断路器状态重置为 CLOSED，强制关闭断路器。
     *
     * @param name 断路器名称
     */
    public void resetCircuitBreaker(String name) {
        CircuitBreaker circuitBreaker = getCircuitBreaker(name);
        if (circuitBreaker != null) {
            circuitBreaker.reset();
            log.info("断路器已重置, name: {}", name);
        }
    }

    /**
     * 强制打开断路器
     *
     * <p>手动将断路器设置为 OPEN 状态，所有请求将直接失败。
     *
     * @param name 断路器名称
     */
    public void forceOpen(String name) {
        CircuitBreaker circuitBreaker = getCircuitBreaker(name);
        if (circuitBreaker != null) {
            circuitBreaker.transitionToOpenState();
            log.info("断路器已强制打开, name: {}", name);
        }
    }

    /**
     * 强制关闭断路器
     *
     * <p>手动将断路器设置为 CLOSED 状态，允许所有请求通过。
     *
     * @param name 断路器名称
     */
    public void forceClosed(String name) {
        CircuitBreaker circuitBreaker = getCircuitBreaker(name);
        if (circuitBreaker != null) {
            circuitBreaker.transitionToClosedState();
            log.info("断路器已强制关闭, name: {}", name);
        }
    }

    /**
     * 获取断路器状态信息
     *
     * @param name 断路器名称
     * @return 断路器状态信息
     */
    public CircuitBreakerState getCircuitBreakerState(String name) {
        CircuitBreaker circuitBreaker = getCircuitBreaker(name);
        if (circuitBreaker == null) {
            return null;
        }

        CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
        long totalCalls = metrics.getNumberOfSuccessfulCalls() + metrics.getNumberOfFailedCalls();
        return CircuitBreakerState.builder()
                .name(name)
                .state(circuitBreaker.getState().name())
                .failureRate(metrics.getFailureRate())
                .successRate(100 - metrics.getFailureRate())
                .totalCalls(totalCalls)
                .failedCalls(metrics.getNumberOfFailedCalls())
                .slowCalls(metrics.getNumberOfSlowCalls())
                .build();
    }

    /**
     * 创建默认断路器配置。
     *
     * <p>参数来源于 {@link CircuitBreakerProperties#getDefaultConfig()}，
     * 未显式配置时回退到代码中的合理默认值。
     *
     * <ul>
     *   <li><b>异常过滤</b>：仅记录 IOException、TimeoutException 和 5xx HTTP 错误，
     *       排除业务异常（BusinessException），避免业务错误不必要地触发熔断</li>
     * </ul>
     *
     * @return 默认配置
     */
    private CircuitBreakerConfig createDefaultConfig() {
        CircuitBreakerProperties.ModeConfig dc = properties.getDefaultConfig();
        return CircuitBreakerConfig.custom()
                .failureRateThreshold(dc.getFailureRateThreshold())
                .waitDurationInOpenState(dc.getWaitDurationInOpenState())
                .slidingWindowSize(dc.getSlidingWindowSize())
                .minimumNumberOfCalls(dc.getMinimumNumberOfCalls())
                .permittedNumberOfCallsInHalfOpenState(dc.getPermittedCallsInHalfOpen())
                .recordException(e -> !(e instanceof com.apex.scaffold.common.exception.BusinessException))
                .build();
    }

    /**
     * 创建严格模式断路器配置。
     *
     * <p>参数来源于 {@link CircuitBreakerProperties#getStrict()}。
     *
     * @return 严格模式配置
     */
    public CircuitBreakerConfig createStrictConfig() {
        CircuitBreakerProperties.ModeConfig sc = properties.getStrict();
        return CircuitBreakerConfig.custom()
                .failureRateThreshold(sc.getFailureRateThreshold())
                .waitDurationInOpenState(sc.getWaitDurationInOpenState())
                .slidingWindowSize(sc.getSlidingWindowSize())
                .minimumNumberOfCalls(sc.getMinimumNumberOfCalls())
                .permittedNumberOfCallsInHalfOpenState(sc.getPermittedCallsInHalfOpen())
                .recordException(e -> true)
                .build();
    }

    /**
     * 创建宽松模式断路器配置。
     *
     * <p>参数来源于 {@link CircuitBreakerProperties#getLenient()}。
     *
     * @return 宽松模式配置
     */
    public CircuitBreakerConfig createLenientConfig() {
        CircuitBreakerProperties.ModeConfig lc = properties.getLenient();
        return CircuitBreakerConfig.custom()
                .failureRateThreshold(lc.getFailureRateThreshold())
                .waitDurationInOpenState(lc.getWaitDurationInOpenState())
                .slidingWindowSize(lc.getSlidingWindowSize())
                .minimumNumberOfCalls(lc.getMinimumNumberOfCalls())
                .permittedNumberOfCallsInHalfOpenState(lc.getPermittedCallsInHalfOpen())
                .recordException(e -> true)
                .build();
    }

    /**
     * 断路器状态快照。
     *
     * <p>包含断路器当前的运行状态和核心指标，用于监控和诊断。
     */
    public static class CircuitBreakerState {
        /** 断路器名称 */
        private String name;

        /** 当前状态（CLOSED/OPEN/HALF_OPEN） */
        private String state;

        /** 失败率（百分比） */
        private double failureRate;

        /** 成功率（百分比） */
        private double successRate;

        /** 总调用次数 */
        private long totalCalls;

        /** 失败调用次数 */
        private long failedCalls;

        /** 慢调用次数 */
        private long slowCalls;

        /**
         * 创建熔断器状态构建器。
         *
         * <p>手动 builder（Lombok {@code @Builder} 在某些场景不生成静态内部类方法）。</p>
         *
         * @return 新的熔断器状态构建器实例
         */
        public static CircuitBreakerStateBuilder builder() {
            return new CircuitBreakerStateBuilder();
        }

        public static class CircuitBreakerStateBuilder {
            private String name;
            private String state;
            private double failureRate;
            private double successRate;
            private long totalCalls;
            private long failedCalls;
            private long slowCalls;

            /**
             * name
             *
             * @param name 参数
             *
             * @return 结果
             */
            public CircuitBreakerStateBuilder name(String name) {
                this.name = name;
                return this;
            }

            /**
             * state
             *
             * @param state 参数
             *
             * @return 结果
             */
            public CircuitBreakerStateBuilder state(String state) {
                this.state = state;
                return this;
            }

            /**
             * 失败ureRate
             *
             * @param failureRate 参数
             *
             * @return 结果
             */
            public CircuitBreakerStateBuilder failureRate(double failureRate) {
                this.failureRate = failureRate;
                return this;
            }

            /**
             * 成功Rate
             *
             * @param successRate 参数
             *
             * @return 结果
             */
            public CircuitBreakerStateBuilder successRate(double successRate) {
                this.successRate = successRate;
                return this;
            }

            /**
             * totalCalls
             *
             * @param totalCalls 参数
             *
             * @return 结果
             */
            public CircuitBreakerStateBuilder totalCalls(long totalCalls) {
                this.totalCalls = totalCalls;
                return this;
            }

            /**
             * 失败edCalls
             *
             * @param failedCalls 参数
             *
             * @return 结果
             */
            public CircuitBreakerStateBuilder failedCalls(long failedCalls) {
                this.failedCalls = failedCalls;
                return this;
            }

            /**
             * slowCalls
             *
             * @param slowCalls 参数
             *
             * @return 结果
             */
            public CircuitBreakerStateBuilder slowCalls(long slowCalls) {
                this.slowCalls = slowCalls;
                return this;
            }

            /**
             * 构建
             *
             * @return 结果
             */
            public CircuitBreakerState build() {
                CircuitBreakerState s = new CircuitBreakerState();
                s.name = name;
                s.state = state;
                s.failureRate = failureRate;
                s.successRate = successRate;
                s.totalCalls = totalCalls;
                s.failedCalls = failedCalls;
                s.slowCalls = slowCalls;
                return s;
            }
        }
    }
}
