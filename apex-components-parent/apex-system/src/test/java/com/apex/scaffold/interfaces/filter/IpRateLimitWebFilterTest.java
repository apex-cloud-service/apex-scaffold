package com.apex.scaffold.interfaces.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.util.TraceIdUtil;
import com.apex.scaffold.interfaces.monitor.SecurityMetricsRecorder;
import com.apex.scaffold.interfaces.util.ClientIpResolver;
import java.net.InetSocketAddress;
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
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * IP 速率限制过滤器单元测试
 *
 * <p>验证 IpRateLimitWebFilter 的滑动窗口限流、白名单绕过、
 * 超限拦截等核心功能
 *
 * @author Apex Team
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("IpRateLimitWebFilter IP 速率限制过滤器测试")
class IpRateLimitWebFilterTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private IpRateLimitProperties rateLimitProperties;

    @Mock
    private WebFilterChain chain;

    @Mock
    private ReactiveHashOperations<String, Object, Object> hashOps;

    @Mock
    private ClientIpResolver clientIpResolver;

    @Mock
    private SecurityMetricsRecorder securityMetricsRecorder;

    @InjectMocks
    private IpRateLimitWebFilter filter;

    private MockedStatic<TraceIdUtil> traceIdUtilMockedStatic;

    /**
     * 设置{@code up}。
     */
    @BeforeEach
    void setUp() {
        traceIdUtilMockedStatic = mockStatic(TraceIdUtil.class);
    }

    @AfterEach
    void tearDown() {
        traceIdUtilMockedStatic.close();
    }

    @Test
    void samplesRateLimitAndBlockLogsWithoutRequestFields() {
        when(rateLimitProperties.isEnabled()).thenReturn(true);
        when(rateLimitProperties.getDefaultWindow()).thenReturn(60);
        when(rateLimitProperties.getDefaultLimit()).thenReturn(1);
        when(rateLimitProperties.getBlockThreshold()).thenReturn(1);
        when(rateLimitProperties.getBlockDuration()).thenReturn(60);
        when(clientIpResolver.resolve(
                        nullable(InetSocketAddress.class), nullable(java.util.List.class), nullable(String.class)))
                .thenReturn("10.0.0.1");
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(hashOps.get(anyString(), any())).thenReturn(Mono.empty());
        when(hashOps.put(anyString(), any(), any())).thenReturn(Mono.just(true));
        when(redisTemplate.expire(anyString(), any())).thenReturn(Mono.just(true));
        when(redisTemplate.execute(any(), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Flux.just(2L));
        when(redisTemplate.execute(any(), anyList(), anyString())).thenReturn(Flux.just(1L));
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(IpRateLimitWebFilter.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            for (int i = 0; i < 16; i++) {
                MockServerWebExchange exchange = MockServerWebExchange.from(
                        MockServerHttpRequest.get("/api/secret-value").build());
                StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
                assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            }
            assertThat(appender.list).hasSize(10).allSatisfy(event -> assertThat(event.getFormattedMessage())
                    .doesNotContain("secret-value", "10.0.0.1"));
            verify(securityMetricsRecorder, times(16)).recordRateLimitRejected();
            verify(securityMetricsRecorder, times(16)).recordIpBlocked();
            verify(chain, never()).filter(any());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void samplesAlreadyBlockedLogs() {
        when(rateLimitProperties.isEnabled()).thenReturn(true);
        when(rateLimitProperties.getBlockDuration()).thenReturn(60);
        when(clientIpResolver.resolve(
                        nullable(InetSocketAddress.class), nullable(java.util.List.class), nullable(String.class)))
                .thenReturn("10.0.0.1");
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(hashOps.get(anyString(), any())).thenReturn(Mono.just(String.valueOf(System.currentTimeMillis() + 30000)));
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(IpRateLimitWebFilter.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            for (int i = 0; i < 16; i++) {
                MockServerWebExchange exchange = MockServerWebExchange.from(
                        MockServerHttpRequest.get("/api/test").build());
                StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
                assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            }
            assertThat(appender.list).hasSize(5).allSatisfy(event -> assertThat(event.getFormattedMessage())
                    .doesNotContain("10.0.0.1"));
            verify(chain, never()).filter(any());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    @DisplayName("正常请求在限制内应该放行")
    void shouldAllowWithinLimit() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .header("X-Forwarded-For", "10.0.0.1")
                .header("User-Agent", "Mozilla/5.0")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        when(rateLimitProperties.isEnabled()).thenReturn(true);
        when(rateLimitProperties.getDefaultWindow()).thenReturn(60);
        when(rateLimitProperties.getDefaultLimit()).thenReturn(60);
        when(rateLimitProperties.getIpWhitelist()).thenReturn(java.util.List.of());
        when(rateLimitProperties.getPathLimits())
                .thenReturn(java.util.Map.of(
                        "/api/auth/sms/code", 3,
                        "/api/auth/login", 10,
                        "/api/file/upload", 20));
        when(clientIpResolver.resolve(nullable(InetSocketAddress.class), anyList(), nullable(String.class)))
                .thenReturn("10.0.0.1");
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        // 模拟 Lua 脚本返回窗口内请求数 5（未超限）
        when(redisTemplate.execute(any(), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Flux.just(5L));
        when(hashOps.get(anyString(), any())).thenReturn(Mono.empty());
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
    }

    /**
     * 验证 {@code shouldNotPenalizeBusinessApiNotFound} 场景。
     */
    @Test
    @DisplayName("业务 API 返回 404 不应该触发 IP 信誉扣分")
    void shouldNotPenalizeBusinessApiNotFound() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v2/explore/choice-2/current")
                .header("User-Agent", "Apex-Android")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        when(rateLimitProperties.isEnabled()).thenReturn(true);
        when(rateLimitProperties.getDefaultWindow()).thenReturn(60);
        when(rateLimitProperties.getDefaultLimit()).thenReturn(60);
        when(rateLimitProperties.getIpWhitelist()).thenReturn(java.util.List.of());
        when(rateLimitProperties.getPathLimits()).thenReturn(java.util.Map.of());
        when(clientIpResolver.resolve(nullable(InetSocketAddress.class), anyList(), nullable(String.class)))
                .thenReturn("10.0.0.1");
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(redisTemplate.execute(any(), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Flux.just(1L));
        when(hashOps.get(anyString(), any())).thenReturn(Mono.empty());
        when(chain.filter(exchange)).thenAnswer(invocation -> {
            exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
            return Mono.empty();
        });

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
        verify(hashOps, never()).increment(anyString(), any(), anyLong());
    }

    @Test
    @DisplayName("超出速率限制应该返回 429 错误响应")
    void shouldBlockExceedingLimit() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .header("X-Forwarded-For", "10.0.0.1")
                .header("User-Agent", "Mozilla/5.0")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        when(rateLimitProperties.isEnabled()).thenReturn(true);
        when(rateLimitProperties.getDefaultWindow()).thenReturn(60);
        when(rateLimitProperties.getDefaultLimit()).thenReturn(60);
        when(rateLimitProperties.getIpWhitelist()).thenReturn(java.util.List.of());
        when(rateLimitProperties.getPathLimits())
                .thenReturn(java.util.Map.of(
                        "/api/auth/sms/code", 3,
                        "/api/auth/login", 10,
                        "/api/file/upload", 20));
        when(clientIpResolver.resolve(nullable(InetSocketAddress.class), anyList(), nullable(String.class)))
                .thenReturn("10.0.0.1");
        when(rateLimitProperties.getViolationWindow()).thenReturn(60);
        when(rateLimitProperties.getBlockThreshold()).thenReturn(30);
        when(rateLimitProperties.getBlockDuration()).thenReturn(3600);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        // 模拟 Lua 脚本返回窗口内请求数 100（超限）
        when(redisTemplate.execute(any(), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Flux.just(100L));
        when(redisTemplate.execute(any(), anyList(), anyString())).thenReturn(Flux.just(1L));
        when(hashOps.get(anyString(), any())).thenReturn(Mono.empty());
        when(hashOps.put(anyString(), any(), any())).thenReturn(Mono.just(true));
        when(redisTemplate.expire(anyString(), any())).thenReturn(Mono.just(true));
        traceIdUtilMockedStatic.when(() -> TraceIdUtil.getTraceId()).thenReturn("test-trace-id");

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        verify(securityMetricsRecorder).recordRateLimitRejected();
        verify(securityMetricsRecorder, never()).recordIpBlocked();
    }

    /**
     * 验证 {@code shouldNotPenalizeBusinessNotFoundResponse} 场景。
     */
    @Test
    @DisplayName("业务 404 不应计入 IP 违规或触发封禁")
    void shouldNotPenalizeBusinessNotFoundResponse() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/explore/battles/current")
                .header("X-Forwarded-For", "10.0.0.2")
                .header("User-Agent", "ApexApp/1.0")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        when(rateLimitProperties.isEnabled()).thenReturn(true);
        when(rateLimitProperties.getDefaultWindow()).thenReturn(60);
        when(rateLimitProperties.getDefaultLimit()).thenReturn(120);
        when(rateLimitProperties.getIpWhitelist()).thenReturn(java.util.List.of());
        when(rateLimitProperties.getPathLimits()).thenReturn(java.util.Map.of("/api/v1/explore/battles/current", 120));
        when(clientIpResolver.resolve(nullable(InetSocketAddress.class), anyList(), nullable(String.class)))
                .thenReturn("10.0.0.2");
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(hashOps.get(anyString(), any())).thenReturn(Mono.empty());
        when(redisTemplate.execute(any(), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Flux.just(1L));
        when(chain.filter(exchange)).thenAnswer(invocation -> {
            exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
            return Mono.empty();
        });

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(redisTemplate, times(1)).execute(any(), anyList(), anyString(), anyString(), anyString(), anyString());
        verify(redisTemplate, never()).execute(any(), anyList(), anyString());
        verify(hashOps, never()).put(anyString(), any(), any());
        verify(securityMetricsRecorder, never()).recordIpBlocked();
    }

    /**
     * 验证 {@code shouldApplyLimitToTemplatedBattlePath} 场景。
     */
    @Test
    @DisplayName("动态路径配置应匹配实际对局 ID")
    void shouldApplyLimitToTemplatedBattlePath() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/explore/battles/battle-123/submit")
                .header("X-Forwarded-For", "10.0.0.3")
                .header("User-Agent", "ApexApp/1.0")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        when(rateLimitProperties.isEnabled()).thenReturn(true);
        when(rateLimitProperties.getDefaultWindow()).thenReturn(60);
        when(rateLimitProperties.getDefaultLimit()).thenReturn(120);
        when(rateLimitProperties.getViolationWindow()).thenReturn(60);
        when(rateLimitProperties.getBlockThreshold()).thenReturn(30);
        when(rateLimitProperties.getIpWhitelist()).thenReturn(java.util.List.of());
        when(rateLimitProperties.getPathLimits())
                .thenReturn(java.util.Map.of("/api/v1/explore/battles/{battleId}/submit", 30));
        when(clientIpResolver.resolve(nullable(InetSocketAddress.class), anyList(), nullable(String.class)))
                .thenReturn("10.0.0.3");
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(hashOps.get(anyString(), any())).thenReturn(Mono.empty());
        when(redisTemplate.execute(any(), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Flux.just(31L));
        when(redisTemplate.execute(any(), anyList(), anyString())).thenReturn(Flux.just(1L));
        traceIdUtilMockedStatic.when(() -> TraceIdUtil.getTraceId()).thenReturn("test-trace-id");

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        verify(redisTemplate)
                .execute(
                        any(),
                        argThat(keys -> keys.get(0).endsWith("/api/v1/explore/battles/{battleId}/submit")),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString());
        verify(chain, never()).filter(exchange);
    }

    /**
     * 验证 {@code shouldTemporarilyBlockAfterRepeatedRateLimitViolations} 场景。
     */
    @Test
    @DisplayName("持续无视 429 达到阈值后才应短时封禁")
    void shouldTemporarilyBlockAfterRepeatedRateLimitViolations() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .header("X-Forwarded-For", "10.0.0.4")
                .header("User-Agent", "ApexApp/1.0")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        when(rateLimitProperties.isEnabled()).thenReturn(true);
        when(rateLimitProperties.getDefaultWindow()).thenReturn(60);
        when(rateLimitProperties.getDefaultLimit()).thenReturn(120);
        when(rateLimitProperties.getViolationWindow()).thenReturn(60);
        when(rateLimitProperties.getBlockThreshold()).thenReturn(30);
        when(rateLimitProperties.getBlockDuration()).thenReturn(300);
        when(rateLimitProperties.getIpWhitelist()).thenReturn(java.util.List.of());
        when(rateLimitProperties.getPathLimits()).thenReturn(java.util.Map.of());
        when(clientIpResolver.resolve(nullable(InetSocketAddress.class), anyList(), nullable(String.class)))
                .thenReturn("10.0.0.4");
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(hashOps.get(anyString(), any())).thenReturn(Mono.empty());
        when(hashOps.put(anyString(), any(), any())).thenReturn(Mono.just(true));
        when(redisTemplate.expire(anyString(), any())).thenReturn(Mono.just(true));
        when(redisTemplate.execute(any(), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Flux.just(121L));
        when(redisTemplate.execute(any(), anyList(), anyString())).thenReturn(Flux.just(30L));
        traceIdUtilMockedStatic.when(() -> TraceIdUtil.getTraceId()).thenReturn("test-trace-id");

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        verify(hashOps).put(anyString(), any(), any());
        verify(securityMetricsRecorder).recordIpBlocked();
    }

    /**
     * 验证 {@code shouldCapLegacyLongBlockAtCurrentDuration} 场景。
     */
    @Test
    @DisplayName("历史 24 小时封禁应收敛到新的 5 分钟上限")
    void shouldCapLegacyLongBlockAtCurrentDuration() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .header("X-Forwarded-For", "10.0.0.5")
                .header("User-Agent", "ApexApp/1.0")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        when(rateLimitProperties.isEnabled()).thenReturn(true);
        when(rateLimitProperties.getBlockDuration()).thenReturn(300);
        when(rateLimitProperties.getIpWhitelist()).thenReturn(java.util.List.of());
        when(clientIpResolver.resolve(nullable(InetSocketAddress.class), anyList(), nullable(String.class)))
                .thenReturn("10.0.0.5");
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(hashOps.get(anyString(), any()))
                .thenReturn(Mono.just(String.valueOf(System.currentTimeMillis() + 86_400_000L)));
        when(hashOps.put(anyString(), any(), any())).thenReturn(Mono.just(true));
        traceIdUtilMockedStatic.when(() -> TraceIdUtil.getTraceId()).thenReturn("test-trace-id");

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        verify(hashOps).put(anyString(), any(), any());
        verify(redisTemplate, never()).execute(any(), anyList(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("白名单 IP 应该绕过速率限制")
    void shouldWhitelistIp() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .header("X-Forwarded-For", "192.168.1.1")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        when(rateLimitProperties.isEnabled()).thenReturn(true);
        when(rateLimitProperties.getIpWhitelist()).thenReturn(java.util.List.of("192.168.1.1"));
        when(clientIpResolver.resolve(nullable(InetSocketAddress.class), anyList(), nullable(String.class)))
                .thenReturn("192.168.1.1");
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
    }
}
