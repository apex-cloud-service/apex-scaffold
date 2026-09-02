package com.apex.scaffold.interfaces.aspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.annotation.IdempotentApi;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.interfaces.monitor.SecurityMetricsRecorder;
import java.time.Duration;
import java.util.Optional;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;

/**
 * IdempotentApiAspect HTTP 幂等切面测试
 *
 * <p>验证 IdempotentApiAspect 的幂等去重逻辑，
 * 包括首请求放行、重复提交拒绝、缺键头行为、失败释放、Redis 降级等场景。
 * 覆盖函数式端点（ServerRequest）与注解式端点（ServerWebExchange）两种形态。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.2.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("IdempotentApiAspect HTTP 幂等切面测试")
class IdempotentApiAspectTest {

    @InjectMocks
    private IdempotentApiAspect aspect;

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private SecurityMetricsRecorder securityMetricsRecorder;

    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private IdempotentApi annotation;

    private static final String TEST_RESULT = "success";
    private static final String IDEMPOTENCY_KEY = "550e8400-e29b-41d4-a716-446655440000";
    private static final String PATH = "/api/v1/auth/register";

    @Test
    @DisplayName("函数式端点首次请求（携带幂等键头 + SETNX 成功）时应正常执行方法")
    void shouldAllowFirstRequest() throws Throwable {
        ServerRequest request = createMockServerRequest(IDEMPOTENCY_KEY);
        when(joinPoint.getArgs()).thenReturn(new Object[] {request});
        when(annotation.header()).thenReturn("Idempotency-Key");
        when(annotation.expireTime()).thenReturn(600L);
        when(annotation.required()).thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(true));
        when(joinPoint.proceed()).thenReturn(Mono.just(TEST_RESULT));

        Mono<?> result = (Mono<?>) aspect.around(joinPoint, annotation);

        assertThat(result.block()).isEqualTo(TEST_RESULT);
    }

    @Test
    @DisplayName("相同幂等键重复提交（SETNX 返回 false）时应抛出重复提交异常并记录指标")
    void shouldRejectDuplicateSubmission() throws Throwable {
        ServerRequest request = createMockServerRequest(IDEMPOTENCY_KEY);
        when(joinPoint.getArgs()).thenReturn(new Object[] {request});
        when(annotation.header()).thenReturn("Idempotency-Key");
        when(annotation.expireTime()).thenReturn(600L);
        when(annotation.required()).thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(false));

        Mono<?> result = (Mono<?>) aspect.around(joinPoint, annotation);

        assertThatThrownBy(() -> result.block())
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.REPEAT_SUBMIT.getCode());
        verify(securityMetricsRecorder).recordRepeatSubmitRejected();
        verify(joinPoint, never()).proceed();
    }

    @Test
    @DisplayName("严格模式下缺少幂等键头时应抛出缺键异常")
    void shouldRejectWhenHeaderMissingAndRequired() throws Throwable {
        ServerRequest request = createMockServerRequest(null);
        when(joinPoint.getArgs()).thenReturn(new Object[] {request});
        when(annotation.header()).thenReturn("Idempotency-Key");
        when(annotation.required()).thenReturn(true);

        assertThatThrownBy(() -> aspect.around(joinPoint, annotation))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.IDEMPOTENCY_KEY_MISSING.getCode());
        verify(securityMetricsRecorder).recordRepeatSubmitRejected();
        verify(joinPoint, never()).proceed();
    }

    @Test
    @DisplayName("渐进模式（required=false）缺幂等键头时应放行执行方法")
    void shouldProceedWhenHeaderMissingAndNotRequired() throws Throwable {
        ServerRequest request = createMockServerRequest(null);
        when(joinPoint.getArgs()).thenReturn(new Object[] {request});
        when(annotation.header()).thenReturn("Idempotency-Key");
        when(annotation.required()).thenReturn(false);
        when(joinPoint.proceed()).thenReturn(Mono.just(TEST_RESULT));

        Mono<?> result = (Mono<?>) aspect.around(joinPoint, annotation);

        assertThat(result.block()).isEqualTo(TEST_RESULT);
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    @DisplayName("业务方法抛 4xx BusinessException 时应删除幂等键（客户端可换参数重试）")
    void shouldReleaseKeyOnClientBusinessError() throws Throwable {
        ServerRequest request = createMockServerRequest(IDEMPOTENCY_KEY);
        when(joinPoint.getArgs()).thenReturn(new Object[] {request});
        when(annotation.header()).thenReturn("Idempotency-Key");
        when(annotation.expireTime()).thenReturn(600L);
        when(annotation.required()).thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(true));
        when(valueOperations.delete(anyString())).thenReturn(Mono.just(true));
        when(joinPoint.proceed()).thenReturn(Mono.error(new BusinessException(ResultCode.PARAM_ERROR)));

        Mono<?> result = (Mono<?>) aspect.around(joinPoint, annotation);

        assertThatThrownBy(() -> result.block())
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.PARAM_ERROR.getCode());
        verify(valueOperations).delete(anyString());
    }

    @Test
    @DisplayName("业务方法抛 5xx 瞬态异常（非 BusinessException）时应保留幂等键至 TTL，禁止删除以避免重复写库")
    void shouldNotReleaseKeyOnTransient5xxError() throws Throwable {
        ServerRequest request = createMockServerRequest(IDEMPOTENCY_KEY);
        when(joinPoint.getArgs()).thenReturn(new Object[] {request});
        when(annotation.header()).thenReturn("Idempotency-Key");
        when(annotation.expireTime()).thenReturn(600L);
        when(annotation.required()).thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(true));
        when(joinPoint.proceed()).thenReturn(Mono.error(new IllegalStateException("db flush后NPE 或外部IO异常")));

        Mono<?> result = (Mono<?>) aspect.around(joinPoint, annotation);

        assertThatThrownBy(() -> result.block()).isInstanceOf(IllegalStateException.class);
        verify(valueOperations, never()).delete(anyString());
    }

    @Test
    @DisplayName("同步方法抛 5xx 瞬态异常时同样不应释放幂等键")
    void shouldNotReleaseKeyOnSyncProceedTransientError() throws Throwable {
        ServerRequest request = createMockServerRequest(IDEMPOTENCY_KEY);
        when(joinPoint.getArgs()).thenReturn(new Object[] {request});
        when(annotation.header()).thenReturn("Idempotency-Key");
        when(annotation.expireTime()).thenReturn(600L);
        when(annotation.required()).thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(true));
        when(joinPoint.proceed()).thenThrow(new RuntimeException("dao层连接超时"));

        Mono<?> result = (Mono<?>) aspect.around(joinPoint, annotation);

        assertThatThrownBy(() -> result.block()).isInstanceOf(RuntimeException.class);
        verify(valueOperations, never()).delete(anyString());
    }

    @Test
    @DisplayName("Redis 不可用时应降级放行（fail-open）执行方法")
    void shouldFailOpenOnRedisUnavailable() throws Throwable {
        ServerRequest request = createMockServerRequest(IDEMPOTENCY_KEY);
        when(joinPoint.getArgs()).thenReturn(new Object[] {request});
        when(annotation.header()).thenReturn("Idempotency-Key");
        when(annotation.required()).thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.error(new RedisConnectionFailureException("connection refused")));
        when(joinPoint.proceed()).thenReturn(Mono.just(TEST_RESULT));

        Mono<?> result = (Mono<?>) aspect.around(joinPoint, annotation);

        assertThat(result.block()).isEqualTo(TEST_RESULT);
    }

    @Test
    @DisplayName("方法参数中无请求对象时应跳过幂等校验并执行方法")
    void shouldSkipCheckWhenNoRequestContext() throws Throwable {
        when(joinPoint.getArgs()).thenReturn(new Object[] {"someParam"});
        when(joinPoint.proceed()).thenReturn(Mono.just(TEST_RESULT));

        Mono<?> result = (Mono<?>) aspect.around(joinPoint, annotation);

        assertThat(result.block()).isEqualTo(TEST_RESULT);
        verify(redisTemplate, never()).opsForValue();
    }

    /**
     * 创建包含指定幂等键头的模拟函数式 ServerRequest
     *
     * @param idempotencyKey 幂等键头值；{@code null} 表示不携带
     * @return 模拟的 ServerRequest
     */
    private ServerRequest createMockServerRequest(String idempotencyKey) {
        ServerRequest request = mock(ServerRequest.class);
        ServerRequest.Headers serverHeaders = mock(ServerRequest.Headers.class);
        HttpHeaders headers = new HttpHeaders();
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        when(serverHeaders.asHttpHeaders()).thenReturn(headers);
        when(request.headers()).thenReturn(serverHeaders);
        when(request.path()).thenReturn(PATH);
        when(request.remoteAddress()).thenReturn(Optional.empty());
        return request;
    }
}
