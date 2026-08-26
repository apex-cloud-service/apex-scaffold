package com.apex.scaffold.infrastructure.cache.aspect;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.apex.scaffold.infrastructure.cache.annotation.CacheLock;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

/**
 * 分布式缓存锁切面单元测试类
 *
 * <p>测试 CacheLockAspect 的 around 方法，验证分布式锁的获取、释放逻辑，
 * 包括正常获取锁执行方法、获取锁超时抛出异常、锁释放等场景。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class CacheLockAspectTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    @Mock
    private RLock lock;

    @InjectMocks
    private CacheLockAspect cacheLockAspect;

    private Method testMethod;

    /**
     * 设置{@code up}。
     * @throws Exception 当操作无法完成时
     */
    @BeforeEach
    void setUp() throws Exception {
        testMethod = TestService.class.getMethod("testMethod", String.class);
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(testMethod);
        when(joinPoint.getArgs()).thenReturn(new Object[] {"user-123"});
    }

    /**
     * 执行 {@code around_LockAcquired_ProceedsAndReturnsResult} 操作。
     * @throws Throwable 当操作无法完成时
     */
    @Test
    @DisplayName("around - 成功获取锁并执行目标方法")
    void around_LockAcquired_ProceedsAndReturnsResult() throws Throwable {
        CacheLock cacheLock = createCacheLock("order", "#arg0", 5000, TimeUnit.MILLISECONDS);
        when(redissonClient.getLock("cache:lock:order:user-123")).thenReturn(lock);
        when(lock.tryLock(5000, 0L, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(joinPoint.proceed()).thenReturn("executed");

        Object result = cacheLockAspect.around(joinPoint, cacheLock);

        assertThat(result).isEqualTo("executed");
        verify(lock).tryLock(5000, 0L, TimeUnit.MILLISECONDS);
        verify(lock).unlock();
        verify(joinPoint).proceed();
    }

    /**
     * 执行 {@code around_LockTimeout_ThrowsRuntimeException} 操作。
     * @throws Throwable 当操作无法完成时
     */
    @Test
    @DisplayName("around - 获取锁超时应抛出RuntimeException")
    void around_LockTimeout_ThrowsRuntimeException() throws Throwable {
        CacheLock cacheLock = createCacheLock("order", "#arg0", 3000, TimeUnit.MILLISECONDS);
        when(cacheLock.fallbackStrategy()).thenReturn(CacheLock.FallbackStrategy.THROW_EXCEPTION);
        when(cacheLock.errorMessage()).thenReturn("获取分布式锁失败");
        when(redissonClient.getLock("cache:lock:order:user-123")).thenReturn(lock);
        when(lock.tryLock(3000, 0L, TimeUnit.MILLISECONDS)).thenReturn(false);

        assertThatThrownBy(() -> cacheLockAspect.around(joinPoint, cacheLock))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("获取分布式锁失败");

        verify(lock, never()).unlock();
        verify(joinPoint, never()).proceed();
    }

    /**
     * 执行 {@code around_NonSpelKey_UsesOriginalKey} 操作。
     * @throws Throwable 当操作无法完成时
     */
    @Test
    @DisplayName("around - 非SpEL key应直接使用原始字符串")
    void around_NonSpelKey_UsesOriginalKey() throws Throwable {
        when(joinPoint.getArgs()).thenReturn(new Object[] {"ignored"});
        CacheLock cacheLock = createCacheLock("user", "fixed-key", 1000, TimeUnit.SECONDS);
        when(redissonClient.getLock("cache:lock:user:fixed-key")).thenReturn(lock);
        when(lock.tryLock(1000, 0L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(joinPoint.proceed()).thenReturn("done");

        Object result = cacheLockAspect.around(joinPoint, cacheLock);

        assertThat(result).isEqualTo("done");
        verify(redissonClient).getLock("cache:lock:user:fixed-key");
    }

    /**
     * 执行 {@code around_EmptyPrefix_UsesDefaultPrefix} 操作。
     * @throws Throwable 当操作无法完成时
     */
    @Test
    @DisplayName("around - 空前缀应使用cache:lock:前缀+key")
    void around_EmptyPrefix_UsesDefaultPrefix() throws Throwable {
        when(joinPoint.getArgs()).thenReturn(new Object[] {"ignored"});
        CacheLock cacheLock = createCacheLock("", "my-key", 100, TimeUnit.SECONDS);
        when(redissonClient.getLock("cache:lock::my-key")).thenReturn(lock);
        when(lock.tryLock(100, 0L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(joinPoint.proceed()).thenReturn("result");

        Object result = cacheLockAspect.around(joinPoint, cacheLock);

        assertThat(result).isEqualTo("result");
        verify(redissonClient).getLock("cache:lock::my-key");
    }

    /**
     * 执行 {@code around_LockAcquiredButMethodThrows_ReleasesLock} 操作。
     * @throws Throwable 当操作无法完成时
     */
    @Test
    @DisplayName("around - 获取锁成功但方法执行抛出异常应释放锁")
    void around_LockAcquiredButMethodThrows_ReleasesLock() throws Throwable {
        CacheLock cacheLock = createCacheLock("test", "#arg0", 2000, TimeUnit.MILLISECONDS);
        when(redissonClient.getLock("cache:lock:test:user-123")).thenReturn(lock);
        when(lock.tryLock(2000, 0L, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(joinPoint.proceed()).thenThrow(new RuntimeException("业务异常"));

        assertThatThrownBy(() -> cacheLockAspect.around(joinPoint, cacheLock))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("业务异常");

        verify(lock).unlock();
    }

    /**
     * 执行 {@code around_LockAcquiredButNotHeld_DoesNotUnlock} 操作。
     * @throws Throwable 当操作无法完成时
     */
    @Test
    @DisplayName("around - 获取锁后isHeldByCurrentThread返回false不应释放锁")
    void around_LockAcquiredButNotHeld_DoesNotUnlock() throws Throwable {
        CacheLock cacheLock = createCacheLock("test", "#arg0", 1000, TimeUnit.SECONDS);
        when(redissonClient.getLock("cache:lock:test:user-123")).thenReturn(lock);
        when(lock.tryLock(1000, 0L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(false);
        when(joinPoint.proceed()).thenReturn("done");

        Object result = cacheLockAspect.around(joinPoint, cacheLock);

        assertThat(result).isEqualTo("done");
        verify(lock, never()).unlock();
    }

    /**
     * 创建{@code cacheLock}。
     * @param prefix 参数 {@code prefix}
     * @param key 参数 {@code key}
     * @param waitTime 参数 {@code waitTime}
     * @param timeUnit 参数 {@code timeUnit}
     * @return 操作结果
     */
    private CacheLock createCacheLock(String prefix, String key, long waitTime, TimeUnit timeUnit) {
        CacheLock cacheLock = mock(CacheLock.class);
        when(cacheLock.prefix()).thenReturn(prefix);
        when(cacheLock.key()).thenReturn(key);
        when(cacheLock.waitTime()).thenReturn(waitTime);
        when(cacheLock.leaseTime()).thenReturn(0L);
        when(cacheLock.timeUnit()).thenReturn(timeUnit);
        return cacheLock;
    }

    static class TestService {
        /**
         * 验证 {@code testMethod} 场景。
         * @param arg0 参数 {@code arg0}
         * @return 字符串结果
         */
        @CacheLock(prefix = "test", key = "#arg0")
        public String testMethod(String arg0) {
            return "result";
        }
    }
}
