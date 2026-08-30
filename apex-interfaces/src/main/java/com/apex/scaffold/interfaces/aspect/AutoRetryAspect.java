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

@Slf4j
@Aspect
@Component
@Order(1)
public class AutoRetryAspect {

    private static final String METRIC_RETRY = "apex.retry.auto.count";
    private final MeterRegistry meterRegistry;

    public AutoRetryAspect(@Nullable MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

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
            long retryDelay = Math.min(
                    maxDelay,
                    (long) (delay * Math.pow(multiplier, signal.totalRetries())));
            return Mono.delay(java.time.Duration.ofMillis(Math.max(0, retryDelay)))
                    .thenReturn(signal);
        }));
    }

    private boolean isRetryable(Throwable error, AutoRetry annotation) {
        for (Class<? extends Throwable> type : annotation.retryOn()) {
            if (type.isInstance(error)) {
                return true;
            }
        }
        return false;
    }

    private long delay(AutoRetry annotation, int attempt) {
        double multiplier = Math.max(1.0, annotation.backoffMultiplier());
        return Math.min(annotation.maxDelayMs(), (long) (annotation.retryDelayMs() * Math.pow(multiplier, attempt - 1)));
    }

    private void sleep(long delay) throws InterruptedException {
        if (delay > 0) {
            Thread.sleep(delay);
        }
    }

    private Throwable unwrap(Throwable error) {
        if (error instanceof InvocationTargetException invocation && invocation.getCause() != null) {
            return invocation.getCause();
        }
        return error;
    }

    private void recordRetry() {
        if (meterRegistry != null) {
            meterRegistry.counter(METRIC_RETRY, "result", "retried").increment();
        }
    }
}
