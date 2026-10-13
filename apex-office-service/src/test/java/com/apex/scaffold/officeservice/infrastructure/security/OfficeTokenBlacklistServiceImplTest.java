package com.apex.scaffold.officeservice.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.config.RedisKeyPrefix;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("OfficeTokenBlacklistServiceImpl 单元测试")
class OfficeTokenBlacklistServiceImplTest {

    private ReactiveStringRedisTemplate redisTemplate;
    private ReactiveValueOperations<String, String> valueOperations;
    private RedisKeyPrefix redisKeyPrefix;
    private OfficeTokenBlacklistServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(ReactiveStringRedisTemplate.class);
        valueOperations = mock(ReactiveValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        redisKeyPrefix = mock(RedisKeyPrefix.class);
        service = new OfficeTokenBlacklistServiceImpl(redisTemplate, redisKeyPrefix);
    }

    private static final long DEFAULT_TTL = 604800000L;

    @Nested
    @DisplayName("add")
    class AddTest {

        @Test
        @DisplayName("空白 token 返回空 Mono 且不访问 Redis")
        void blankToken() {
            StepVerifier.create(service.add("  ", 1000L)).verifyComplete();
            verify(redisTemplate, never()).opsForValue();
        }

        @Test
        @DisplayName("null token 返回空 Mono")
        void nullToken() {
            StepVerifier.create(service.add(null, 1000L)).verifyComplete();
            verify(redisTemplate, never()).opsForValue();
        }

        @Test
        @DisplayName("正数过期时间使用指定 TTL")
        void positiveExpire() {
            when(redisKeyPrefix.wrap("token:blacklist:abc")).thenReturn("v:token:blacklist:abc");
            when(valueOperations.set(eq("v:token:blacklist:abc"), eq("1"), eq(Duration.ofMillis(5000L))))
                    .thenReturn(Mono.just(true));

            StepVerifier.create(service.add("abc", 5000L)).verifyComplete();
            verify(valueOperations).set(eq("v:token:blacklist:abc"), eq("1"), eq(Duration.ofMillis(5000L)));
        }

        @Test
        @DisplayName("非正数过期时间退化为默认 7 天")
        void defaultExpire() {
            when(redisKeyPrefix.wrap("token:blacklist:abc")).thenReturn("token:blacklist:abc");
            when(valueOperations.set(eq("token:blacklist:abc"), eq("1"), eq(Duration.ofMillis(DEFAULT_TTL))))
                    .thenReturn(Mono.just(true));

            StepVerifier.create(service.add("abc", 0L)).verifyComplete();
            verify(valueOperations).set(eq("token:blacklist:abc"), eq("1"), eq(Duration.ofMillis(DEFAULT_TTL)));
        }
    }

    @Nested
    @DisplayName("isBlacklisted")
    class IsBlacklistedTest {

        @Test
        @DisplayName("空白 token 返回 false")
        void blankToken() {
            StepVerifier.create(service.isBlacklisted("  ")).expectNext(false).verifyComplete();
            verify(redisTemplate, never()).opsForValue();
        }

        @Test
        @DisplayName("null token 返回 false")
        void nullToken() {
            StepVerifier.create(service.isBlacklisted(null)).expectNext(false).verifyComplete();
        }

        @Test
        @DisplayName("键存在返回 true")
        void present() {
            when(redisKeyPrefix.wrap("token:blacklist:abc")).thenReturn("token:blacklist:abc");
            when(valueOperations.get("token:blacklist:abc")).thenReturn(Mono.just("1"));

            StepVerifier.create(service.isBlacklisted("abc")).expectNext(true).verifyComplete();
        }

        @Test
        @DisplayName("键为空返回 false")
        void absent() {
            when(redisKeyPrefix.wrap("token:blacklist:abc")).thenReturn("token:blacklist:abc");
            when(valueOperations.get("token:blacklist:abc")).thenReturn(Mono.empty());

            StepVerifier.create(service.isBlacklisted("abc")).expectNext(false).verifyComplete();
        }
    }

    @Test
    @DisplayName("默认 TTL 常量为 7 天")
    void defaultTtlConstant() {
        assertThat(DEFAULT_TTL).isEqualTo(604800000L);
    }
}
