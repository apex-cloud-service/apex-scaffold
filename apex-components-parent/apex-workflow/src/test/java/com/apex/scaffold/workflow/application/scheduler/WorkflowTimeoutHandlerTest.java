package com.apex.scaffold.workflow.application.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.apex.scaffold.workflow.application.service.WorkflowService;
import com.apex.scaffold.workflow.domain.model.Task;
import com.apex.scaffold.workflow.domain.repository.ScanLease;
import com.apex.scaffold.workflow.domain.repository.TimeoutTaskScan;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

class WorkflowTimeoutHandlerTest {
    private final TimeoutTaskScan repository = mock(TimeoutTaskScan.class);
    private final WorkflowService service = mock(WorkflowService.class);
    private final WorkflowTimeoutHandler handler =
            new WorkflowTimeoutHandler(repository, service, null, false, false, 300);

    @Test
    void hasSingleConstructorForImplicitInjection() {
        var constructors = WorkflowTimeoutHandler.class.getDeclaredConstructors();
        assertThat(constructors).hasSize(1);
        assertThat(Modifier.isPublic(constructors[0].getModifiers())).isTrue();
        assertThat(constructors[0].getParameterTypes())
                .containsExactly(
                        TimeoutTaskScan.class,
                        WorkflowService.class,
                        ScanLease.class,
                        boolean.class,
                        boolean.class,
                        long.class);
        assertThat(constructors[0].getDeclaredAnnotations()).isEmpty();
    }

