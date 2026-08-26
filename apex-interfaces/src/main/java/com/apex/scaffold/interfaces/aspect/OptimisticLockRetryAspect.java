package com.apex.scaffold.interfaces.aspect;

import com.apex.scaffold.common.annotation.OptimisticLockRetry;
import com.apex.scaffold.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

/**
 * 数据库乐观锁重试切面
 *
 * <p>拦截标注 {@link OptimisticLockRetry} 注解的方法，在检测到
 * {@link OptimisticLockingFailureException} 乐观锁冲突异常时，
 * 自动进行方法级重试，直至成功或达到最大重试次数。
 *
 * <h2>重试策略</h2>
 * <ol>
 *   <li><strong>立即重试</strong>：首次冲突后稍作等待立即重试</li>
 *   <li><strong>指数退避</strong>：连续冲突时，重试间隔逐步增加（最多扩大 3 倍）</li>
 *   <li><strong>快速失败</strong>：达到最大重试次数后抛出 {@link BusinessException}</li>
 * </ol>
 *
 * <h2>与 MyBatis-Plus 集成</h2>
 * <p>MyBatis-Plus 的 {@code updateById} 在 version 字段冲突时原生返回 0 行；
 * 基础设施层的 {@code OptimisticLockUpdateAspect} 将其转换为
 * {@link OptimisticLockingFailureException}，此切面再捕获该异常并触发同步方法重试。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see OptimisticLockRetry
 */
@Slf4j
@Aspect
@Component
@Order(0)
public class OptimisticLockRetryAspect {

    /** 最大退避倍数，指数退避算法的上限，防止重试间隔增长到不合理的级别。 */
    private static final long MAX_BACKOFF_MULTIPLIER = 3L;

    /**
     * 构造乐观锁重试切面
     */
    public OptimisticLockRetryAspect() {}

    /**
     * 乐观锁重试环绕通知
     *
     * <p>拦截 {@link OptimisticLockRetry} 注解的方法。
     * 当捕获到 {@link OptimisticLockingFailureException} 时，
     * 根据注解配置自动重试。
     *
     * @param joinPoint 连接点
     * @param annotation 乐观锁重试注解
     * @return 目标方法执行结果
     * @throws Throwable 重试耗尽或非乐观锁异常
     */
    @Around("@annotation(annotation)")
    public Object around(ProceedingJoinPoint joinPoint, OptimisticLockRetry annotation) throws Throwable {
        int maxRetries = annotation.maxRetries();
        long retryDelay = annotation.retryDelay();
        boolean idempotent = annotation.idempotent();

        // 首次执行尝试（计入重试次数）
        int attempt = 0;
        while (attempt <= maxRetries) {
            if (attempt > 0) {
                // 非首次执行：等待后重试
                long backoffDelay = Math.min(retryDelay * attempt, retryDelay * MAX_BACKOFF_MULTIPLIER);
                try {
                    Thread.sleep(backoffDelay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new BusinessException("操作被中断");
                }
                log.debug(
                        "乐观锁重试第 {} 次, 方法: {}.{}, 等待: {}ms",
                        attempt,
                        joinPoint.getSignature().getDeclaringType().getSimpleName(),
                        joinPoint.getSignature().getName(),
                        backoffDelay);
            }

            try {
                return joinPoint.proceed();
            } catch (OptimisticLockingFailureException e) {
                attempt++;
                if (attempt > maxRetries) {
                    log.warn(
                            "乐观锁重试耗尽, 方法: {}.{}, attempts: {}",
                            joinPoint.getSignature().getDeclaringType().getSimpleName(),
                            joinPoint.getSignature().getName(),
                            attempt - 1);
                    throw new BusinessException(annotation.message());
                }
                log.debug(
                        "乐观锁冲突, 准备重试, 方法: {}.{}. attempt: {}/{}",
                        joinPoint.getSignature().getDeclaringType().getSimpleName(),
                        joinPoint.getSignature().getName(),
                        attempt,
                        maxRetries);
            } catch (RuntimeException e) {
                // 非乐观锁异常直接抛出，不重试
                throw e;
            }
        }

        // 理论上不会执行到此处
        throw new BusinessException(annotation.message());
    }
}
