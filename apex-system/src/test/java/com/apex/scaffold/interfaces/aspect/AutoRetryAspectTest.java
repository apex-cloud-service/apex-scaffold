package com.apex.scaffold.interfaces.aspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.annotation.AutoRetry;
import java.util.concurrent.atomic.AtomicInteger;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AutoRetryAspectTest {

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private AutoRetry annotation;

    @Test
    void retriesSynchronousMethodUntilSuccess() throws Throwable {
        AtomicInteger attempts = new AtomicInteger();
        when(annotation.maxAttempts()).thenReturn(3);
        when(annotation.retryDelayMs()).thenReturn(0L);
        when(annotation.retryOn()).thenReturn(new Class[] {IllegalStateException.class});
        when(joinPoint.proceed()).thenAnswer(invocation -> {
            if (attempts.incrementAndGet() < 3) {
                throw new IllegalStateException("transient");
            }
            return "ok";
        });

        Object result = new AutoRetryAspect(null).around(joinPoint, annotation);

        assertThat(result).isEqualTo("ok");
        assertThat(attempts).hasValue(3);
    }

    @Test
    void doesNotRetryNonMatchingSynchronousException() throws Throwable {
        when(annotation.maxAttempts()).thenReturn(3);
        when(annotation.retryOn()).thenReturn(new Class[] {IllegalStateException.class});
        when(joinPoint.proceed()).thenThrow(new IllegalArgumentException("permanent"));

        assertThatThrownBy(() -> new AutoRetryAspect(null).around(joinPoint, annotation))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("permanent");
    }

    @Test
    void retriesMonoWithoutBlocking() throws Throwable {
        AtomicInteger attempts = new AtomicInteger();
        when(annotation.maxAttempts()).thenReturn(3);
        when(annotation.retryDelayMs()).thenReturn(0L);
        when(annotation.retryOn()).thenReturn(new Class[] {IllegalStateException.class});
        when(joinPoint.proceed())
                .thenAnswer(invocation -> Mono.defer(() -> {
                    if (attempts.incrementAndGet() < 3) {
                        return Mono.error(new IllegalStateException("transient"));
                    }
                    return Mono.just("ok");
                }));

        Object result = new AutoRetryAspect(null).around(joinPoint, annotation);

        StepVerifier.create((Mono<String>) result).expectNext("ok").verifyComplete();
        assertThat(attempts).hasValue(3);
    }

    @Test
    void retriesFluxUntilCompletion() throws Throwable {
        AtomicInteger attempts = new AtomicInteger();
        when(annotation.maxAttempts()).thenReturn(2);
        when(annotation.retryDelayMs()).thenReturn(0L);
        when(annotation.retryOn()).thenReturn(new Class[] {IllegalStateException.class});
        when(joinPoint.proceed())
                .thenAnswer(invocation -> Flux.defer(() -> {
                    if (attempts.incrementAndGet() == 1) {
                        return Flux.error(new IllegalStateException("transient"));
                    }
                    return Flux.just("ok");
                }));

        Object result = new AutoRetryAspect(null).around(joinPoint, annotation);

        StepVerifier.create((Flux<String>) result).expectNext("ok").verifyComplete();
        assertThat(attempts).hasValue(2);
    }
}