    @Test
    void springContextInjectsDependenciesAndDefaultLeaseTtl() {
        ScanLease lease = mock(ScanLease.class);
        when(repository.findPendingTasks()).thenReturn(Flux.empty());
        when(lease.acquire(eq("workflow-timeout-scan"), anyString(), eq(Duration.ofSeconds(300))))
                .thenReturn(Mono.just(true));
        when(lease.release(eq("workflow-timeout-scan"), anyString())).thenReturn(Mono.empty());

        new ApplicationContextRunner()
                .withBean(TimeoutTaskScan.class, () -> repository)
                .withBean(WorkflowService.class, () -> service)
                .withBean(ScanLease.class, () -> lease)
                .withUserConfiguration(WorkflowTimeoutHandler.class)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(WorkflowTimeoutHandler.class);
                    StepVerifier.create(context.getBean(WorkflowTimeoutHandler.class)
                                    .scanTimeoutTasks())
                            .verifyComplete();
                    verify(repository).findPendingTasks();
                    verify(lease).acquire(eq("workflow-timeout-scan"), anyString(), eq(Duration.ofSeconds(300)));
                    verify(lease).release(eq("workflow-timeout-scan"), anyString());
                });
    }

    @Test
    void springContextBindsExternalSchedulingAndConfiguredLeaseTtl() {
        ScanLease lease = mock(ScanLease.class);
        when(repository.findPendingTasks()).thenReturn(Flux.empty());
        when(lease.acquire(eq("workflow-timeout-scan"), anyString(), eq(Duration.ofSeconds(45))))
                .thenReturn(Mono.just(true));
        when(lease.release(eq("workflow-timeout-scan"), anyString())).thenReturn(Mono.empty());

        new ApplicationContextRunner()
                .withBean(TimeoutTaskScan.class, () -> repository)
                .withBean(WorkflowService.class, () -> service)
                .withBean(ScanLease.class, () -> lease)
                .withUserConfiguration(WorkflowTimeoutHandler.class)
                .withPropertyValues(
                        "apex.job.executor.enabled=true",
                        "apex.job.workflow.enabled=true",
                        "apex.workflow.timeout-scan.lease-seconds=45")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(WorkflowTimeoutHandler.class);
                    WorkflowTimeoutHandler managed = context.getBean(WorkflowTimeoutHandler.class);
                    managed.handleTimeoutTasks();
                    verifyNoInteractions(repository, lease);
                    StepVerifier.create(managed.scanTimeoutTasks()).verifyComplete();
                    verify(lease).acquire(eq("workflow-timeout-scan"), anyString(), eq(Duration.ofSeconds(45)));
                    verify(lease).release(eq("workflow-timeout-scan"), anyString());
                });
    }

    @Test
    void waitsForAllTasksAndReportsFailureAfterOtherTasks() {
        Task first = task(1L, "ESCALATE");
        Task second = task(2L, "AUTO_COMPLETE");
        Sinks.Empty<Void> completion = Sinks.empty();
        CountDownLatch started = new CountDownLatch(1);
        when(repository.findPendingTasks()).thenReturn(Flux.just(first, second));
        when(service.handleTimeoutTask(
                        argThat(task -> task != null && task.getId().equals(1L))))
                .thenReturn(Mono.error(new IllegalStateException("单项失败")));
        when(service.handleTimeoutTask(
                        argThat(task -> task != null && task.getId().equals(2L))))
                .thenReturn(completion.asMono().doOnSubscribe(ignored -> started.countDown()));

        StepVerifier.create(handler.scanTimeoutTasks())
                .then(() -> await(started))
                .expectNoEvent(Duration.ofMillis(50))
                .then(() -> completion.tryEmitEmpty())
                .expectErrorMessage("单项失败")
                .verify(Duration.ofSeconds(5));
        verify(service).handleTimeoutTask(second);
    }

    @Test
    void rejectsConcurrentScanAndReleasesAfterCancellation() {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch cancelled = new CountDownLatch(1);
        when(repository.findPendingTasks())
                .thenReturn(Flux.<Task>never()
                        .doOnSubscribe(ignored -> started.countDown())
                        .doOnCancel(cancelled::countDown));
        Disposable first = handler.scanTimeoutTasks().subscribe();
        await(started);
        StepVerifier.create(handler.scanTimeoutTasks())
                .expectErrorMessage("超时扫描正在执行")
                .verify();
        first.dispose();
        await(cancelled);
        when(repository.findPendingTasks()).thenReturn(Flux.empty());
        StepVerifier.create(handler.scanTimeoutTasks()).verifyComplete();
    }

    @Test
    void localScanOnlyDisabledWhenBothSwitchesEnabled() {
        for (boolean executor : new boolean[] {false, true}) {
            for (boolean workflow : new boolean[] {false, true}) {
                TimeoutTaskScan local = mock(TimeoutTaskScan.class);
                CountDownLatch scanned = new CountDownLatch(1);
                if (!(executor && workflow)) {
                    when(local.findPendingTasks())
                            .thenReturn(Flux.<Task>empty().doOnSubscribe(ignored -> scanned.countDown()));
                }
                new WorkflowTimeoutHandler(local, service, null, executor, workflow, 300).handleTimeoutTasks();
                if (executor && workflow) {
                    verifyNoInteractions(local);
                } else {
                    await(scanned);
                    verify(local).findPendingTasks();
                }
            }
        }
    }

    @Test
    void filtersFutureAndMissingDeadlineAndPreservesNotifyBehavior() {
        Task future = task(1L, "ESCALATE");
        future.setTimeoutAt(LocalDateTime.now().plusHours(1));
        Task missing = task(2L, "AUTO_COMPLETE");
        missing.setTimeoutAt(null);
        when(repository.findPendingTasks()).thenReturn(Flux.just(future, missing, task(3L, "NOTIFY")));
        StepVerifier.create(handler.scanTimeoutTasks()).verifyComplete();
        verifyNoInteractions(service);
    }

    @Test
    void defaultStrategyEscalatesAndUnknownStrategyFails() {
        when(repository.findPendingTasks()).thenReturn(Flux.just(task(1L, null), task(2L, "UNKNOWN")));
        when(service.handleTimeoutTask(any(Task.class))).thenReturn(Mono.empty());
        StepVerifier.create(handler.scanTimeoutTasks())
                .expectError(IllegalArgumentException.class)
                .verify();
        verify(service)
                .handleTimeoutTask(argThat(task -> task != null && task.getId().equals(1L)));
    }

    @Test
    void executesEachTaskInItsPersistedTenantContext() {
        Task first = task(1L, "AUTO_COMPLETE");
        first.setTenantId("tenant-A");
        Task second = task(2L, "AUTO_COMPLETE");
        second.setTenantId("tenant-B");
        when(repository.findPendingTasks()).thenReturn(Flux.just(first, second));
        when(service.handleTimeoutTask(any(Task.class)))
                .thenAnswer(invocation -> Mono.deferContextual(context -> {
                    assertThat(context.<String>get(com.apex.scaffold.common.context.TenantContext.REACTOR_CONTEXT_KEY))
                            .isEqualTo(invocation.<Task>getArgument(0).getId() == 1L ? "tenant-A" : "tenant-B");
                    return Mono.empty();
                }));
        StepVerifier.create(handler.scanTimeoutTasks()).verifyComplete();
    }

    @Test
    void rejectsTaskWithoutPersistedTenantBeforeMutation() {
        Task invalid = task(1L, "AUTO_COMPLETE");
        invalid.setTenantId(" ");
        when(repository.findPendingTasks()).thenReturn(Flux.just(invalid));
        StepVerifier.create(handler.scanTimeoutTasks())
                .expectError(IllegalStateException.class)
                .verify();
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"success", "error", "cancel"})
    void leaseCleanupFinishesBeforeReentry(String outcome) {
        ScanLease lease = mock(ScanLease.class);
        Sinks.Empty<Void> scanFinished = Sinks.empty();
        Sinks.Empty<Void> releaseFinished = Sinks.empty();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch releasing = new CountDownLatch(1);
        CountDownLatch terminated = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        when(lease.acquire(anyString(), anyString(), any())).thenReturn(Mono.just(true));
        when(lease.release(anyString(), anyString()))
                .thenReturn(releaseFinished.asMono().doOnSubscribe(ignored -> releasing.countDown()));
        when(repository.findPendingTasks())
                .thenReturn(scanFinished
                        .asMono()
                        .thenMany(Flux.<Task>empty())
                        .doOnSubscribe(ignored -> started.countDown()));
        WorkflowTimeoutHandler leased = new WorkflowTimeoutHandler(repository, service, lease, false, false, 300);
        Disposable subscription = leased.scanTimeoutTasks()
                .subscribe(
                        ignored -> {},
                        error -> {
                            failure.set(error);
                            terminated.countDown();
                        },
                        terminated::countDown);
        await(started);
        switch (outcome) {
            case "success" -> scanFinished.tryEmitEmpty();
            case "error" -> scanFinished.tryEmitError(new IllegalStateException("扫描失败"));
            case "cancel" -> subscription.dispose();
            default -> throw new AssertionError(outcome);
        }
        await(releasing);
        StepVerifier.create(leased.scanTimeoutTasks())
                .expectErrorMessage("超时扫描正在执行")
                .verify(Duration.ofSeconds(5));
        verify(lease, times(1)).acquire(anyString(), anyString(), any());
        assertThat(terminated.getCount()).isEqualTo(1);
        assertThat(releaseFinished.tryEmitEmpty()).isEqualTo(Sinks.EmitResult.OK);
        if (!"cancel".equals(outcome)) {
            await(terminated);
            if ("error".equals(outcome)) {
                assertThat(failure.get()).hasMessage("扫描失败");
            } else {
                assertThat(failure.get()).isNull();
            }
        }
        when(repository.findPendingTasks()).thenReturn(Flux.empty());
        StepVerifier.create(leased.scanTimeoutTasks()).verifyComplete();
        ArgumentCaptor<String> owners = ArgumentCaptor.forClass(String.class);
        verify(lease, times(2)).acquire(anyString(), owners.capture(), any());
        assertThat(owners.getAllValues().get(0))
                .isNotEqualTo(owners.getAllValues().get(1));
        verify(lease).release("workflow-timeout-scan", owners.getAllValues().get(0));
        verify(lease).release("workflow-timeout-scan", owners.getAllValues().get(1));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void cancellationWaitsForLateAcquisitionBeforeCleanup(boolean acquired) {
        ScanLease lease = mock(ScanLease.class);
        Sinks.One<Boolean> acquisition = Sinks.one();
        AtomicBoolean acquisitionCancelled = new AtomicBoolean();
        when(lease.acquire(anyString(), anyString(), any()))
                .thenReturn(acquisition.asMono().doOnCancel(() -> acquisitionCancelled.set(true)));
        when(lease.release(anyString(), anyString())).thenReturn(Mono.empty());
        WorkflowTimeoutHandler leased = new WorkflowTimeoutHandler(repository, service, lease, false, false, 300);

        Disposable subscription = leased.scanTimeoutTasks().subscribe();
        subscription.dispose();
        assertThat(acquisitionCancelled).isFalse();
        StepVerifier.create(leased.scanTimeoutTasks())
                .expectErrorMessage("超时扫描正在执行")
                .verify(Duration.ofSeconds(5));
        assertThat(acquisition.tryEmitValue(acquired)).isEqualTo(Sinks.EmitResult.OK);
        verify(lease, times(acquired ? 1 : 0)).release(anyString(), anyString());
        verifyNoInteractions(repository);

        when(lease.acquire(anyString(), anyString(), any())).thenReturn(Mono.just(false));
        StepVerifier.create(leased.scanTimeoutTasks()).verifyComplete();
    }

    @Test
    void cancelledAcquisitionFailureUnlocksWithoutStartingScan() {
        ScanLease lease = mock(ScanLease.class);
        Sinks.One<Boolean> acquisition = Sinks.one();
        when(lease.acquire(anyString(), anyString(), any())).thenReturn(acquisition.asMono());
        WorkflowTimeoutHandler leased = new WorkflowTimeoutHandler(repository, service, lease, false, false, 300);
        Disposable subscription = leased.scanTimeoutTasks().subscribe();
        subscription.dispose();
        assertThat(acquisition.tryEmitError(new IllegalStateException("抢占失败"))).isEqualTo(Sinks.EmitResult.OK);
        when(lease.acquire(anyString(), anyString(), any())).thenReturn(Mono.just(false));
        StepVerifier.create(leased.scanTimeoutTasks()).verifyComplete();
        verify(lease, never()).release(anyString(), anyString());
        verifyNoInteractions(repository);
    }

    @Test
    void acquisitionFailureAllowsNextScanWithoutReleasingUnownedLease() {
        ScanLease lease = mock(ScanLease.class);
        when(lease.acquire(anyString(), anyString(), any()))
                .thenReturn(Mono.error(new IllegalStateException("抢占失败")), Mono.just(false));
        WorkflowTimeoutHandler leased = new WorkflowTimeoutHandler(repository, service, lease, false, false, 300);
        StepVerifier.create(leased.scanTimeoutTasks())
                .expectErrorMessage("抢占失败")
                .verify();
        StepVerifier.create(leased.scanTimeoutTasks()).verifyComplete();
        verify(lease, never()).release(anyString(), anyString());
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void releaseFailurePreservesScanOutcomeAndAllowsReentry(boolean scanFails) {
        ScanLease lease = mock(ScanLease.class);
        when(lease.acquire(anyString(), anyString(), any())).thenReturn(Mono.just(true));
        when(lease.release(anyString(), anyString())).thenReturn(Mono.error(new IllegalStateException("释放失败")));
        when(repository.findPendingTasks())
                .thenReturn(scanFails ? Flux.error(new IllegalStateException("扫描失败")) : Flux.empty());
        WorkflowTimeoutHandler leased = new WorkflowTimeoutHandler(repository, service, lease, false, false, 300);
        StepVerifier verifier = scanFails
                ? StepVerifier.create(leased.scanTimeoutTasks()).expectErrorMessage("扫描失败")
                : StepVerifier.create(leased.scanTimeoutTasks()).expectComplete();
        verifier.verify(Duration.ofSeconds(5));
        when(lease.acquire(anyString(), anyString(), any())).thenReturn(Mono.just(false));
        StepVerifier.create(leased.scanTimeoutTasks()).verifyComplete();
        verify(lease, times(1)).release(anyString(), anyString());
    }

    private Task task(Long id, String strategy) {
        Task task = new Task();
        task.setId(id);
        task.setTenantId("tenant-default");
        task.setAssigneeId(10L);
        task.setTimeoutAt(LocalDateTime.now().minusHours(1));
        task.setTimeoutStrategy(strategy);
        return task;
    }

    private void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        }
    }
}
