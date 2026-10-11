package com.apex.scaffold.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * {@link RequestRateLimitFilter} 单元测试.
 *
 * <p>覆盖迁移方案 §1.2.2 的全局限流行为：用户/IP 维度 Key 解析、路径叠加、固定窗口计数、
 * 429 触发出口、Redis 异常降级放行，以及过滤器顺序。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@DisplayName("RequestRateLimitFilter 限流过滤器测试")
class RequestRateLimitFilterTest {

    private KeyResolver ipKeyResolver;
    private KeyResolver userKeyResolver;
    private ReactiveStringRedisTemplate redisTemplate;
    private ReactiveValueOperations<String, String> valueOps;
    private RequestRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        ipKeyResolver = mock(KeyResolver.class);
        userKeyResolver = mock(KeyResolver.class);
        redisTemplate = mock(ReactiveStringRedisTemplate.class);
        valueOps = mock(ReactiveValueOperations.class);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        filter = new RequestRateLimitFilter(ipKeyResolver, userKeyResolver, redisTemplate, 10, 60_000L);
    }

    /**
     * 构造放行链路（不触发限流时调用）.
     */
    private GatewayFilterChain passthroughChain() {
        return exchange -> Mono.empty();
    }

    /**
     * 构造已认证请求（携带 X-User-Id，走用户维度）.
     */
    private MockServerWebExchange authedExchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/orders")
                .header("X-User-Id", "user-1001")
                .build());
    }

    @BeforeEach
    void stubKeyResolvers() {
        lenient().when(ipKeyResolver.resolve(any())).thenReturn(Mono.just("gateway:rl:ip:127.0.0.1"));
        lenient().when(userKeyResolver.resolve(any())).thenReturn(Mono.just("gateway:rl:user:user-1001"));
    }

    @Nested
    @DisplayName("过滤器顺序")
    class OrderTests {

        @Test
        @DisplayName("getOrder 应返回 30")
        void shouldReturnExpectedOrder() {
            assertThat(filter.getOrder())
                    .isEqualTo(RequestRateLimitFilter.ORDER)
                    .isEqualTo(30);
        }
    }

    @Nested
    @DisplayName("限流计数")
    class CountingTests {

        @Test
        @DisplayName("窗口内未超阈值应放行")
        void shouldPassWhenUnderLimit() {
            when(valueOps.increment(anyString())).thenReturn(Mono.just(1L));
            when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));

            MockServerWebExchange exchange = authedExchange();

            StepVerifier.create(filter.filter(exchange, passthroughChain())).verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isNull();
        }

        @Test
        @DisplayName("窗口计数超过容量应返回 429")
        void shouldReturn429WhenOverLimit() {
            when(valueOps.increment(anyString())).thenReturn(Mono.just(11L));
            when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));

            MockServerWebExchange exchange = authedExchange();

            StepVerifier.create(filter.filter(exchange, passthroughChain())).verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        }

        @Test
        @DisplayName("首次命中应设置窗口过期时间")
        void shouldSetExpireOnFirstHit() {
            when(valueOps.increment(anyString())).thenReturn(Mono.just(1L));
            when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));

            filter.filter(authedExchange(), passthroughChain()).block();

            verify(redisTemplate).expire(anyString(), any(Duration.class));
        }
    }

    @Nested
    @DisplayName("Key 维度")
    class KeyDimensionTests {

        @Test
        @DisplayName("已认证请求应优先使用用户维度 KeyResolver")
        void shouldUseUserResolverWhenAuthed() {
            when(valueOps.increment(anyString())).thenReturn(Mono.just(1L));
            when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));

            filter.filter(authedExchange(), passthroughChain()).block();

            verify(userKeyResolver).resolve(any(ServerWebExchange.class));
        }

        @Test
        @DisplayName("匿名请求应回退到 IP 维度 KeyResolver")
        void shouldUseIpResolverWhenAnonymous() {
            when(valueOps.increment(anyString())).thenReturn(Mono.just(1L));
            when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));

            MockServerWebExchange anonymous = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/v1/health").build());

            filter.filter(anonymous, passthroughChain()).block();

            verify(ipKeyResolver).resolve(any(ServerWebExchange.class));
        }
    }

    @Nested
    @DisplayName("异常降级")
    class FallbackTests {

        @Test
        @DisplayName("Redis 计数异常时应降级放行")
        void shouldFallbackOnRedisError() {
            when(valueOps.increment(anyString())).thenReturn(Mono.error(new RuntimeException("redis down")));

            MockServerWebExchange exchange = authedExchange();

            StepVerifier.create(filter.filter(exchange, passthroughChain())).verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isNull();
        }

        @Test
        @DisplayName("下游链路异常必须向上传播，不得被限流降级吞掉（否则熔断永远无法感知下游故障）")
        void shouldPropagateDownstreamErrorInsteadOfSwallowing() {
            when(valueOps.increment(anyString())).thenReturn(Mono.just(1L));
            when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));

            MockServerWebExchange exchange = authedExchange();
            GatewayFilterChain failingChain = ex -> Mono.error(new RuntimeException("downstream connection refused"));

            StepVerifier.create(filter.filter(exchange, failingChain))
                    .expectErrorMatches(err -> err.getMessage().contains("connection refused"))
                    .verify();
        }
    }
}
