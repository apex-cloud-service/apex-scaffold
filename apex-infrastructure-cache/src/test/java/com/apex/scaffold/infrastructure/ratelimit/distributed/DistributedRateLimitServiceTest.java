package com.apex.scaffold.infrastructure.ratelimit.distributed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 分布式限流服务单元测试
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.1.0
 */
@ExtendWith(MockitoExtension.class)
class DistributedRateLimitServiceTest {

    @Mock
    private DistributedTokenBucketRateLimiter rateLimiter;

    private DistributedRateLimitProperties properties;
    private DistributedRateLimitService service;

    @BeforeEach
    void setUp() {
        properties = new DistributedRateLimitProperties();
        properties.setEnabled(true);
        properties.setFallbackToLocal(true);

        // 添加测试规则
        DistributedRateLimitProperties.RateLimitRule rule = new DistributedRateLimitProperties.RateLimitRule();
        rule.setResource("/api/v1/users");
        rule.setCapacity(1000);
        rule.setRefillRate(100);
        rule.setEnabled(true);
        properties.setRules(List.of(rule));

        service = new DistributedRateLimitService(rateLimiter, properties);
    }

    @Test
    void testTryAcquire_WithConfiguredRule_Success() {
        when(rateLimiter.tryAcquire(anyString(), anyLong(), anyDouble(), anyInt()))
                .thenReturn(Mono.just(true));

        StepVerifier.create(service.tryAcquire("/api/v1/users"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void testTryAcquire_WithConfiguredRule_Rejected() {
        when(rateLimiter.tryAcquire(anyString(), anyLong(), anyDouble(), anyInt()))
                .thenReturn(Mono.just(false));

        StepVerifier.create(service.tryAcquire("/api/v1/users"))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void testTryAcquire_NoRule_AllowByDefault() {
        StepVerifier.create(service.tryAcquire("/api/v1/unknown"))
                .expectNext(true) // 未配置规则，默认放行
                .verifyComplete();
    }

    @Test
    void testTryAcquire_RedisError_FallbackToLocal() {
        when(rateLimiter.tryAcquire(anyString(), anyLong(), anyDouble(), anyInt()))
                .thenReturn(Mono.error(new RuntimeException("Redis error")));

        properties.setFallbackToLocal(true);

        StepVerifier.create(service.tryAcquire("/api/v1/users"))
                .expectNext(true) // 降级到本地，暂时放行
                .verifyComplete();
    }

    @Test
    void testTryAcquire_RedisError_FallbackToLocal_RejectsAfterCapacity() {
        properties.getRules().get(0).setCapacity(2);
        when(rateLimiter.tryAcquire(anyString(), anyLong(), anyDouble(), anyInt()))
                .thenReturn(Mono.error(new RuntimeException("Redis error")));

        StepVerifier.create(service.tryAcquire("/api/v1/users").repeat(2))
                .expectNext(true, true, false)
                .verifyComplete();
    }

    @Test
    void testTryAcquire_RedisError_NoFallback_Reject() {
        when(rateLimiter.tryAcquire(anyString(), anyLong(), anyDouble(), anyInt()))
                .thenReturn(Mono.error(new RuntimeException("Redis error")));

        properties.setFallbackToLocal(false);

        StepVerifier.create(service.tryAcquire("/api/v1/users"))
                .expectNext(false) // 快速失败，拒绝请求
                .verifyComplete();
    }

    @Test
    void testGetAvailableTokens() {
        when(rateLimiter.getAvailableTokens(anyString())).thenReturn(Mono.just(500L));

        StepVerifier.create(service.getAvailableTokens("/api/v1/users"))
                .expectNext(500L)
                .verifyComplete();
    }

    @Test
    void testReset() {
        when(rateLimiter.reset(anyString())).thenReturn(Mono.just(true));

        StepVerifier.create(service.reset("/api/v1/users")).expectNext(true).verifyComplete();
    }

    @Test
    void testWarmup_WithConfiguredRule() {
        when(rateLimiter.warmup(anyString(), anyLong(), anyLong())).thenReturn(Mono.just(true));

        StepVerifier.create(service.warmup("/api/v1/users")).expectNext(true).verifyComplete();
    }

    @Test
    void testWarmup_NoRule_ReturnsFalse() {
        StepVerifier.create(service.warmup("/api/v1/unknown")).expectNext(false).verifyComplete();
    }

    @Test
    void testFindRule() {
        DistributedRateLimitProperties.RateLimitRule rule = properties.findRule("/api/v1/users");
        assertThat(rule).isNotNull();
        assertThat(rule.getResource()).isEqualTo("/api/v1/users");
        assertThat(rule.getCapacity()).isEqualTo(1000);
        assertThat(rule.getRefillRate()).isEqualTo(100);
    }

    @Test
    void testHasEnabledRules() {
        assertThat(properties.hasEnabledRules()).isTrue();

        // 禁用所有规则
        properties.getRules().forEach(rule -> rule.setEnabled(false));
        assertThat(properties.hasEnabledRules()).isFalse();
    }
}
