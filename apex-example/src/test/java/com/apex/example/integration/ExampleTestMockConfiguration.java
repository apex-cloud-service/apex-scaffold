package com.apex.example.integration;

import com.apex.scaffold.domain.repository.JobRepository;
import com.apex.scaffold.domain.service.AccountDeletionPort;
import com.apex.scaffold.domain.service.BusinessMetricsPort;
import com.apex.scaffold.infrastructure.config.LokiConfig;
import com.apex.scaffold.infrastructure.config.MeilisearchConfig;
import com.meilisearch.sdk.Client;
import java.time.Duration;
import org.mockito.Answers;
import org.mockito.Mockito;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 示例模块测试支持配置（复刻主应用 ItProfileMockRedisConfiguration 的 mock 集）。
 *
 * <p>为相关集成测试提供隔离的依赖、环境和安全配置，避免测试行为影响生产配置。</p>
 *
 * @author Apex Team
 */
@TestConfiguration
@Profile("test")
public class ExampleTestMockConfiguration {
    @Bean
    @Primary
    RedissonClient redissonClient() {
        return Mockito.mock(RedissonClient.class, Answers.RETURNS_DEEP_STUBS);
    }

    /**
     *
     * <p>除 scan 外，为幂等切面（{@code IdempotentApiAspect}）与多级缓存
     * 打桩 value 操作：SETNX 成功、删除成功，保证写接口幂等示例
     * 在 Mock Redis 下返回 200。</p>
     *
     */
    @Bean
    @Primary
    ReactiveStringRedisTemplate reactiveStringRedisTemplate() {
        ReactiveStringRedisTemplate template = Mockito.mock(ReactiveStringRedisTemplate.class);
        Mockito.when(template.scan(Mockito.any(ScanOptions.class))).thenReturn(Flux.empty());

        // 打桩 value 操作：SETNX 幂等键成功、删除成功
        ReactiveValueOperations<String, String> valueOps = Mockito.mock(ReactiveValueOperations.class);
        Mockito.when(valueOps.setIfAbsent(Mockito.anyString(), Mockito.anyString(), Mockito.any(Duration.class)))
                .thenReturn(Mono.just(true));
        Mockito.when(valueOps.delete(Mockito.any())).thenReturn(Mono.just(true));
        Mockito.when(template.opsForValue()).thenReturn(valueOps);
        return template;
    }

    /** 为降级服务提供隔离的通用 Reactive Redis 模板。 */
    @Bean
    @Primary
    ReactiveRedisTemplate<String, Object> reactiveRedisTemplate() {
        return Mockito.mock(ReactiveRedisTemplate.class, Answers.RETURNS_DEEP_STUBS);
    }

    /**
     * 创建定时任务仓储替身。
     *
     * <p>返回空任务列表，使 {@code JobInitializer} 在测试环境不注册任何定时任务，
     * 避免依赖主应用业务表（sys_job）产生查询错误。</p>
     *
     * @return JobRepository Mockito 替身
     */
    @Bean
    @Primary
    JobRepository jobRepository() {
        JobRepository repo = Mockito.mock(JobRepository.class);
        Mockito.when(repo.findEnabled()).thenReturn(Mono.just(java.util.List.of()));
        return repo;
    }

    /**
     *
     * <p>账号注销持久化端口的实现随 v2.0 业务下线被移除，集成测试通过
     * 测试桩满足 Spring 依赖注入，不触发真实注销流程。</p>
     *
     * @return 空操作的账号注销端口桩
     */
    @Bean
    @Primary
    AccountDeletionPort accountDeletionPort() {
        AccountDeletionPort port = Mockito.mock(AccountDeletionPort.class);
        Mockito.when(port.deactivateAndEnqueue(Mockito.any())).thenReturn(Mono.empty());
        Mockito.when(port.deactivateAndEnqueueByAdmin(Mockito.any())).thenReturn(Mono.empty());
        Mockito.when(port.cleanup(Mockito.any())).thenReturn(Mono.empty());
        return port;
    }

