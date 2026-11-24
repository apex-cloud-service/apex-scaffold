package com.apex.scaffold.infrastructure.remote.config;

import com.apex.scaffold.infrastructure.chaos.ChaosInjectionService;
import com.apex.scaffold.infrastructure.remote.logging.ApiCallLogService;
import com.apex.scaffold.infrastructure.remote.service.RemoteApiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.resources.ConnectionProvider;

/**
 * {@link RemoteApiClient} 构造配置 —— 替代 {@code @Service} + 字段注入 的 Bean 定义方式。
 *
 * <p>所有依赖均通过构造器注入，不使用字段注入、{@code @Qualifier} 或 setter 注入。
 * 可选依赖通过 {@link ObjectProvider#getIfAvailable()} 安全获取。</p>
 *
 * <p>配置读取链路：
 * <ul>
 *   <li>{@code remote.api.compress-response} → {@code compressResponse} 构造参数</li>
 *   <li>{@link RemoteApiConnectionConfig#remoteApiConnectionProvider} → {@code connectionProvider}</li>
 *   <li>resilience4j 自动配置 → {@link CircuitBreakerRegistry}</li>
 *   <li>Spring Boot 自动配置 → {@link WebClient.Builder}</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.1.0
 */
@AutoConfiguration
@org.springframework.boot.context.properties.EnableConfigurationProperties(RemoteApiProperties.class)
@org.springframework.context.annotation.ComponentScan("com.apex.scaffold.infrastructure.remote.logging")
@Slf4j
public class RemoteApiClientConfig {

    /**
     * 创建 {@link RemoteApiClient} Bean，全部通过构造器注入。
     *
     * <p>连接池和熔断器注册表使用 {@link ObjectProvider} 安全获取，容器中不存在时为 {@code null}。
     * HTTP 响应压缩开关通过 {@code @Value} 读取 YAML 配置。</p>
     *
     * @param properties 远程 API 配置属性
     * @param webClientBuilder Spring Boot 自动配置的 WebClient 构建器
     * @param connectionProvider 共享连接池（可选，来自 {@link RemoteApiConnectionConfig}）
     * @param compressResponse 是否启用 HTTP 响应压缩
     * @param circuitBreakerRegistry resilience4j 熔断器注册表（可选，来自 resilience4j 自动配置）
     * @param logService API 调用日志服务
     * @param objectMapper JSON 序列化器，用于请求体日志记录
     * @param chaosInjectionService 混沌工程故障注入服务（可选，未注册时关闭注入）
     * @return 配置完成的 RemoteApiClient 实例
     */
    @Bean
    public RemoteApiClient remoteApiClient(
            RemoteApiProperties properties,
            WebClient.Builder webClientBuilder,
            ObjectProvider<ConnectionProvider> connectionProvider,
            @Value("${remote.api.compress-response:true}") boolean compressResponse,
            ObjectProvider<CircuitBreakerRegistry> circuitBreakerRegistry,
            ApiCallLogService logService,
            ObjectMapper objectMapper,
            ObjectProvider<ChaosInjectionService> chaosInjectionService) {

        ConnectionProvider pool = connectionProvider.getIfAvailable();
        CircuitBreakerRegistry cbRegistry = circuitBreakerRegistry.getIfAvailable();
        ChaosInjectionService chaos = chaosInjectionService.getIfAvailable();

        log.info(
                "创建 RemoteApiClient Bean: connectionPool={}, compressResponse={}, circuitBreaker={}, chaosInjection={}",
                pool != null ? "shared" : "none",
                compressResponse,
                cbRegistry != null ? "enabled" : "disabled",
                chaos != null ? "enabled" : "disabled");

        return new RemoteApiClient(
                properties, webClientBuilder, pool, compressResponse, cbRegistry, logService, objectMapper, chaos);
    }
}
