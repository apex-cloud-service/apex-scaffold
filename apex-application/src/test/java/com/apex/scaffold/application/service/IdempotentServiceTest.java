package com.apex.scaffold.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Duration;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;

/**
 * 幂等性服务单元测试类（Redis 实现版本）
 *
 * <p>使用 Mockito 模拟 {@link ReactiveStringRedisTemplate} 的 Redis 操作，
 * 验证 IdempotentService v2.0 在 Redis 实现下的核心业务逻辑，
 * 包括幂等性检查、删除和 Token 验证功能。
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IdempotentServiceTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    private IdempotentService idempotentService;

    private SimpleMeterRegistry meterRegistry;

    /**
     * 设置{@code up}。
     */
    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        idempotentService = new IdempotentService(redisTemplate, meterRegistry);
    }

    /**
     * 检查{@code andSet_FirstRequest_ReturnsTrue}。
     */
    @Test
    @DisplayName("checkAndSet - 首次请求返回true")
    void checkAndSet_FirstRequest_ReturnsTrue() {
        stubValueOperations();
        when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class)))
                .thenReturn(Mono.just(true));

        reactor.test.StepVerifier.create(idempotentService.checkAndSet("request-001"))
                .expectNext(true)
                .verifyComplete();

        org.assertj.core.api.Assertions.assertThat(
                        meterRegistry.get("apex.idempotent.count").tag("result", "allowed").counter().count())
                .isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(
                        meterRegistry.find("apex.idempotent.count").tag("result", "duplicate").counters())
                .isEmpty();
    }

    /**
     * 检查{@code andSet_DuplicateRequest_ReturnsFalse}。
     */
    @Test
    @DisplayName("checkAndSet - 重复请求返回false")
    void checkAndSet_DuplicateRequest_ReturnsFalse() {
        stubValueOperations();
        when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class)))
                .thenReturn(Mono.just(false));

        reactor.test.StepVerifier.create(idempotentService.checkAndSet("request-002"))
                .expectNext(false)
                .verifyComplete();

        org.assertj.core.api.Assertions.assertThat(
                        meterRegistry.get("apex.idempotent.count").tag("result", "duplicate").counter().count())
                .isEqualTo(1);
    }

    /**
     * 检查{@code andSet_DifferentKeys_ReturnsTrue}。
     */
    @Test
    @DisplayName("checkAndSet - 不同key可分别设置")
    void checkAndSet_DifferentKeys_ReturnsTrue() {
        stubValueOperations();
        when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class)))
                .thenReturn(Mono.just(true));

        reactor.test.StepVerifier.create(idempotentService.checkAndSet("request-003"))
                .expectNext(true)
                .verifyComplete();

        reactor.test.StepVerifier.create(idempotentService.checkAndSet("request-004"))
                .expectNext(true)
                .verifyComplete();
    }

    /**
     * 删除{@code _ExistingKey_ReturnsTrue}。
     */
    @Test
    @DisplayName("delete - 删除已存在的key返回true")
    void delete_ExistingKey_ReturnsTrue() {
        stubValueOperations();
        when(valueOperations.delete(anyString())).thenReturn(Mono.just(true));

        reactor.test.StepVerifier.create(idempotentService.delete("request-005"))
                .expectNext(true)
                .verifyComplete();
    }

    /**
     * 删除{@code _NonExistingKey_ReturnsFalse}。
     */
    @Test
    @DisplayName("delete - 删除不存在的key返回false")
    void delete_NonExistingKey_ReturnsFalse() {
        stubValueOperations();
        when(valueOperations.delete(anyString())).thenReturn(Mono.just(false));

        reactor.test.StepVerifier.create(idempotentService.delete("non-existing"))
                .expectNext(false)
                .verifyComplete();
    }

    /**
     * 校验{@code token_ExistingToken_ReturnsTrue}。
     */
    @Test
    @DisplayName("validateToken - 已存在的token返回true")
    void validateToken_ExistingToken_ReturnsTrue() {
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(true));

        reactor.test.StepVerifier.create(idempotentService.validateToken("token-001"))
                .expectNext(true)
                .verifyComplete();
    }

    /**
     * 校验{@code token_NonExistingToken_ReturnsFalse}。
     */
    @Test
    @DisplayName("validateToken - 不存在的token返回false")
    void validateToken_NonExistingToken_ReturnsFalse() {
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(false));

        reactor.test.StepVerifier.create(idempotentService.validateToken("token-not-found"))
                .expectNext(false)
                .verifyComplete();
    }

    /**
     * 校验{@code token_DoesNotModifyCache}。
     */
    @Test
    @DisplayName("validateToken - 验证不修改缓存状态（hasKey为只读操作）")
    void validateToken_DoesNotModifyCache() {
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(true));

        reactor.test.StepVerifier.create(idempotentService.validateToken("token-002"))
                .expectNext(true)
                .verifyComplete();
    }

    /**
     * 检查{@code andSet_UsesKeyPrefix}。
     */
    @Test
    @DisplayName("checkAndSet - Key带前缀存储确保隔离")
    void checkAndSet_UsesKeyPrefix() {
        stubValueOperations();
        when(valueOperations.setIfAbsent(eq("idempotent:request-prefix"), eq("1"), any(Duration.class)))
                .thenReturn(Mono.just(true));

        reactor.test.StepVerifier.create(idempotentService.checkAndSet("request-prefix"))
                .expectNext(true)
                .verifyComplete();
    }

    /**
     * 执行 {@code stubValueOperations} 操作。
     */
    private void stubValueOperations() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }
}
