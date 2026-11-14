package com.apex.scaffold.gateway.lb;

import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 灰度标签负载均衡装配配置（全局默认，实例级路由）.
 *
 * <p>对应迁移方案 §15 灰度切流的装配环节：以 {@code @LoadBalancerClients(defaultConfiguration = ...)}
 * 将本配置注册为 Spring Cloud LoadBalancer 的**全局默认子上下文配置**，
 * 对全部 {@code lb://apex-*-service} 客户端生效（子上下文按 client 惰性创建）。
 *
 * <p>装配链路（以实际 API 为准，Spring Cloud 2025.0.0 / LoadBalancer 4.3.0）：
 * <pre>
 *   ServiceInstanceListSupplier.builder()
 *       .withDiscoveryClient()   // 基座：从 ReactiveDiscoveryClient 拉取实例
 *       .withCaching()           // 装饰：Caffeine/默认 实例清单缓存（无 CacheManager 时自动降级）
 *       .with(delegateCreator)   // 最外层：{@link GrayTagServiceInstanceListSupplier} 灰度过滤
 *       .build(ctx)
 * </pre>
 *
 * <p>因框架默认供应商 {@code discoveryClientServiceInstanceListSupplier} 标注
 * {@code @ConditionalOnMissingBean}，且用户 default 配置在子上下文中先于框架默认配置注册，
 * 本配置提供的 {@code ServiceInstanceListSupplier} 将优先装配，不产生重复 Bean。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Configuration(proxyBeanMethods = false)
@LoadBalancerClients(defaultConfiguration = GrayTagLoadBalancerConfiguration.class)
public class GrayTagLoadBalancerConfiguration {

    /**
     * 构建灰度标签感知的实例供应商（discovery + caching + gray-tag 过滤）.
     *
     * @param ctx 负载均衡子上下文（{@code LoadBalancerClientFactory} 按 client 创建）
     * @return 已按 {@code X-Gray-Tag} 装饰的 {@link ServiceInstanceListSupplier}
     */
    @Bean
    public ServiceInstanceListSupplier grayTagServiceInstanceListSupplier(ConfigurableApplicationContext ctx) {
        return ServiceInstanceListSupplier.builder()
                .withDiscoveryClient()
                .withCaching()
                .with((context, delegate) -> new GrayTagServiceInstanceListSupplier(delegate))
                .build(ctx);
    }
}
