package com.apex.scaffold.interfaces.aspect;

import com.apex.scaffold.common.annotation.AutoRetry;
import io.micrometer.core.instrument.MeterRegistry;
import java.lang.reflect.InvocationTargetException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * 方法级自动重试切面。
 *
 * <p>拦截标注 {@link AutoRetry} 注解的方法，按照「固定最大次数 + 指数退避」策略
 * 对可重试的失败进行自动重试。该切面同时支持两种调用形态：</p>
 *
 * <ul>
 *   <li><strong>同步方法</strong>：返回普通对象，若执行抛出可重试异常，则在当前线程
 *       按退避策略等待后重试；</li>
 *   <li><strong>响应式方法</strong>：返回 {@link Mono} 或 {@link Flux}，若其数据流
 *       在传播过程中失败（如下游网络调用出错），则基于 Reactor 的
 *       {@link Retry} 机制在异步流水线上重放整个发布流。</li>
 * </ul>
 *
 * <h2>重试策略</h2>
 * <ol>
 *   <li><strong>最大尝试次数</strong>：{@link AutoRetry#maxAttempts()}，
 *       含首次执行，最多尝试该次数；</li>
 *   <li><strong>指数退避</strong>：每次重试间隔 = {@code retryDelayMs × multiplier^(attempt-1)}，
 *       并以 {@code maxDelayMs} 为上限封顶，避免间隔无限增长；</li>
 *   <li><strong>可重试异常过滤</strong>：仅当抛出异常命中
 *       {@link AutoRetry#retryOn()} 声明的类型时才会重试，其余异常立即上抛；</li>
 *   <li><strong>异常解包</strong>：自动剥离 {@link InvocationTargetException} 等包装层，
 *       以其真实根因参与可重试性判断。</li>
 * </ol>
 *
 * <h2>使用前提</h2>
 * <p>仅应在业务方法本身<strong>幂等</strong>、或重试失败可由上层兜底回滚的场景使用
 * （与 {@link AutoRetry} 注解约定一致），否则重复执行可能造成副作用累积。</p>
 *
 * <h2>可观测性</h2>
 * <p>每次实际触发的重试都会递增 Micrometer 计数器
 * {@code apex.retry.auto.count}（tag {@code result=retried}），
 * 便于在监控面板上观测重试频次。指标注册表缺失时为安全空操作。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see AutoRetry
 */
@Slf4j
@Aspect
@Component
@Order(1)
public class AutoRetryAspect {

    /** 自动重试指标名（Micrometer Counter）。 */
    private static final String METRIC_RETRY = "apex.retry.auto.count";

    /** Micrometer 指标注册表，可能为空（未装配时）。 */
    private final MeterRegistry meterRegistry;

    /**
     * 构造自动重试切面。
     *
     * @param meterRegistry Micrometer 指标注册表；可为 {@code null}，此时重试指标记录为空操作
     */
    public AutoRetryAspect(@Nullable MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * 自动重试环绕通知。
     *
     * <p>拦截所有标注 {@link AutoRetry} 注解的方法，将其返回值按形态分发：
     * 普通对象走同步循环重试，{@link Mono}/{@link Flux} 走 Reactor 异步重试。</p>
     *
     * @param joinPoint  连接点，用于执行目标方法
     * @param annotation 目标方法上的重试配置注解
     * @return 目标方法的执行结果（无论是普通对象、{@link Mono} 还是 {@link Flux}）
     * @throws Throwable 重试次数耗尽，或抛出不可重试异常时向上传递
     */
    @Around("@annotation(annotation)")
    public Object around(ProceedingJoinPoint joinPoint, AutoRetry annotation) throws Throwable {
        int maxAttempts = Math.max(1, annotation.maxAttempts());
        Throwable failure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                Object result = joinPoint.proceed();
                if (result instanceof Mono<?> mono) {
                    return mono.retryWhen(retrySpec(annotation));
                }
                if (result instanceof Flux<?> flux) {
                    return flux.retryWhen(retrySpec(annotation));
                }
                return result;
            } catch (Throwable error) {
                failure = unwrap(error);
                if (attempt == maxAttempts || !isRetryable(failure, annotation)) {
                    throw failure;
                }
                recordRetry();
                sleep(delay(annotation, attempt));
            }
        }
        throw failure;
    }

    /**
     * 构造响应式流的重试规格（Reactor {@link Retry}）。
     *
     * <p>用于 {@link Mono}/{@link Flux} 返回值形态：对每个失败信号判断可重试性，
     * 达到最大重试次数或命中不可重试异常时终止，否则按指数退避延时后重放</p>
     *
     * @param annotation 重试配置注解
     * @return 配置好退避与终止条件的 {@link Retry} 实例
     */
    private Retry retrySpec(AutoRetry annotation) {
        int retries = Math.max(0, annotation.maxAttempts() - 1);
        long delay = Math.max(0, annotation.retryDelayMs());
        long maxDelay = Math.max(delay, annotation.maxDelayMs());
        double multiplier = Math.max(1.0, annotation.backoffMultiplier());
        return Retry.from(signals -> signals.concatMap(signal -> {
            Throwable failure = unwrap(signal.failure());
            if (!isRetryable(failure, annotation)) {
                return Mono.error(failure);
            }
            if (signal.totalRetries() >= retries) {
                return Mono.error(failure);
            }
            recordRetry();
            long retryDelay = Math.min(maxDelay, (long) (delay * Math.pow(multiplier, signal.totalRetries())));
            return Mono.delay(java.time.Duration.ofMillis(Math.max(0, retryDelay)))
                    .thenReturn(signal);
        }));
    }

    /**
     * 判断异常是否可重试。
     *
     * <p>遍历 {@link AutoRetry#retryOn()} 声明的异常类型，
     * 若该异常是其（含其子类）实例则判定为可重试，否则不可重试。</p>
     *
     * @param error      待判断的（已解包的）异常，不会为空
     * @param annotation 重试配置注解，提供 {@code retryOn} 类型列表
     * @return 命中可重试类型时返回 {@code true}，否则返回 {@code false}
     */
    private boolean isRetryable(Throwable error, AutoRetry annotation) {
        for (Class<? extends Throwable> type : annotation.retryOn()) {
            if (type.isInstance(error)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 计算同步重试前应等待的毫秒数（指数退避）。
     *
     * <p>退避公式：{@code retryDelayMs × multiplier^(attempt-1)}，
     * 并以 {@code maxDelayMs} 封顶。注意原始实现未做下限约束，若注解配了
     * 负的初始延时可能导致休眠异常，故在 {@link #sleep(long)} 中做了安全处理。</p>
     *
     * @param annotation 重试配置注解，提供退避参数
     * @param attempt    当前是第几次执行（从 1 开始），用于计算指数项
     * @return 本次重试前应等待的毫秒数
     */
    private long delay(AutoRetry annotation, int attempt) {
        double multiplier = Math.max(1.0, annotation.backoffMultiplier());
        return Math.min(
                annotation.maxDelayMs(), (long) (annotation.retryDelayMs() * Math.pow(multiplier, attempt - 1)));
    }

    /**
     * 阻塞当前线程指定时长（用于同步重试的退避等待）。
     *
     * @param delay 等待的毫秒数；小于等于 0 时直接返回，不做任何等待
     * @throws InterruptedException 线程在等待期间被中断
     */
    private void sleep(long delay) throws InterruptedException {
        if (delay > 0) {
            Thread.sleep(delay);
        }
    }

    /**
     * 解包异常，剥离 AOP/反射代理包装层。
     *
     * <p>当异常为 {@link InvocationTargetException} 且其存在真实根因时，
     * 返回其 {@code cause}，否则原样返回，保证可重试性判断基于真实业务异常。</p>
     *
     * @param error 待解包的原始异常，不会为空
     * @return 解包后的真实根因；非包装异常时原样返回
     */
    private Throwable unwrap(Throwable error) {
        if (error instanceof InvocationTargetException invocation && invocation.getCause() != null) {
            return invocation.getCause();
        }
        return error;
    }

    /**
     * 记录一次实际触发的重试到指标系统。
     *
     * <p>对计数器 {@code apex.retry.auto.count}（tag {@code result=retried}）
     * 做递增；当指标注册表缺失（为 {@code null}）时为空操作。</p>
     */
    private void recordRetry() {
        if (meterRegistry != null) {
            meterRegistry.counter(METRIC_RETRY, "result", "retried").increment();
        }
    }
}
