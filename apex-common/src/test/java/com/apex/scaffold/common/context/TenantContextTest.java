package com.apex.scaffold.common.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

class TenantContextTest {

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void tenantContextShouldBeAvailableAfterThreadSwitchAndClearedAfterCompletion() {
        Mono<String> source = TenantContext.withTenant(
                Mono.defer(() -> Mono.just(TenantContext.currentTenantId().orElse("missing"))));

        StepVerifier.create(source.publishOn(reactor.core.scheduler.Schedulers.boundedElastic())
                        .contextWrite(TenantContext.withTenant("tenant-a")))
                .expectNext("tenant-a")
                .verifyComplete();

        assertThat(TenantContext.currentTenantId()).isEmpty();
    }

    @Test
    void tenantContextShouldBeAvailableInsideSubscribeOnTask() {
        Mono<String> source = TenantContext.withTenant(
                Mono.fromCallable(() -> TenantContext.currentTenantId().orElse("missing"))
                        .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic()));

        StepVerifier.create(source.contextWrite(TenantContext.withTenant("tenant-a")))
                .expectNext("tenant-a")
                .verifyComplete();

        assertThat(TenantContext.currentTenantId()).isEmpty();
    }

    @Test
    void tenantContextShouldNotLeakAcrossReusedSchedulerThread() {
        Scheduler scheduler = Schedulers.newSingle("tenant-context-isolation");
        try {
            Mono<String> tenantTask = tenantFromScheduledTask(scheduler);

            StepVerifier.create(tenantTask.contextWrite(TenantContext.withTenant("tenant-a")))
                    .expectNext("tenant-a")
                    .verifyComplete();
            StepVerifier.create(tenantTask.contextWrite(TenantContext.withTenant("tenant-b")))
                    .expectNext("tenant-b")
                    .verifyComplete();
            StepVerifier.create(tenantFromScheduledTask(scheduler))
                    .expectNext("missing")
                    .verifyComplete();
        } finally {
            scheduler.dispose();
        }
    }

    @Test
    void tenantContextShouldBeRestoredAfterScheduledTaskFailure() {
        Scheduler scheduler = Schedulers.newSingle("tenant-context-failure");
        try {
            Mono<String> failingTask = TenantContext.withTenant(Mono.<String>fromCallable(() -> {
                        throw new IllegalStateException(
                                TenantContext.currentTenantId().orElse("missing"));
                    })
                    .subscribeOn(scheduler));

            StepVerifier.create(failingTask.contextWrite(TenantContext.withTenant("tenant-a")))
                    .expectErrorMatches(
                            error -> error instanceof IllegalStateException && "tenant-a".equals(error.getMessage()))
                    .verify();
            StepVerifier.create(tenantFromScheduledTask(scheduler))
                    .expectNext("missing")
                    .verifyComplete();
        } finally {
            scheduler.dispose();
        }
    }

    @Test
    void tenantContextShouldBeClearedAfterCancellation() {
        Mono<String> source = TenantContext.withTenant(Mono.never());

        StepVerifier.create(source.timeout(Duration.ofMillis(10)).contextWrite(TenantContext.withTenant("tenant-a")))
                .expectError()
                .verify();

        assertThat(TenantContext.currentTenantId()).isEmpty();
    }

    private Mono<String> tenantFromScheduledTask(Scheduler scheduler) {
        return TenantContext.withTenant(
                Mono.fromCallable(() -> TenantContext.currentTenantId().orElse("missing"))
                        .subscribeOn(scheduler));
    }
}
