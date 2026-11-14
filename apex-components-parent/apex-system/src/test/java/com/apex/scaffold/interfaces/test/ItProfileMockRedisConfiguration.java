package com.apex.scaffold.interfaces.test;

import com.apex.scaffold.common.context.DataScopeProvider;
import com.apex.scaffold.common.context.ReactiveDataScopeProvider;
import com.apex.scaffold.domain.service.AccountDeletionPort;
import com.apex.scaffold.domain.service.BusinessMetricsPort;
import com.apex.scaffold.infrastructure.config.LokiConfig;
import com.apex.scaffold.infrastructure.config.MeilisearchConfig;
import com.meilisearch.sdk.Client;
import java.util.Optional;
import org.mockito.Answers;
import org.mockito.Mockito;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 集成测试环境统一配置 — IT Profile Mock/Stub 提供者
 *
 * <p>本配置类为 {@code @Profile("it")} 集成测试环境提供所有条件化依赖的 Mock 或 Stub 实现，
 * 确保 Spring ApplicationContext 可以完整启动，而无需真实外部基础设施（Redis、Loki、Meilisearch 等）。
 *
 * <h2>设计原则</h2>
 * <ul>
 *   <li><b>最小外部依赖</b>：只依赖本地 MySQL（apex_test）,所有其他外部服务均 mock</li>
 *   <li><b>系统性覆盖</b>：为所有 {@code @ConditionalOnProperty} 或 {@code @ConditionalOnClass} 的 Bean 提供替代</li>
 *   <li><b>行为一致性</b>：Mock 实现保持接口契约,返回合理的测试数据</li>
 *   <li><b>可维护性</b>：所有 mock 集中在此配置类,避免散落在各测试类</li>
 * </ul>
 *
 * <h2>Mock 清单</h2>
 * <ul>
 *   <li>Redis: {@link RedissonClient}, {@link ReactiveStringRedisTemplate}, {@link ReactiveRedisTemplate}</li>
 *   <li>Security: {@link PasswordEncoder}</li>
 *   <li>Data Scope: {@link DataScopeProvider}, {@link ReactiveDataScopeProvider}</li>
 *   <li>Business Ports: {@link AccountDeletionPort}, {@link BusinessMetricsPort}</li>
 *   <li>Logging: {@link LokiConfig.LokiProperties}, {@link WebClient} (Loki 客户端)</li>
 *   <li>Search: {@link MeilisearchConfig.MeilisearchProperties}, {@link Client} (Meilisearch 客户端)</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.2.0
 */
@TestConfiguration
@Profile("it")
public class ItProfileMockRedisConfiguration {

    // ========================================================================
    // Redis Mock Beans
    // ========================================================================

    @Bean
    @Primary
    public RedissonClient redissonClient() {
        return Mockito.mock(RedissonClient.class, Answers.RETURNS_DEEP_STUBS);
    }

    @Bean
    @Primary
    public ReactiveStringRedisTemplate reactiveStringRedisTemplate() {
        ReactiveStringRedisTemplate template = Mockito.mock(ReactiveStringRedisTemplate.class);
        Mockito.when(template.scan(Mockito.any(ScanOptions.class))).thenReturn(Flux.empty());
        return template;
    }

    @Bean
    @Primary
    public ReactiveRedisTemplate<String, Object> reactiveRedisTemplate() {
        return Mockito.mock(ReactiveRedisTemplate.class, Answers.RETURNS_DEEP_STUBS);
    }

    // ========================================================================
    // Security Beans
    // ========================================================================

    @Bean
    @Primary
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // ========================================================================
    // Data Scope Beans
    // ========================================================================

    @Bean
    @Primary
    public DataScopeProvider dataScopeProvider() {
        return Optional::empty;
    }

    @Bean
    @Primary
    public ReactiveDataScopeProvider reactiveDataScopeProvider() {
        return Mono::empty;
    }

    // ========================================================================
    // Business Port Beans
    // ========================================================================

    @Bean
    @Primary
    public AccountDeletionPort accountDeletionPort() {
        AccountDeletionPort port = Mockito.mock(AccountDeletionPort.class);
        Mockito.when(port.deactivateAndEnqueue(Mockito.any())).thenReturn(Mono.empty());
        Mockito.when(port.deactivateAndEnqueueByAdmin(Mockito.any())).thenReturn(Mono.empty());
        Mockito.when(port.cleanup(Mockito.any())).thenReturn(Mono.empty());
        return port;
    }

    @Bean
    @Primary
    public BusinessMetricsPort businessMetricsPort() {
        return Mockito.mock(BusinessMetricsPort.class);
    }

