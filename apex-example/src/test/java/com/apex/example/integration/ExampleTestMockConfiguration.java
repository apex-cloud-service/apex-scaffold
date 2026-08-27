package com.apex.example.integration;

import com.apex.scaffold.application.service.FileService;
import com.apex.scaffold.domain.repository.JobRepository;
import com.apex.scaffold.domain.service.AccountDeletionPort;
import com.apex.scaffold.domain.service.BusinessMetricsPort;
import com.apex.scaffold.domain.service.StorageService;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.mockito.Answers;
import org.mockito.Mockito;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 示例模块测试支持配置（复刻主应用 ItProfileMockRedisConfiguration 的 mock 集）。
 *
 * <p>为相关集成测试提供隔离的依赖、环境和安全配置，避免测试行为影响生产配置。</p>
 *
 * @author Apex Team
 */
@Configuration
@Profile("test")
public class ExampleTestMockConfiguration {

    /**
     * 执行 {@code redissonClient} 操作。
     * @return 操作结果
     */
    @Bean
    @Primary
    RedissonClient redissonClient() {
        return Mockito.mock(RedissonClient.class, Answers.RETURNS_DEEP_STUBS);
    }

    /**
     * 执行 {@code reactiveStringRedisTemplate} 操作。
     * @return 操作结果
     */
    @Bean
    @Primary
    ReactiveStringRedisTemplate reactiveStringRedisTemplate() {
        ReactiveStringRedisTemplate template = Mockito.mock(ReactiveStringRedisTemplate.class);
        Mockito.when(template.scan(Mockito.any(ScanOptions.class))).thenReturn(Flux.empty());
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
     * 执行 {@code accountDeletionPort} 操作。
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
     * 执行 {@code businessMetricsPort} 操作。
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
     * 执行 {@code fileService} 操作。
     * @return 操作结果
     */
    @Bean
    @Primary
    FileService fileService() {
        FileService service = Mockito.mock(FileService.class);
        Mockito.when(service.getUrl(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(Mono.just("http://localhost/mock-file-url"));
        Mockito.when(service.exists(Mockito.anyString(), Mockito.anyString())).thenReturn(Mono.just(false));
        Mockito.when(service.delete(Mockito.anyString(), Mockito.anyString())).thenReturn(Mono.just(true));
        return service;
    }

    /**
     * 执行 {@code storageService} 操作。
     * @return 操作结果
     */
    @Bean
    @Primary
    StorageService storageService() {
        return new StorageService() {
            /** {@inheritDoc} */
            @Override
            public Mono<String> upload(
                    String bucketName, String objectName, InputStream inputStream, String contentType) {
                return Mono.error(new UnsupportedOperationException());
            }

            /** {@inheritDoc} */
            @Override
            public Mono<InputStream> download(String bucketName, String objectName) {
                return Mono.just(new ByteArrayInputStream("fake-face".getBytes(StandardCharsets.UTF_8)));
            }

            /** {@inheritDoc} */
            @Override
            public Mono<Boolean> delete(String bucketName, String objectName) {
                return Mono.just(true);
            }

            /** {@inheritDoc} */
            @Override
            public Mono<String> getPresignedUrl(String bucketName, String objectName, Integer expiry) {
                return Mono.just("http://localhost/mock-presigned-url");
            }

            /** {@inheritDoc} */
            @Override
            public Mono<Boolean> exists(String bucketName, String objectName) {
                return Mono.just(true);
            }

            /** {@inheritDoc} */
            @Override
            public String getPhotoUrlPrefix() {
                return "/uploads/apex";
            }
        };
    }
}
