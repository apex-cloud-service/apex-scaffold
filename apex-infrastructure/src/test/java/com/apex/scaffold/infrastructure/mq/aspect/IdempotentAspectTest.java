package com.apex.scaffold.infrastructure.mq.aspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.infrastructure.mq.annotation.Idempotent;
import java.lang.reflect.Method;
import java.time.Duration;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.Codec;

/**
 * {@link IdempotentAspect} 的单元测试类
 *
 * <p>测试基于Redisson的分布式幂等性控制：首次处理、重复消息、异常回滚等。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("IdempotentAspect 单元测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IdempotentAspectTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature signature;

    @Mock
    private RBucket<String> bucket;

    private IdempotentAspect aspect;

    /** 用于反射获取Idempotent注解的测试方法 */
    @Idempotent(key = "messageId", expireTime = 3600)
    void annotatedMethod(@SuppressWarnings("unused") String messageId) {}

    /**
     * 设置{@code up}。
     * @throws Exception 当操作无法完成时
     */
    @BeforeEach
    void setUp() throws Exception {
        aspect = new IdempotentAspect(redissonClient);
        when(joinPoint.getSignature()).thenReturn(signature);
        doReturn(bucket).when(redissonClient).getBucket(anyString(), any(Codec.class));
        when(bucket.expire(any(Duration.class))).thenReturn(true);
    }

    @Nested
    @DisplayName("around 方法 - 幂等性控制")
    class AroundTests {

        /**
         * 设置{@code upMethod}。
         * @throws Exception 当操作无法完成时
         */
        @BeforeEach
        void setupMethod() throws Exception {
            Method method = IdempotentAspectTest.class.getDeclaredMethod("annotatedMethod", String.class);
            when(signature.getMethod()).thenReturn(method);
        }

        /**
         * 执行 {@code shouldProceedOnFirstAttempt} 操作。
         * @throws Throwable 当操作无法完成时
         */
        @Test
        @DisplayName("首次处理：setIfAbsent成功则应执行目标方法")
        void shouldProceedOnFirstAttempt() throws Throwable {
            when(bucket.setIfAbsent(eq("processed"), any(Duration.class))).thenReturn(true);
            when(joinPoint.getArgs()).thenReturn(new Object[] {"msg-001"});
            when(signature.getParameterNames()).thenReturn(new String[] {"messageId"});
            when(joinPoint.proceed()).thenReturn("result");

            Object result = aspect.around(joinPoint);

            assertThat(result).isEqualTo("result");
            verify(joinPoint).proceed();
        }

        /**
         * 执行 {@code shouldReturnNullOnDuplicate} 操作。
         * @throws Throwable 当操作无法完成时
         */
        @Test
        @DisplayName("重复消息：setIfAbsent失败应返回null")
        void shouldReturnNullOnDuplicate() throws Throwable {
            when(bucket.setIfAbsent(eq("processed"), any(Duration.class))).thenReturn(false);
            when(joinPoint.getArgs()).thenReturn(new Object[] {"msg-001"});
            when(signature.getParameterNames()).thenReturn(new String[] {"messageId"});

            Object result = aspect.around(joinPoint);

            assertThat(result).isNull();
            verify(joinPoint, never()).proceed();
        }

        /**
         * 执行 {@code shouldDeleteBucketAndRethrowOnException} 操作。
         * @throws Throwable 当操作无法完成时
         */
        @Test
        @DisplayName("处理异常时应删除幂等标记并重新抛出")
        void shouldDeleteBucketAndRethrowOnException() throws Throwable {
            when(bucket.setIfAbsent(eq("processed"), any(Duration.class))).thenReturn(true);
            when(joinPoint.getArgs()).thenReturn(new Object[] {"msg-001"});
            when(signature.getParameterNames()).thenReturn(new String[] {"messageId"});
            when(joinPoint.proceed()).thenThrow(new RuntimeException("处理失败"));

            try {
                aspect.around(joinPoint);
            } catch (RuntimeException e) {
                assertThat(e.getMessage()).isEqualTo("处理失败");
            }
            verify(bucket).delete();
        }

        /**
         * 执行 {@code shouldSkipIdempotencyWhenKeyEmpty} 操作。
         * @throws Throwable 当操作无法完成时
         */
        @Test
        @DisplayName("key为空时自动生成默认key并执行幂等检查")
        void shouldSkipIdempotencyWhenKeyEmpty() throws Throwable {
            Method emptyKeyMethod = EmptyKeyTestClass.class.getDeclaredMethod("emptyKeyMethod", String.class);
            when(signature.getMethod()).thenReturn(emptyKeyMethod);
            when(joinPoint.getArgs()).thenReturn(new Object[] {""});
            when(signature.getParameterNames()).thenReturn(new String[] {"param"});
            when(joinPoint.proceed()).thenReturn("result");
            when(bucket.setIfAbsent(eq("processed"), any(Duration.class))).thenReturn(true);

            Object result = aspect.around(joinPoint);

            assertThat(result).isEqualTo("result");
            // key为空时parseKey生成默认key, 仍然执行幂等检查
            verify(bucket).setIfAbsent(eq("processed"), any(Duration.class));
            verify(joinPoint).proceed();
        }
    }

    /** 测试用：空key注解 */
    @Idempotent(key = "")
    void emptyKeyMethod(@SuppressWarnings("unused") String param) {}

    static class EmptyKeyTestClass {
        /**
         * 执行 {@code emptyKeyMethod} 操作。
         * @param param 参数 {@code param}
         */
        @Idempotent(key = "")
        void emptyKeyMethod(@SuppressWarnings("unused") String param) {}
    }
}
