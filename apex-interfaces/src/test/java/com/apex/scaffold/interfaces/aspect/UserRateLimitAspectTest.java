package com.apex.scaffold.interfaces.aspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.annotation.UserRateLimit;
import com.apex.scaffold.common.exception.BusinessException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RedissonClient;
import reactor.core.publisher.Mono;

/**
 * UserRateLimitAspect 用户级别限流切面测试
 *
 * <p>验证 UserRateLimitAspect 的用户限流逻辑，
 * 包括限流通过、限流拒绝、SpEL 键解析、默认值使用等场景
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UserRateLimitAspect 用户级别限流切面测试")
class UserRateLimitAspectTest {

    @InjectMocks
    private UserRateLimitAspect aspect;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RRateLimiter rateLimiter;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature signature;

    @Mock
    private UserRateLimit annotation;

    private static final String TEST_RESULT = "success";

    /**
     * 设置{@code up}。
     */
    @BeforeEach
    void setUp() {
        when(joinPoint.getSignature()).thenReturn(signature);
    }

    @Test
    @DisplayName("限流通过时应正常执行方法")
    void shouldAllowRequestWhenUnderLimit() throws Throwable {
        when(redissonClient.getRateLimiter(anyString())).thenReturn(rateLimiter);
        when(rateLimiter.tryAcquire()).thenReturn(true);
        when(annotation.prefix()).thenReturn("sms");
        when(annotation.key()).thenReturn("test");
        when(annotation.rate()).thenReturn(1.0);
        when(annotation.rateInterval()).thenReturn(60L);
        when(annotation.message()).thenReturn("操作过于频繁，请稍后再试");
        when(joinPoint.proceed()).thenReturn(TEST_RESULT);

        Mono<?> result = (Mono<?>) aspect.around(joinPoint, annotation);

        assertThat(result.block()).isEqualTo(TEST_RESULT);
    }

    @Test
    @DisplayName("限流未通过时应抛出 BusinessException")
    void shouldBlockRequestWhenOverLimit() throws Throwable {
        when(redissonClient.getRateLimiter(anyString())).thenReturn(rateLimiter);
        when(rateLimiter.tryAcquire()).thenReturn(false);
        when(annotation.prefix()).thenReturn("sms");
        when(annotation.key()).thenReturn("test");
        when(annotation.rate()).thenReturn(1.0);
        when(annotation.rateInterval()).thenReturn(60L);
        when(annotation.message()).thenReturn("操作过于频繁，请稍后再试");

        Mono<?> result = (Mono<?>) aspect.around(joinPoint, annotation);
        assertThatThrownBy(() -> result.block())
                .isInstanceOf(BusinessException.class)
                .hasMessage("操作过于频繁，请稍后再试");
    }

    @Test
    @DisplayName("应正确解析 SpEL 表达式中的参数值")
    void shouldResolveSpelKey() throws Throwable {
        when(signature.getMethod())
                .thenReturn(UserRateLimitAspectTest.class.getDeclaredMethod("dummyMethodWithPhone", String.class));
        when(joinPoint.getArgs()).thenReturn(new Object[] {"13800138000"});
        when(redissonClient.getRateLimiter("rate:user:sms:13800138000")).thenReturn(rateLimiter);
        when(rateLimiter.tryAcquire()).thenReturn(true);
        when(annotation.prefix()).thenReturn("sms");
        when(annotation.key()).thenReturn("#phone");
        when(annotation.rate()).thenReturn(1.0);
        when(annotation.rateInterval()).thenReturn(60L);
        when(annotation.message()).thenReturn("操作过于频繁，请稍后再试");
        when(joinPoint.proceed()).thenReturn(TEST_RESULT);

        Mono<?> result = (Mono<?>) aspect.around(joinPoint, annotation);

        assertThat(result.block()).isEqualTo(TEST_RESULT);
    }

    @Test
    @DisplayName("Redisson 缺失时应降级到本地限流")
    void shouldFallbackToLocalRateLimitWhenRedissonIsMissing() throws Throwable {
        UserRateLimitAspect localAspect = new UserRateLimitAspect(null);
        when(annotation.prefix()).thenReturn("sms");
        when(annotation.key()).thenReturn("test");
        when(annotation.rate()).thenReturn(1.0);
        when(annotation.rateInterval()).thenReturn(60L);
        when(annotation.message()).thenReturn("操作过于频繁，请稍后再试");
        when(joinPoint.proceed()).thenReturn(TEST_RESULT);

        Mono<?> result = (Mono<?>) localAspect.around(joinPoint, annotation);

        assertThat(result.block()).isEqualTo(TEST_RESULT);
    }

    @Test
    @DisplayName("未指定限流参数时应使用默认值")
    void shouldUseDefaultRateLimit() throws Throwable {
        when(redissonClient.getRateLimiter(anyString())).thenReturn(rateLimiter);
        when(rateLimiter.tryAcquire()).thenReturn(true);
        when(annotation.prefix()).thenReturn("");
        when(annotation.key()).thenReturn("default");
        when(annotation.rate()).thenReturn(1.0);
        when(annotation.rateInterval()).thenReturn(1L);
        when(annotation.message()).thenReturn("操作过于频繁，请稍后再试");
        when(joinPoint.proceed()).thenReturn(TEST_RESULT);

        Mono<?> result = (Mono<?>) aspect.around(joinPoint, annotation);

        assertThat(result.block()).isEqualTo(TEST_RESULT);
    }

    /**
     * 用于 SpEL 解析测试的辅助方法
     *
     * @param phone 手机号参数
     * @return 固定结果
     */
    @SuppressWarnings("unused")
    public String dummyMethodWithPhone(String phone) {
        return TEST_RESULT;
    }
}