    // ========================================================================
    // Loki Logging Beans
    // ========================================================================

    /**
     * 为测试环境提供 Loki 配置属性 Bean
     *
     * <p>由于 {@link com.apex.scaffold.infrastructure.config.LokiConfig} 使用
     * {@code @ConditionalOnProperty(prefix = "loki", name = "enabled", havingValue = "true")},
     * 在测试环境（{@code loki.enabled=false}）下不会创建 {@link LokiConfig.LokiProperties} Bean。
     * 但 {@link com.apex.scaffold.infrastructure.log.LokiAdapter} 依赖该 Bean,
     * 导致集成测试 ApplicationContext 启动失败。
     *
     * <p>本方法提供测试专用的 {@link LokiConfig.LokiProperties} Mock,
     * 确保依赖链完整。Mock 配置使用默认值,不会实际连接 Loki 服务。
     *
     * @return Loki 配置属性 Mock
     */
    @Bean
    @Primary
    public LokiConfig.LokiProperties lokiProperties() {
        LokiConfig.LokiProperties properties = new LokiConfig.LokiProperties();
        properties.setUrl("http://localhost:3100");
        properties.setEnabled(false);
        properties.setMaxLines(1000);
        properties.setMaxQueryRangeSeconds(3600);
        return properties;
    }

    /**
     * 为测试环境提供 Loki WebClient Bean
     *
     * <p>由于 {@link com.apex.scaffold.infrastructure.config.LokiConfig} 在测试环境不生效,
     * {@code lokiWebClient} Bean 不会被创建。但 {@link com.apex.scaffold.infrastructure.log.LokiAdapter}
     * 依赖该 Bean,导致集成测试失败。
     *
     * <p>本方法提供测试专用的 Mock WebClient,所有 HTTP 请求返回空响应,
     * 不会实际连接 Loki 服务。
     *
     * @return Loki WebClient Mock
     */
    @Bean
    @Primary
    public WebClient lokiWebClient() {
        return Mockito.mock(WebClient.class, Answers.RETURNS_DEEP_STUBS);
    }

    // ========================================================================
    // Meilisearch Beans
    // ========================================================================

    /**
     * 为测试环境提供 Meilisearch 配置属性 Bean
     *
     * <p>由于 {@link com.apex.scaffold.infrastructure.config.MeilisearchConfig} 使用
     * {@code @ConditionalOnProperty(prefix = "meilisearch", name = "enabled", havingValue = "true")},
     * 在测试环境（{@code meilisearch.enabled=false}）下不会创建 {@link MeilisearchConfig.MeilisearchProperties} Bean。
     * 但 {@link com.apex.scaffold.infrastructure.search.MeilisearchAdapter} 依赖该 Bean,
     * 导致集成测试 ApplicationContext 启动失败。
     *
     * <p>本方法提供测试专用的 {@link MeilisearchConfig.MeilisearchProperties} Mock,
     * 确保依赖链完整。Mock 配置使用默认值,不会实际连接 Meilisearch 服务。
     *
     * @return Meilisearch 配置属性 Mock
     */
    @Bean
    @Primary
    public MeilisearchConfig.MeilisearchProperties meilisearchProperties() {
        MeilisearchConfig.MeilisearchProperties properties = new MeilisearchConfig.MeilisearchProperties();
        properties.setUrl("http://localhost:7700");
        properties.setApiKey("test-api-key");
        properties.setEnabled(false);
        properties.setIndexPrefix("test_");
        properties.setBatchSize(100);
        return properties;
    }

    /**
     * 为测试环境提供 Meilisearch Client Bean
     *
     * <p>由于 {@link com.apex.scaffold.infrastructure.config.MeilisearchConfig} 使用
     * {@code @ConditionalOnProperty(prefix = "meilisearch", name = "enabled", havingValue = "true")},
     * 在测试环境（{@code meilisearch.enabled=false}）下不会创建 {@link Client} Bean。
     * 但 {@link com.apex.scaffold.infrastructure.search.MeilisearchAdapter} 依赖该 Bean,
     * 导致集成测试 ApplicationContext 启动失败。
     *
     * <p>本方法提供测试专用的 Mock Meilisearch Client,
     * 所有索引和查询操作返回空结果,不会实际连接 Meilisearch 服务。
     *
     * @return Meilisearch Client Mock
     */
    @Bean
    @Primary
    public Client meilisearchClient() {
        return Mockito.mock(Client.class, Answers.RETURNS_DEEP_STUBS);
    }
}
