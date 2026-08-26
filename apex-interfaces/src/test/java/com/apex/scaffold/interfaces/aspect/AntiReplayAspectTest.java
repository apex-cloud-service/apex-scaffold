package com.apex.scaffold.interfaces.aspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.annotation.AntiReplay;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.interfaces.monitor.SecurityMetricsRecorder;
import java.time.Duration;
import java.time.Instant;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * AntiReplayAspect 防重放攻击切面测试
 *
 * <p>验证 AntiReplayAspect 的防重放校验逻辑，
 * 包括新鲜请求放行、重放请求拦截、时间戳校验、跳过校验等场景
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AntiReplayAspect 防重放攻击切面测试")
class AntiReplayAspectTest {

    @InjectMocks
    private AntiReplayAspect aspect;

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private SecurityMetricsRecorder securityMetricsRecorder;

    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private AntiReplay annotation;

    private static final String TEST_RESULT = "success";
    private static final String TEST_NONCE = "550e8400-e29b-41d4-a716-446655440000";

    /**
     * 执行 {@code shouldAllowFreshRequest} 操作。
     * @throws Throwable 当操作无法完成时
     */
    @Test
    @DisplayName("新鲜请求（有效时间戳 + 新 Nonce）时应正常执行方法")
    void shouldAllowFreshRequest() throws Throwable {
        long now = Instant.now().getEpochSecond();
        ServerWebExchange exchange = createMockExchange(String.valueOf(now), TEST_NONCE);
        when(joinPoint.getArgs()).thenReturn(new Object[] {exchange});
        when(annotation.maxTimeDiff()).thenReturn(300L);
        when(annotation.requireNonce()).thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(true));
        when(joinPoint.proceed()).thenReturn(TEST_RESULT);

        Mono<?> result = (Mono<?>) aspect.around(joinPoint, annotation);

        assertThat(result.block()).isEqualTo(TEST_RESULT);
    }

    /**
     * 执行 {@code shouldReplayRequest} 操作。
     * @throws Throwable 当操作无法完成时
     */
    @Test
    @DisplayName("Nonce 已存在于 Redis 时应抛出 BusinessException")
    void shouldReplayRequest() throws Throwable {
        long now = Instant.now().getEpochSecond();
        ServerWebExchange exchange = createMockExchange(String.valueOf(now), TEST_NONCE);
        when(joinPoint.getArgs()).thenReturn(new Object[] {exchange});
        when(annotation.maxTimeDiff()).thenReturn(300L);
        when(annotation.requireNonce()).thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(false));

        Mono<?> result = (Mono<?>) aspect.around(joinPoint, annotation);
        assertThatThrownBy(() -> result.block())
                .isInstanceOf(BusinessException.class)
                .hasMessage("请求已过期或重复提交");
        verify(securityMetricsRecorder).recordAntiReplayRejected();
    }

    /**
     * 执行 {@code shouldRejectExpiredTimestamp} 操作。
     * @throws Throwable 当操作无法完成时
     */
    @Test
    @DisplayName("时间戳偏差超过 maxTimeDiff 时应抛出 BusinessException")
    void shouldRejectExpiredTimestamp() throws Throwable {
        long expiredTimestamp = Instant.now().getEpochSecond() - 600;
        ServerWebExchange exchange = createMockExchange(String.valueOf(expiredTimestamp), TEST_NONCE);
        when(joinPoint.getArgs()).thenReturn(new Object[] {exchange});
        when(annotation.maxTimeDiff()).thenReturn(300L);

        assertThatThrownBy(() -> aspect.around(joinPoint, annotation))
                .isInstanceOf(BusinessException.class)
                .hasMessage("请求已过期或重复提交");
    }

    /**
     * 执行 {@code shouldSkipCheckWhenNoExchange} 操作。
     * @throws Throwable 当操作无法完成时
     */
    @Test
    @DisplayName("参数中无 ServerWebExchange 时应跳过校验并正常执行方法")
    void shouldSkipCheckWhenNoExchange() throws Throwable {
        when(joinPoint.getArgs()).thenReturn(new Object[] {"someParam"});
        when(joinPoint.proceed()).thenReturn(TEST_RESULT);

        Object result = aspect.around(joinPoint, annotation);

        assertThat(result).isEqualTo(TEST_RESULT);
    }

    /**
     * 创建包含指定时间戳和 Nonce 请求头的模拟 ServerWebExchange
     *
     * @param timestamp 时间戳
     * @param nonce     随机数
     * @return 模拟的 ServerWebExchange
     */
    private ServerWebExchange createMockExchange(String timestamp, String nonce) {
        ServerWebExchange exchange = mock(ServerWebExchange.class);
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        HttpHeaders headers = mock(HttpHeaders.class);

        when(exchange.getRequest()).thenReturn(request);
        when(request.getHeaders()).thenReturn(headers);
        when(headers.getFirst("X-Timestamp")).thenReturn(timestamp);
        when(headers.getFirst("X-Nonce")).thenReturn(nonce);

        return exchange;
    }
}