    /**
     *
     * <p>业务指标端点的 Micrometer 实现随 v2.0 业务下线被移除，集成测试通过
     * 空桩满足 Spring 依赖注入。</p>
     *
     * @return 空操作的业务指标端口桩
     */
    @Bean
    @Primary
    BusinessMetricsPort businessMetricsPort() {
        return Mockito.mock(BusinessMetricsPort.class);
    }

    /**
     * 提供 Loki 配置属性替身。
     *
     * <p>{@code LokiConfig} 使用 {@code @ConditionalOnProperty(loki.enabled=true)}，
     * 测试环境不创建 {@link LokiConfig.LokiProperties} Bean；而无条件 {@code @Component}
     * {@code LokiAdapter} 依赖该 Bean，故需测试桩保证依赖链完整（默认不连接 Loki 服务）。</p>
     *
     * @return Loki 配置属性替身
     */
    @Bean
    @Primary
    LokiConfig.LokiProperties lokiProperties() {
        LokiConfig.LokiProperties properties = new LokiConfig.LokiProperties();
        properties.setUrl("http://localhost:3100");
        properties.setEnabled(false);
        properties.setMaxLines(1000);
        properties.setMaxQueryRangeSeconds(3600);
        return properties;
    }

    /**
     * 提供 @Primary WebClient 替身。
     *
     * <p>主应用存在多个具名 WebClient Bean（officeWebClient/chuanglanWebClient/
     * weChatApiWebClient/weWorkWebClient）且均未标注 {@code @Primary}，
     * 而办公平台组织客户端（Feishu/WeCom/DingTalk OrganizationClient）按类型注入
     * {@link WebClient}，导致 {@code NoUniqueBeanDefinitionException}。
     * 此处置一个 {@code @Primary} 替身作为唯一候选，满足注入且不会真实发出 HTTP 请求。</p>
     *
     * @return WebClient 替身
     */
    @Bean
    @Primary
    WebClient webClient() {
        return Mockito.mock(WebClient.class, Answers.RETURNS_DEEP_STUBS);
    }

    /**
     * 提供响应式事务操作器替身。
     *
     * <p>{@code TransactionalOperator} 仅当存在 {@code ReactiveTransactionManager}
     * （依赖 R2DBC 数据源配置）时由 Spring Boot 自动装配，而示例应用未配置 R2DBC，
     * 故测试环境不创建该 Bean；无条件 {@code @Service} {@code OfficeOrganizationSyncService}
     * 按类型注入它，需以 Mockito 替身满足依赖注入，不真实开启响应式事务。</p>
     *
     * @return TransactionalOperator 替身
     */
    @Bean
    @Primary
    TransactionalOperator transactionalOperator() {
        return Mockito.mock(TransactionalOperator.class, Answers.RETURNS_DEEP_STUBS);
    }

    /**
     * 提供 Meilisearch 配置属性替身。
     *
     * <p>{@code MeilisearchConfig} 使用 {@code @ConditionalOnProperty(meilisearch.enabled=true)}，
     * 测试环境不创建 {@link MeilisearchConfig.MeilisearchProperties} Bean；而无条件
     * {@code @Component} {@code MeilisearchAdapter} 依赖该 Bean，故需测试桩保证依赖链完整。</p>
     *
     * @return Meilisearch 配置属性替身
     */
    @Bean
    @Primary
    MeilisearchConfig.MeilisearchProperties meilisearchProperties() {
        MeilisearchConfig.MeilisearchProperties properties = new MeilisearchConfig.MeilisearchProperties();
        properties.setUrl("http://localhost:7700");
        properties.setApiKey("test-api-key");
        properties.setEnabled(false);
        properties.setIndexPrefix("test_");
        properties.setBatchSize(100);
        return properties;
    }

    /**
     * 提供 Meilisearch 客户端替身。
     *
     * <p>测试环境不创建真实 {@link Client} Bean（{@code meilisearch.enabled=false}），
     * 以 Mockito 替身满足 {@code MeilisearchAdapter} 的构造依赖，所有索引/查询返回空结果，
     * 不会实际连接 Meilisearch 服务。</p>
     *
     * @return Meilisearch 客户端替身
     */
    @Bean
    @Primary
    Client meilisearchClient() {
        return Mockito.mock(Client.class, Answers.RETURNS_DEEP_STUBS);
    }
}
