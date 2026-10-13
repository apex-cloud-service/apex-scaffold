package com.apex.scaffold.interfaces.aspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.annotation.DataScope;
import com.apex.scaffold.common.context.DataScopeConfig;
import com.apex.scaffold.common.context.DataScopeContext;
import com.apex.scaffold.common.context.DataScopeInfo;
import com.apex.scaffold.common.context.DataScopeJdbcBridge;
import com.apex.scaffold.common.context.DataScopeProvider;
import com.apex.scaffold.common.context.ReactiveDataScopeProvider;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class DataScopeAspectTest {

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
    }

    @Test
    void springContextMustInjectBothDataScopeProviders() {
        DataScopeProvider syncProvider = Optional::empty;
        ReactiveDataScopeProvider reactiveProvider = Mono::empty;
        try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            context.registerBean(DataScopeProvider.class, () -> syncProvider);
            context.registerBean(ReactiveDataScopeProvider.class, () -> reactiveProvider);
            context.register(DataScopeAspect.class);
            context.refresh();
            assertThat(context.getBean(DataScopeAspect.class)).isNotNull();
        }
    }

    @Test
    void shouldHaveSingleConstructorForSpringInjection() {
        Constructor<?>[] constructors = DataScopeAspect.class.getDeclaredConstructors();

        assertThat(constructors).singleElement().satisfies(constructor -> assertThat(constructor.getParameterTypes())
                .containsExactly(DataScopeProvider.class, ReactiveDataScopeProvider.class));
    }

    @Test
    void shouldResolveProviderAtEachMonoSubscriptionAndWriteScopeToReactorContext() throws Throwable {
        AtomicInteger resolves = new AtomicInteger();
        DataScopeProvider provider = () -> {
            resolves.incrementAndGet();
            return Optional.of(DataScopeInfo.ofDept(10L));
        };
        ReactiveDataScopeProvider reactiveProvider = () -> Mono.justOrEmpty(provider.resolve());
        ProceedingJoinPoint joinPoint = joinPoint(
                Mono.class,
                Mono.deferContextual(contextView -> Mono.just(new Observation(
                        contextView.get(DataScopeJdbcBridge.CONTEXT_KEY),
                        DataScopeContext.currentScope().isPresent()))));

        Mono<Observation> result =
                castMono(new DataScopeAspect(provider, reactiveProvider).applyDataScope(joinPoint, annotation()));

        assertThat(resolves).hasValue(0);
        StepVerifier.create(result.repeat(1))
                .assertNext(observation -> {
                    assertThat(observation.scope().info().deptId()).isEqualTo(10L);
                    assertThat(observation.threadLocalPresent()).isFalse();
                })
                .assertNext(observation -> {
                    assertThat(observation.scope().info().deptId()).isEqualTo(10L);
                    assertThat(observation.threadLocalPresent()).isFalse();
                })
                .verifyComplete();
        assertThat(resolves).hasValue(2);
        verify(joinPoint).proceed();
    }

    @Test
    void shouldResolveReactiveProviderFromSecurityContextAtSubscription() throws Throwable {
        ReactiveDataScopeProvider reactiveProvider = () -> Mono.just(DataScopeInfo.ofSelf(7L));
        DataScopeProvider syncProvider = Optional::empty;
        ProceedingJoinPoint joinPoint = joinPoint(
                Mono.class,
                Mono.deferContextual(contextView -> Mono.just(contextView.get(DataScopeJdbcBridge.CONTEXT_KEY))));

        Mono<DataScopeContext.Scope> result =
                castMono(new DataScopeAspect(syncProvider, reactiveProvider).applyDataScope(joinPoint, annotation()));

        StepVerifier.create(result)
                .assertNext(scope -> assertThat(scope.info().userId()).isEqualTo(7L))
                .verifyComplete();
    }

    @Test
    void shouldFailMonoSubscriptionWhenProviderIsEmpty() throws Throwable {
        ProceedingJoinPoint joinPoint = joinPoint(Mono.class, Mono.just("value"));
        DataScopeProvider provider = Optional::empty;
        ReactiveDataScopeProvider reactiveProvider = () -> Mono.justOrEmpty(provider.resolve());
        Mono<?> result =
                castMono(new DataScopeAspect(provider, reactiveProvider).applyDataScope(joinPoint, annotation()));

        StepVerifier.create(result)
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("Required data scope could not be resolved"))
                .verify();
    }

    @Test
    void shouldResolveProviderAtFluxSubscription() throws Throwable {
        AtomicInteger resolves = new AtomicInteger();
        DataScopeProvider provider = () -> {
            resolves.incrementAndGet();
            return Optional.of(DataScopeInfo.all());
        };
        ReactiveDataScopeProvider reactiveProvider = () -> Mono.justOrEmpty(provider.resolve());
        ProceedingJoinPoint joinPoint = joinPoint(
                Flux.class,
                Flux.deferContextual(contextView -> Flux.just(contextView.get(DataScopeJdbcBridge.CONTEXT_KEY))));

        Flux<DataScopeContext.Scope> result =
                castFlux(new DataScopeAspect(provider, reactiveProvider).applyDataScope(joinPoint, annotation()));

        assertThat(resolves).hasValue(0);
        StepVerifier.create(result)
                .assertNext(scope -> assertThat(scope.info().isAll()).isTrue())
                .verifyComplete();
        assertThat(resolves).hasValue(1);
    }

    @Test
    void shouldRestoreOuterScopeForSynchronousMethod() throws Throwable {
        DataScopeContext.Scope outer = new DataScopeContext.Scope(DataScopeInfo.ofDept(1L), DataScopeConfig.defaults());
        DataScopeInfo inner = DataScopeInfo.ofSelf(2L);
        ProceedingJoinPoint joinPoint = joinPoint(String.class, null);
        when(joinPoint.proceed())
                .thenAnswer(invocation -> DataScopeContext.currentScope().orElseThrow());

        DataScopeProvider syncProvider = () -> Optional.of(inner);
        ReactiveDataScopeProvider reactiveProvider = () -> Mono.just(inner);

        try (AutoCloseable ignored = DataScopeContext.install(outer)) {
            Object observed =
                    new DataScopeAspect(syncProvider, reactiveProvider).applyDataScope(joinPoint, annotation());

            assertThat(observed).isEqualTo(new DataScopeContext.Scope(inner, DataScopeConfig.defaults()));
            assertThat(DataScopeContext.currentScope()).contains(outer);
        }
    }

    private ProceedingJoinPoint joinPoint(Class<?> returnType, Object result) throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getReturnType()).thenReturn(returnType);
        when(signature.getName()).thenReturn("secured");
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.proceed()).thenReturn(result);
        return joinPoint;
    }

    private DataScope annotation() throws NoSuchMethodException {
        Method method = SecuredMethods.class.getDeclaredMethod("secured");
        return method.getAnnotation(DataScope.class);
    }

    @SuppressWarnings("unchecked")
    private <T> Mono<T> castMono(Object value) {
        return (Mono<T>) value;
    }

    @SuppressWarnings("unchecked")
    private <T> Flux<T> castFlux(Object value) {
        return (Flux<T>) value;
    }

    private record Observation(DataScopeContext.Scope scope, boolean threadLocalPresent) {}

    private static final class SecuredMethods {
        @DataScope
        private void secured() {}
    }
}
