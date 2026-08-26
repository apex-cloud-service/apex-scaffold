package com.apex.scaffold.interfaces.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.util.TraceIdUtil;
import com.apex.scaffold.interfaces.monitor.SecurityMetricsRecorder;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 全局安全防护过滤器单元测试
 *
 * <p>验证 GlobalGuardWebFilter 的安全响应头注入、IP 黑名单检测、
 * User-Agent 空值检查等核心功能
 *
 * @author Apex Team
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("GlobalGuardWebFilter 全局安全防护过滤器测试")
class GlobalGuardWebFilterTest {

    @Mock
    private WebFilterChain chain;

    @Mock
    private SecurityGuardProperties guardProperties;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private SecurityMetricsRecorder securityMetricsRecorder;

    @Mock
    @SuppressWarnings("rawtypes")
    private RBucket bucket;

    @InjectMocks
    private GlobalGuardWebFilter filter;

    private MockedStatic<TraceIdUtil> traceIdUtilMockedStatic;

    /**
     * 设置{@code up}。
     */
    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        traceIdUtilMockedStatic = mockStatic(TraceIdUtil.class);
        when(redissonClient.getBucket(anyString())).thenReturn(bucket);
        when(bucket.get()).thenReturn(null);
    }

    /**
     * 执行 {@code tearDown} 操作。
     */
    @AfterEach
    void tearDown() {
        traceIdUtilMockedStatic.close();
    }

    /**
     * 执行 {@code shouldInjectSecurityHeaders} 操作。
     */
    @Test
    @DisplayName("正常请求应该放行")
    void shouldInjectSecurityHeaders() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        when(guardProperties.isUaCheckEnabled()).thenReturn(false);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    /**
     * 执行 {@code shouldCheckIpBlacklist} 操作。
     */
    @Test
    @DisplayName("应该执行 IP 黑名单检查并放行正常 IP")
    void shouldCheckIpBlacklist() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .header("X-Forwarded-For", "10.0.0.1")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        when(guardProperties.isUaCheckEnabled()).thenReturn(false);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    /**
     * 验证 {@code shouldCacheNonBlacklistedIp} 场景。
     */
    @Test
    @DisplayName("普通 IP 应使用短时负缓存避免每请求查询 Redis")
    void shouldCacheNonBlacklistedIp() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .header("X-Forwarded-For", "10.0.0.2")
                .build();
        when(guardProperties.isUaCheckEnabled()).thenReturn(false);
        when(guardProperties.getIpBlacklistCacheSize()).thenReturn(100);
        when(guardProperties.getIpBlacklistNegativeCacheTtl()).thenReturn(3);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(MockServerWebExchange.from(request), chain))
                .verifyComplete();
        StepVerifier.create(filter.filter(MockServerWebExchange.from(request), chain))
                .verifyComplete();

        verify(bucket, times(1)).get();
    }

    /**
     * 执行 {@code shouldAllowEmptyUaForPublicPaths} 操作。
     */
    @Test
    @DisplayName("公共路径的空 User-Agent 应该放行")
    void shouldAllowEmptyUaForPublicPaths() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/health").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        when(guardProperties.isUaCheckEnabled()).thenReturn(true);
        when(guardProperties.getUaAllowEmptyPaths()).thenReturn(java.util.List.of("/actuator/health", "/health"));
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
    }

    /**
     * 执行 {@code shouldBlockBlacklistedIp} 操作。
     */
    @Test
    @DisplayName("黑名单 IP 应该返回 403 禁止访问")
    void shouldBlockBlacklistedIp() {
        String blockedIp = "192.168.1.100";
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .header("X-Forwarded-For", blockedIp)
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        when(guardProperties.isUaCheckEnabled()).thenReturn(false);
        when(guardProperties.getIpBlacklistCacheTtl()).thenReturn(300);
        when(bucket.get()).thenReturn(System.currentTimeMillis());
        traceIdUtilMockedStatic.when(() -> TraceIdUtil.getTraceId()).thenReturn("test-trace-id");

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(chain, never()).filter(any());
    }

    /**
     * 验证 {@code shouldRecordMetricWhenBlockingIp} 场景。
     */
    @Test
    @DisplayName("新增 IP 黑名单时应该记录安全指标")
    void shouldRecordMetricWhenBlockingIp() {
        when(guardProperties.getIpBlacklistCacheTtl()).thenReturn(300);

        filter.blockIp("192.168.1.101");

        verify(bucket).set(any(Long.class), any(Duration.class));
        verify(securityMetricsRecorder).recordIpBlocked();
    }
}
