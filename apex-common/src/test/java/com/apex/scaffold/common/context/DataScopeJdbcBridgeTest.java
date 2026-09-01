package com.apex.scaffold.common.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

class DataScopeJdbcBridgeTest {

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
    }

    @Test
    void shouldFailClearlyWhenRequiredScopeIsMissing() {
        Mono<String> result = Mono.deferContextual(
                contextView -> Mono.fromCallable(DataScopeJdbcBridge.required(contextView, () -> "value")));

        StepVerifier.create(result)
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("Required data scope is missing from Reactor Context"))
                .verify();
    }

    @Test
    void shouldInstallAllScopeDuringCallableAndRestorePreviousScope() {
        DataScopeContext.Scope previous =
                new DataScopeContext.Scope(DataScopeInfo.ofDept(9L), DataScopeConfig.defaults());
        DataScopeContext.Scope all = new DataScopeContext.Scope(DataScopeInfo.all(), DataScopeConfig.defaults());

        try (AutoCloseable ignored = DataScopeContext.install(previous)) {
            Mono<DataScopeContext.Scope> result = Mono.deferContextual(contextView -> Mono.fromCallable(
                            DataScopeJdbcBridge.required(contextView, () -> DataScopeContext.currentScope()
                                    .orElseThrow())))
                    .contextWrite(context -> context.put(DataScopeJdbcBridge.CONTEXT_KEY, all));

            StepVerifier.create(result).expectNext(all).verifyComplete();
            assertThat(DataScopeContext.currentScope()).contains(previous);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    @Test
    void shouldRestorePreviousScopeWhenCallableFails() {
        DataScopeContext.Scope previous =
                new DataScopeContext.Scope(DataScopeInfo.ofDept(9L), DataScopeConfig.defaults());
        DataScopeContext.Scope current =
                new DataScopeContext.Scope(DataScopeInfo.ofSelf(7L), DataScopeConfig.defaults());

        try (AutoCloseable ignored = DataScopeContext.install(previous)) {
            Callable<String> failingCallable = () -> {
                throw new IllegalArgumentException("boom");
            };
            Mono<String> result = Mono.deferContextual(contextView ->
                            Mono.fromCallable(DataScopeJdbcBridge.required(contextView, failingCallable)))
                    .contextWrite(context -> context.put(DataScopeJdbcBridge.CONTEXT_KEY, current));

            StepVerifier.create(result).expectErrorMessage("boom").verify();
            assertThat(DataScopeContext.currentScope()).contains(previous);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    @Test
    void shouldIsolateConcurrentSubscriptionsOnJdbcWorkers() {
        List<DataScopeContext.Scope> observed = new CopyOnWriteArrayList<>();
        DataScopeContext.Scope first = new DataScopeContext.Scope(DataScopeInfo.ofDept(1L), DataScopeConfig.defaults());
        DataScopeContext.Scope second =
                new DataScopeContext.Scope(DataScopeInfo.ofDept(2L), DataScopeConfig.defaults());

        Flux<DataScopeContext.Scope> result =
                Flux.merge(invokeOnJdbcWorker(first, observed), invokeOnJdbcWorker(second, observed));

        StepVerifier.create(result).expectNextCount(2).verifyComplete();
        assertThat(observed).containsExactlyInAnyOrder(first, second);
        assertThat(DataScopeContext.currentScope()).isEmpty();
    }

    private Mono<DataScopeContext.Scope> invokeOnJdbcWorker(
            DataScopeContext.Scope scope, List<DataScopeContext.Scope> observed) {
        return Mono.deferContextual(contextView -> Mono.fromCallable(DataScopeJdbcBridge.required(contextView, () -> {
                            DataScopeContext.Scope current =
                                    DataScopeContext.currentScope().orElseThrow();
                            observed.add(current);
                            return current;
                        }))
                        .subscribeOn(Schedulers.boundedElastic()))
                .contextWrite(context -> context.put(DataScopeJdbcBridge.CONTEXT_KEY, scope));
    }
}
