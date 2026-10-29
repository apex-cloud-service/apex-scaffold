package com.apex.scaffold.infrastructure.cache.aspect;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.infrastructure.cache.annotation.CacheLock;
import com.apex.scaffold.infrastructure.cache.annotation.CacheLock.FallbackStrategy;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

/**
 * 分布式缓存锁切面（基于 Redisson）
 *
 * <p>基于 Redisson 的 {@link RLock} 实现分布式锁功能，用于防止缓存击穿和重复请求。
 * 相比本地 {@code ReentrantLock} 方案，Redisson 分布式锁确保多实例部署下锁状态一致共享。
 *
 * <h2>设计说明</h2>
 * <ol>
 * <li>拦截标注 {@link CacheLock} 注解的方法</li>
 * <li>根据注解中的 key 表达式（支持 SpEL）动态生成锁键</li>
 * <li>通过 Redisson {@link RLock#tryLock(long, long, TimeUnit)} 尝试获取分布式锁</li>
 * <li>获取锁成功则执行目标方法</li>
 * <li>获取锁失败则根据 {@link FallbackStrategy} 执行降级策略</li>
 * <li>方法执行完毕后释放锁</li>
 * </ol>
 *
 * <h2>线程安全性</h2>
 * <pre>
 *   cache:lock:{prefix}:{key}
 * </pre>
 *
 * <h2>设计说明</h2>
 * <ul>
 * <li>{@link FallbackStrategy#THROW_EXCEPTION} - 获取锁失败时抛出异常（默认）</li>
 * <li>{@link FallbackStrategy#RETURN_NULL} - 获取锁失败时返回 null</li>
 * <li>{@link FallbackStrategy#RETURN_DEFAULT} - 获取锁失败时执行降级方法返回默认值</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code * // 默认策略：获取锁失败抛出异常
 * @CacheLock(prefix = "user", key = "#userId", waitTime = 3000)
 * public UserEntity getUserById(Long userId) { ... }
 *
 * // 获取锁失败返回 null
 * @CacheLock(prefix = "order", key = "#orderId", fallbackStrategy = FallbackStrategy.RETURN_NULL)
 * public OrderEntity getOrder(Long orderId) { ... }
 *
 * // 获取锁失败执行降级方法
 * @CacheLock(prefix = "product", key = "#productId",
 *            fallbackStrategy = FallbackStrategy.RETURN_DEFAULT,
 *            fallbackMethod = "getProductFromCache")
 * public ProductEntity getProduct(Long productId) { ... }
 *
 * public ProductEntity getProductFromCache(Long productId) { ... }
 * }</pre>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 1.0.0
 * @see CacheLock
 * @see RedissonClient
 * @see RLock
 */
@Slf4j
@Aspect
@Component
@lombok.RequiredArgsConstructor
public class CacheLockAspect {

    /** SpEL 表达式前缀标识 */
    private static final String SPEL_PREFIX = "#";

    /** Redis 分布式锁键前缀 */
    private static final String LOCK_KEY_PREFIX = "cache:lock:";

    /** Redisson 客户端，提供分布式锁能力 */
    private final RedissonClient redissonClient;

    /** SpEL 表达式解析器，用于解析动态锁键 */
    private final SpelExpressionParser parser = new SpelExpressionParser();

    /** 参数名发现器，用于获取方法参数的名称 */
    private final ParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();

    /** 降级方法缓存，避免重复反射查找 */
    private final Map<String, Method> fallbackMethodCache = new ConcurrentHashMap<>();

    /**
     * 分布式锁环绕通知
     *
     * <p>拦截所有标注 {@link CacheLock} 注解的方法，先尝试获取分布式锁，
     * 获取成功后执行目标方法，失败则根据降级策略处理。
     *
     * @param joinPoint 连接点，提供目标方法的执行上下文
     * @param cacheLock 缓存锁注解实例
     * @return 目标方法的执行结果或降级策略返回值
     * @throws Throwable 获取锁失败且策略为 THROW_EXCEPTION 时抛出异常，或目标方法执行异常
     */
    @Around("@annotation(cacheLock)")
    public Object around(ProceedingJoinPoint joinPoint, CacheLock cacheLock) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();

        String key = parseKey(cacheLock.key(), method, args);
        String lockKey = LOCK_KEY_PREFIX + cacheLock.prefix() + ":" + key;

        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired = false;

