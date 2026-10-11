package com.apex.scaffold.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * {@link TokenBucketRateLimitFilter} 单元测试.
 *
 * <p>覆盖 Task 2 的核心限流内核行为：Redis 令牌桶（Lua 原子脚本）超限返回 429、
 * Redis 异常降级放行并累计 fail-open 指标、放行后下游异常必须向上传播、
 * 固定窗口算法（{@code algorithm=fixed-window}）兼容。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TokenBucketRateLimitFilter 令牌桶限流内核测试")
class TokenBucketRateLimitFilterTest {

    /** 与 {@code GatewayRateLimitProperties.keyPrefix} + scope:identity + :path 拼接一致 */
    private static final String KEY = "apex:gateway:rl:tenant:acme:/api/v1/orders";

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOps;

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    private final GatewayRateLimitProperties properties = new GatewayRateLimitProperties();

    private TokenBucketRateLimitFilter filter;

    private GatewayFilterChain passthrough;

    @BeforeEach
    void setUp() {
        filter = new TokenBucketRateLimitFilter(redisTemplate, properties, meterRegistry);
        passthrough = exchange -> Mono.empty();
    }

    private MockServerWebExchange exchange() {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders").build());
    }

    @Nested
    @DisplayName("令牌桶算法")
    class TokenBucketTests {

        @Test
        @DisplayName("令牌耗尽时应返回 429 且带 Retry-After / X-RateLimit-Remaining 头")
        void rejectsWith429WhenTokensExhausted() {
            when(redisTemplate.execute(any(RedisScript.class), anyList(), anyList()))
                    .thenReturn(Flux.just(0L));

            MockServerWebExchange ex = exchange();

            StepVerifier.create(filter.apply(ex, passthrough, KEY, 1000, 100)).verifyComplete();

            assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(ex.getResponse().getHeaders().getFirst("Retry-After")).isNotBlank();
            assertThat(ex.getResponse().getHeaders().getFirst("X-RateLimit-Remaining"))
                    .isEqualTo("0");
            assertThat(meterRegistry.counter("apex.gateway.ratelimit.rejected").count())
                    .isEqualTo(1.0d);
        }

        @Test
        @DisplayName("令牌充足时应放行且不写响应状态")
        void allowsWhenTokensAvailable() {
            when(redisTemplate.execute(any(RedisScript.class), anyList(), anyList()))
                    .thenReturn(Flux.just(1L));

            MockServerWebExchange ex = exchange();

            StepVerifier.create(filter.apply(ex, passthrough, KEY, 1000, 100)).verifyComplete();

            assertThat(ex.getResponse().getStatusCode()).isNull();
        }

        @Test
        @DisplayName("Redis 异常时应降级放行并累计 fail-open 指标")
        void failsOpenWhenRedisError() {
            when(redisTemplate.execute(any(RedisScript.class), anyList(), anyList()))
                    .thenReturn(Flux.error(new RuntimeException("redis down")));

            MockServerWebExchange ex = exchange();

            StepVerifier.create(filter.apply(ex, passthrough, KEY, 1000, 100)).verifyComplete();

            assertThat(ex.getResponse().getStatusCode()).isNull();
            assertThat(meterRegistry.counter("apex.gateway.ratelimit.failopen").count())
                    .isEqualTo(1.0d);
        }

        @Test
        @DisplayName("放行后下游异常必须向上传播，不得被降级吞掉")
        void propagatesDownstreamErrorWhenAllowed() {
            when(redisTemplate.execute(any(RedisScript.class), anyList(), anyList()))
                    .thenReturn(Flux.just(1L));

            GatewayFilterChain failing = ex -> Mono.error(new RuntimeException("downstream connection refused"));

            StepVerifier.create(filter.apply(exchange(), failing, KEY, 1000, 100))
                    .expectErrorMatches(err -> err.getMessage().contains("connection refused"))
                    .verify();
        }

        @Test
        @DisplayName("tryAcquire 应透传 Redis 错误（由 apply 层统一降级）")
        void tryAcquirePropagatesRedisError() {
            when(redisTemplate.execute(any(RedisScript.class), anyList(), anyList()))
                    .thenReturn(Flux.error(new RuntimeException("redis down")));

            StepVerifier.create(filter.tryAcquire(KEY, 1000, 100)).expectError().verify();
        }
    }

    @Nested
    @DisplayName("固定窗口算法（algorithm=fixed-window）")
    class FixedWindowTests {

        @BeforeEach
        void useFixedWindow() {
            properties.setAlgorithm("fixed-window");
            lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        }

        @Test
        @DisplayName("窗口计数超容量应返回 429")
        void rejectsWhenOverCapacity() {
            when(valueOps.increment(anyString())).thenReturn(Mono.just(11L));

            MockServerWebExchange ex = exchange();

            StepVerifier.create(filter.apply(ex, passthrough, KEY, 10, 100)).verifyComplete();

            assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        }

        @Test
        @DisplayName("窗口内未超容量应放行")
        void allowsWhenUnderCapacity() {
            when(valueOps.increment(anyString())).thenReturn(Mono.just(1L));
            lenient()
                    .when(redisTemplate.expire(anyString(), any(java.time.Duration.class)))
                    .thenReturn(Mono.just(true));

            MockServerWebExchange ex = exchange();

            StepVerifier.create(filter.apply(ex, passthrough, KEY, 10, 100)).verifyComplete();

            assertThat(ex.getResponse().getStatusCode()).isNull();
        }
    }
}
