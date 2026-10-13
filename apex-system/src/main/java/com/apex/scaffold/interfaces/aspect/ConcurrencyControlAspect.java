package com.apex.scaffold.interfaces.aspect;

import static com.apex.scaffold.common.scheduler.BlockingIoSchedulers.cache;

import com.apex.scaffold.common.annotation.ConcurrencyControl;
import com.apex.scaffold.common.exception.BusinessException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 并发控制切面
 *
 * <p>实现方法级别的并发控制，防止同一方法被过多并发调用。
 * 基于 {@link Semaphore} 实现信号量机制（可扩展至 Redisson RSemaphore 分布式信号量），
 * 支持响应式和非响应式两种调用方式。
 *
 * <h2>工作原理</h2>
 * <ul>
 *   <li>通过 {@link ConcurrencyControl} 注解标注需要控制并发的方法</li>
 *   <li>以方法签名为 Key，维护独立的 {@link Semaphore} 实例</li>
 *   <li>对于返回 {@link Mono} 的方法，将信号量获取操作包装在
 *       {@link com.apex.scaffold.common.scheduler.BlockingIoSchedulers#cache()}
 *       调度器上执行，避免阻塞事件循环线程，同时不挤占 JDBC 核心业务池</li>
 *   <li>对于同步方法，直接在当前线程执行信号量获取</li>
 *   <li>获取信号量失败时抛出 {@link BusinessException}，拒绝执行目标方法</li>
 *   <li>在 finally 块中释放信号量，确保不会发生资源泄漏</li>
 * </ul>
 *
 * <h2>线程模型</h2>
 * <p>响应式路径下信号量 {@code tryAcquire(timeout)} 阻塞调用由 cache() 专用池承载；
 * 若后续升级为 Redisson 分布式 RSemaphore，该调度器选择无需变更。</p>
 *
 * @author Apex Team
 * @version 2.1.0
 * @since 1.0.0
 * @see ConcurrencyControl
 * @see Semaphore
 */
@Slf4j
@Aspect
@Component
public class ConcurrencyControlAspect {

    /**
     * 构造并发控制切面。
     *
     * <p>初始化用于存放各方法信号量的线程安全映射表。</p>
     */
    public ConcurrencyControlAspect() {}

    /** 并发控制信号量映射表，Key 为方法签名，Value 为对应的信号量 */
    private final Map<String, Semaphore> semaphoreMap = new ConcurrentHashMap<>();

    /**
     * 并发控制拦截
     *
     * <p>拦截所有标注 {@link ConcurrencyControl} 注解的方法，根据返回类型采取不同的处理策略：
     * <ul>
     *   <li>若返回 {@link Mono}，将信号量获取包装在 {@link Mono#fromCallable}
     *       中并通过 {@code cache()} 专用池执行，避免阻塞事件循环线程</li>
     *   <li>若返回其他类型，在当前线程同步执行信号量获取</li>
     * </ul>
     *
     * @param joinPoint 切点
     * @param annotation 并发控制注解
     * @return 方法执行结果
     * @throws Throwable 执行异常
     */
    @Around("@annotation(annotation)")
    public Object around(ProceedingJoinPoint joinPoint, ConcurrencyControl annotation) throws Throwable {
        String key = joinPoint.getSignature().toString();
        int maxConcurrent = annotation.maxConcurrent();
        long timeout = annotation.timeout();
        String message = annotation.message();

        Semaphore semaphore = semaphoreMap.computeIfAbsent(key, k -> new Semaphore(maxConcurrent, true));

        // 获取目标方法的返回类型，判断是否为响应式类型
        boolean isReactive = isReactiveReturnType(joinPoint);

        if (isReactive) {
            return Mono.fromCallable(() -> {
                        boolean acquired = semaphore.tryAcquire(timeout, TimeUnit.MILLISECONDS);
                        if (!acquired) {
                            log.warn("并发控制拒绝, key: {}, maxConcurrent: {}", key, maxConcurrent);
                            throw new BusinessException(message);
                        }
                        log.debug("并发控制通过, key: {}, availablePermits: {}", key, semaphore.availablePermits());
                        return true;
                    })
                    .subscribeOn(cache())
                    .flatMap(acquired -> Mono.defer(() -> {
                        try {
                            return (Mono<?>) joinPoint.proceed();
                        } catch (Throwable e) {
                            return Mono.error(e);
                        }
                    }))
                    .doFinally(signalType -> {
                        semaphore.release();
                        log.debug("并发控制释放, key: {}, availablePermits: {}", key, semaphore.availablePermits());
                    });
        } else {
            // 同步路径：直接在当前线程获取信号量
            try {
                boolean acquired = semaphore.tryAcquire(timeout, TimeUnit.MILLISECONDS);
                if (!acquired) {
                    log.warn("并发控制拒绝, key: {}, maxConcurrent: {}", key, maxConcurrent);
                    throw new BusinessException(message);
                }

                log.debug("并发控制通过, key: {}, availablePermits: {}", key, semaphore.availablePermits());

                return joinPoint.proceed();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("并发控制中断, key: {}", key, e);
                throw new BusinessException("系统繁忙，请稍后再试");
            } finally {
                semaphore.release();
                log.debug("并发控制释放, key: {}, availablePermits: {}", key, semaphore.availablePermits());
            }
        }
    }

    /**
     * 判断目标方法的返回类型是否为响应式类型（{@link Mono}）
     *
     * @param joinPoint 连接点
     * @return 如果返回类型是 {@link Mono} 则返回 true
     */
    private boolean isReactiveReturnType(ProceedingJoinPoint joinPoint) {
        if (joinPoint.getSignature() instanceof org.aspectj.lang.reflect.MethodSignature) {
            Class<?> returnType = ((org.aspectj.lang.reflect.MethodSignature) joinPoint.getSignature()).getReturnType();
            return Mono.class.isAssignableFrom(returnType);
        }
        return false;
    }
}