        try {
            acquired = lock.tryLock(cacheLock.waitTime(), cacheLock.leaseTime(), cacheLock.timeUnit());
            if (!acquired) {
                log.warn("获取分布式锁失败，执行降级策略: {}", lockKey);
                return handleFallback(joinPoint, method, args, cacheLock);
            }
            log.debug("获取分布式锁成功: {}", lockKey);
            return joinPoint.proceed();
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                try {
                    lock.unlock();
                    log.debug("释放分布式锁: {}", lockKey);
                } catch (IllegalMonitorStateException e) {
                    log.warn("释放锁异常（可能已自动过期）: {}", lockKey, e);
                }
            }
        }
    }

    /**
     * 处理获取锁失败时的降级策略
     *
     * @param joinPoint  连接点
     * @param method     目标方法
     * @param args       方法参数
     * @param cacheLock  缓存锁注解
     * @return 降级策略返回值
     * @throws Throwable 降级方法执行异常
     */
    private Object handleFallback(ProceedingJoinPoint joinPoint, Method method, Object[] args, CacheLock cacheLock)
            throws Throwable {
        FallbackStrategy strategy = cacheLock.fallbackStrategy();

        return switch (strategy) {
            case RETURN_NULL -> {
                log.debug("降级策略: RETURN_NULL");
                yield null;
            }
            case RETURN_DEFAULT -> {
                log.debug("降级策略: RETURN_DEFAULT, 降级方法: {}", cacheLock.fallbackMethod());
                yield invokeFallbackMethod(joinPoint.getTarget(), method, args, cacheLock.fallbackMethod());
            }
            case THROW_EXCEPTION -> {
                log.debug("降级策略: THROW_EXCEPTION");
                throw new BusinessException(ResultCode.LOCK_ERROR, cacheLock.errorMessage());
            }
        };
    }

    /**
     * 调用降级方法
     *
     * <p>通过反射调用同类中的降级方法，降级方法必须与原方法参数列表完全相同。
     *
     * @param target         目标对象
     * @param originalMethod 原方法
     * @param args           方法参数
     * @param fallbackMethodName 降级方法名称
     * @return 降级方法执行结果
     * @throws Throwable 反射调用异常
     */
    private Object invokeFallbackMethod(Object target, Method originalMethod, Object[] args, String fallbackMethodName)
            throws Throwable {
        if (fallbackMethodName == null || fallbackMethodName.isBlank()) {
            log.warn("未配置降级方法名称，返回 null");
            return null;
        }

        String cacheKey = target.getClass().getName() + "." + fallbackMethodName;
        Method fallbackMethod = fallbackMethodCache.computeIfAbsent(cacheKey, key -> {
            try {
                return target.getClass().getMethod(fallbackMethodName, originalMethod.getParameterTypes());
            } catch (NoSuchMethodException e) {
                log.error("降级方法不存在: {}", fallbackMethodName, e);
                return null;
            }
        });

        if (fallbackMethod == null) {
            log.warn("降级方法查找失败，返回 null");
            return null;
        }

        try {
            return fallbackMethod.invoke(target, args);
        } catch (InvocationTargetException e) {
            log.error("降级方法执行失败: {}", fallbackMethodName, e.getTargetException());
            throw e.getTargetException();
        } catch (IllegalAccessException e) {
            log.error("降级方法访问失败: {}", fallbackMethodName, e);
            throw e;
        }
    }

    /**
     * 解析 SpEL 锁键表达式
     *
     * <p>将注解中的 SpEL 表达式（如 {@code #userId}）解析为实际的方法参数值。
     * 如果 key 不是 SpEL 表达式，则直接返回原始字符串。
     *
     * @param key    注解中配置的 key 值
     * @param method 目标方法
     * @param args   目标方法的参数值数组
     * @return 解析后的锁键字符串
     */
    private String parseKey(String key, Method method, Object[] args) {
        if (!key.contains(SPEL_PREFIX)) {
            return key;
        }

        EvaluationContext context = new StandardEvaluationContext();
        String[] parameterNames = nameDiscoverer.getParameterNames(method);
        if (parameterNames != null) {
            IntStream.range(0, parameterNames.length).forEach(i -> context.setVariable(parameterNames[i], args[i]));
        }

        Expression expression = parser.parseExpression(key);
        return expression.getValue(context, String.class);
    }
}
