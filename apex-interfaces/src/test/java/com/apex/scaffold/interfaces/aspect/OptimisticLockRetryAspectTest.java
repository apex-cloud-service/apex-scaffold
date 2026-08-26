package com.apex.scaffold.interfaces.aspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.annotation.OptimisticLockRetry;
import com.apex.scaffold.common.exception.BusinessException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * {@link OptimisticLockRetryAspect} 的单元测试类
 *
 * <p>测试乐观锁重试切面的核心逻辑：首次成功、冲突重试、指数退避、重试耗尽等。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("OptimisticLockRetryAspect 单元测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OptimisticLockRetryAspectTest {

    private final OptimisticLockRetryAspect aspect = new OptimisticLockRetryAspect();

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private OptimisticLockRetry annotation;

    @Mock
    private Signature signature;

    /**
     * 设置{@code up}。
     */
    @BeforeEach
    void setUp() {
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getDeclaringType()).thenReturn(TestService.class);
        when(signature.getName()).thenReturn("testMethod");
    }

    static class TestService {}

    @Nested
    @DisplayName("around 方法")
    class AroundTests {

        /**
         * 执行 {@code shouldReturnOnFirstSuccess} 操作。
         * @throws Throwable 当操作无法完成时
         */
        @Test
        @DisplayName("首次执行成功应返回结果")
        void shouldReturnOnFirstSuccess() throws Throwable {
            when(annotation.maxRetries()).thenReturn(3);
            when(annotation.retryDelay()).thenReturn(10L);
            when(annotation.idempotent()).thenReturn(false);
            when(joinPoint.proceed()).thenReturn("success");

            Object result = aspect.around(joinPoint, annotation);

            assertThat(result).isEqualTo("success");
            verify(joinPoint, times(1)).proceed();
        }

        /**
         * 执行 {@code shouldRetryAndSucceed} 操作。
         * @throws Throwable 当操作无法完成时
         */
        @Test
        @DisplayName("乐观锁冲突后应重试并成功")
        void shouldRetryAndSucceed() throws Throwable {
            when(annotation.maxRetries()).thenReturn(3);
            when(annotation.retryDelay()).thenReturn(1L);
            when(annotation.idempotent()).thenReturn(false);

            // 第一次抛乐观锁异常，第二次成功
            when(joinPoint.proceed())
                    .thenThrow(new OptimisticLockingFailureException("conflict"))
                    .thenReturn("retry-success");

            Object result = aspect.around(joinPoint, annotation);

            assertThat(result).isEqualTo("retry-success");
            verify(joinPoint, times(2)).proceed();
        }

        /**
         * 执行 {@code shouldThrowWhenRetriesExhausted} 操作。
         * @throws Throwable 当操作无法完成时
         */
        @Test
        @DisplayName("重试耗尽后应抛出 BusinessException")
        void shouldThrowWhenRetriesExhausted() throws Throwable {
            when(annotation.maxRetries()).thenReturn(2);
            when(annotation.retryDelay()).thenReturn(1L);
            when(annotation.idempotent()).thenReturn(false);
            when(annotation.message()).thenReturn("操作冲突，请重试");
            when(joinPoint.proceed()).thenThrow(new OptimisticLockingFailureException("conflict"));

            assertThatThrownBy(() -> aspect.around(joinPoint, annotation)).isInstanceOf(BusinessException.class);

            verify(joinPoint, times(3)).proceed(); // 首次 + 2次重试
        }

        /**
         * 执行 {@code shouldNotRetryForNonOptimisticLockException} 操作。
         * @throws Throwable 当操作无法完成时
         */
        @Test
        @DisplayName("非乐观锁 RuntimeException 应直接抛出不重试")
        void shouldNotRetryForNonOptimisticLockException() throws Throwable {
            when(annotation.maxRetries()).thenReturn(3);
            when(annotation.retryDelay()).thenReturn(1L);
            when(annotation.idempotent()).thenReturn(false);
            when(joinPoint.proceed()).thenThrow(new RuntimeException("other error"));

            assertThatThrownBy(() -> aspect.around(joinPoint, annotation))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("other error");

            verify(joinPoint, times(1)).proceed();
        }
    }
}
