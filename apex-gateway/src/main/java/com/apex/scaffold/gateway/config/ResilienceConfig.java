package com.apex.scaffold.gateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.common.circuitbreaker.configuration.CircuitBreakerConfigCustomizer;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 网关熔断（Resilience4j）装配：按路由覆盖阈值 + 状态迁移事件接入日志与计数指标.
 *
 * <p><b>为什么不用 {@code ReactiveResilience4JCircuitBreakerFactory#configure}</b>：
 * 网关路由的 {@code CircuitBreaker} 过滤器以实例名（{@code authService} 等）调用
 * {@code factory.create(id, group)}；而 {@code resilience4j-spring-boot3} 的
 * {@code CircuitBreakerConfiguration} 会在启动时把 {@code resilience4j.circuitbreaker.instances.*}
 * 预先注册进 {@code CircuitBreakerRegistry}。{@code create} 内部解析配置时
 * <em>优先</em>取 {@code registry.getConfiguration(id)}，仅在缺失时才回退到工厂内配置，
 * 即 <b>注册表命名配置覆盖工厂 {@code configure()}</b>。
 * 因此按路由覆盖阈值的正确钩子是 {@link CircuitBreakerConfigCustomizer} Bean
 * （由 {@code CompositeCustomizer} 在构建命名配置时按实例名回调），
 * 而非工厂 {@code configure()}。
 *
 * <p>本配置的职责：
 * <ul>
 *   <li>{@link #circuitBreakerOverrides()}：将 {@code apex.gateway.circuit-breaker.instances.*}
 *       的可覆盖参数落到命名实例（Nacos 热更新后重新绑定属性即可生效）。</li>
 *   <li>{@link #circuitBreakerEventConsumer(MeterRegistry)}：订阅状态迁移与「拒绝放行」事件，
 *       输出 WARN/INFO 日志并累计 Micrometer 计数指标，供 Prometheus 抓取与告警。</li>
 * </ul>
 *
 * <p>当 {@code apex.gateway.circuit-breaker.enabled=false} 时，上述 Bean 均不注册，
 * 熔断退化为不覆盖、不额外计量（Resilience4j 自身指标仍由 actuator 暴露）。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Configuration
@EnableConfigurationProperties(CircuitBreakerProperties.class)
public class ResilienceConfig {

    private static final Logger log = LoggerFactory.getLogger(ResilienceConfig.class);

    /** 状态迁移计数指标名 */
    private static final String METRIC_TRANSITION = "apex.gateway.circuitbreaker.transitions";

    /** 熔断 OPEN 拒绝放行计数指标名 */
    private static final String METRIC_REJECTED = "apex.gateway.circuitbreaker.rejected";

    /**
     * 按实例名生成熔断配置覆盖器.
     *
     * <p>仅在属性中显式声明的字段才覆盖默认值（{@code null} 表示沿用
     * {@code resilience4j.circuitbreaker.configs.default}）。
     *
     * @param properties 网关熔断覆盖属性
     * @return 熔断配置覆盖器列表（可能为空）
     */
    @Bean
    @ConditionalOnProperty(
            prefix = "apex.gateway.circuit-breaker",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    public List<CircuitBreakerConfigCustomizer> circuitBreakerOverrides(CircuitBreakerProperties properties) {
        List<CircuitBreakerConfigCustomizer> customizers = new ArrayList<>();
        for (Map.Entry<String, CircuitBreakerProperties.InstanceOverride> entry :
                properties.getInstances().entrySet()) {
            String name = entry.getKey();
            CircuitBreakerProperties.InstanceOverride override = entry.getValue();
            if (override == null) {
                continue;
            }
            customizers.add(CircuitBreakerConfigCustomizer.of(name, builder -> {
                applyOverride(builder, override);
                log.info("应用网关熔断实例覆盖配置 name={}", name);
            }));
        }
        if (customizers.isEmpty()) {
            log.info("未配置 apex.gateway.circuit-breaker.instances, 熔断沿用 resilience4j 默认配置");
        }
        return customizers;
    }

    /**
     * 熔断事件消费者：绑定状态迁移与拒绝放行事件到日志与计数指标.
     *
     * <p>命名实例在 {@code CircuitBreakerRegistry} 初始化阶段被注册时会触发
     * {@link EntryAddedEvent}，此时为每个实例订阅其事件发布器。
     *
     * @param meterRegistry 指标注册表
     * @return 熔断注册表事件消费者
     */
    @Bean
    @ConditionalOnProperty(
            prefix = "apex.gateway.circuit-breaker",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    public RegistryEventConsumer<CircuitBreaker> circuitBreakerEventConsumer(MeterRegistry meterRegistry) {
        return new RegistryEventConsumer<CircuitBreaker>() {
            @Override
            public void onEntryAddedEvent(EntryAddedEvent<CircuitBreaker> entryAddedEvent) {
                bindEvents(entryAddedEvent.getAddedEntry(), meterRegistry);
            }

            @Override
            public void onEntryRemovedEvent(
                    io.github.resilience4j.core.registry.EntryRemovedEvent<CircuitBreaker> event) {
                log.debug("熔断实例移除 name={}", event.getRemovedEntry().getName());
            }

            @Override
            public void onEntryReplacedEvent(
                    io.github.resilience4j.core.registry.EntryReplacedEvent<CircuitBreaker> event) {
                log.debug("熔断实例替换 name={}", event.getNewEntry().getName());
            }
        };
    }

    /**
     * 为单个熔断实例订阅状态迁移与拒绝放行事件.
     *
     * @param circuitBreaker 熔断实例
     * @param meterRegistry  指标注册表
     */
    private void bindEvents(CircuitBreaker circuitBreaker, MeterRegistry meterRegistry) {
        String name = circuitBreaker.getName();
        circuitBreaker
                .getEventPublisher()
                .onStateTransition(event -> {
                    String from = event.getStateTransition().getFromState().name();
                    String to = event.getStateTransition().getToState().name();
                    Counter.builder(METRIC_TRANSITION)
                            .description("Circuit breaker state transitions")
                            .tag("name", name)
                            .tag("from", from)
                            .tag("to", to)
                            .register(meterRegistry)
                            .increment();
                    if (CircuitBreaker.State.OPEN.name().equals(to)) {
                        log.warn("网关熔断开启 name={} {}->{}, 后续请求将走降级兜底", name, from, to);
                    } else if (CircuitBreaker.State.CLOSED.name().equals(to)) {
                        log.info("网关熔断恢复 name={} {}->{}", name, from, to);
                    } else {
                        log.info("网关熔断状态迁移 name={} {}->{}", name, from, to);
                    }
                })
                .onCallNotPermitted(event -> {
                    Counter.builder(METRIC_REJECTED)
                            .description("Calls rejected because circuit is open")
                            .tag("name", circuitBreaker.getName())
                            .register(meterRegistry)
                            .increment();
                    log.warn("网关熔断拒绝放行 name={}, 直接返回降级响应", circuitBreaker.getName());
                });
    }

    /**
     * 将单个实例的覆盖参数写入熔断配置构建器（仅覆盖非空字段）.
     *
     * @param builder  熔断配置构建器
     * @param override 覆盖参数
     */
    private void applyOverride(
            CircuitBreakerConfig.Builder builder, CircuitBreakerProperties.InstanceOverride override) {
        if (override.getSlidingWindowSize() != null) {
            builder.slidingWindowSize(override.getSlidingWindowSize());
        }
        if (override.getMinimumNumberOfCalls() != null) {
            builder.minimumNumberOfCalls(override.getMinimumNumberOfCalls());
        }
        if (override.getFailureRateThreshold() != null) {
            builder.failureRateThreshold(override.getFailureRateThreshold());
        }
        if (override.getSlowCallRateThreshold() != null) {
            builder.slowCallRateThreshold(override.getSlowCallRateThreshold());
        }
        if (override.getSlowCallDurationThreshold() != null) {
            builder.slowCallDurationThreshold(override.getSlowCallDurationThreshold());
        }
        if (override.getWaitDurationInOpenState() != null) {
            builder.waitDurationInOpenState(override.getWaitDurationInOpenState());
        }
        if (override.getPermittedNumberOfCallsInHalfOpenState() != null) {
            builder.permittedNumberOfCallsInHalfOpenState(override.getPermittedNumberOfCallsInHalfOpenState());
        }
    }
}
