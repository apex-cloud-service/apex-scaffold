package com.apex.scaffold.application.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.apex.scaffold.application.service.JobLogService;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.domain.model.entity.JobEntity;
import com.apex.scaffold.domain.model.entity.JobLogEntity;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

class JobSchedulerTenantTest {

    private final JobInvoker jobInvoker = mock(JobInvoker.class);
    private final JobLogService jobLogService = mock(JobLogService.class);
    private final JobScheduler scheduler = new JobScheduler(jobInvoker, jobLogService);

    @AfterEach
    void tearDown() {
        scheduler.shutdown();
        TenantContext.clear();
    }

    @Test
    void shouldExecuteWithJobTenantAndPersistLogWithSameTenant() {
        JobEntity job = job("tenant-a");
        AtomicReference<String> invokedTenant = new AtomicReference<>();
        AtomicReference<String> loggedTenant = new AtomicReference<>();
        doAnswer(invocation -> {
                    invokedTenant.set(TenantContext.currentTenantId().orElse(null));
                    return null;
                })
                .when(jobInvoker)
                .invoke(job);
        when(jobLogService.record(any(JobLogEntity.class))).thenAnswer(invocation -> {
            loggedTenant.set(invocation.getArgument(0, JobLogEntity.class).getTenantId());
            return Mono.empty();
        });

        scheduler.trigger(job).block();

        assertThat(invokedTenant).hasValue("tenant-a");
        assertThat(loggedTenant).hasValue("tenant-a");
        assertThat(TenantContext.currentTenantId()).isEmpty();
    }

    @Test
    void shouldRecordFailureWithJobTenantAndRestoreCallerTenant() {
        JobEntity job = job("tenant-a");
        AtomicReference<String> invokedTenant = new AtomicReference<>();
        AtomicReference<JobLogEntity> logged = new AtomicReference<>();
        doAnswer(invocation -> {
                    invokedTenant.set(TenantContext.currentTenantId().orElse(null));
                    throw new IllegalStateException("failed");
                })
                .when(jobInvoker)
                .invoke(job);
        when(jobLogService.record(any(JobLogEntity.class))).thenAnswer(invocation -> {
            logged.set(invocation.getArgument(0, JobLogEntity.class));
            return Mono.empty();
        });
        TenantContext.setTenantId("caller-tenant");

        scheduler.trigger(job).block();

        assertThat(invokedTenant).hasValue("tenant-a");
        assertThat(logged).isNotNull();
        assertThat(logged.get().getTenantId()).isEqualTo("tenant-a");
        assertThat(logged.get().isSuccess()).isFalse();
        assertThat(TenantContext.currentTenantId()).contains("caller-tenant");
    }

    @Test
    void shouldCancelPreviousScheduleWhenRegisteringSameJobAgain() {
        JobEntity job = job("tenant-a");
        scheduler.register(job).block();
        Disposable first = (Disposable)
                ((java.util.Map<?, ?>) ReflectionTestUtils.getField(scheduler, "schedules")).get(job.getId());

        scheduler.register(job).block();
        Disposable second = (Disposable)
                ((java.util.Map<?, ?>) ReflectionTestUtils.getField(scheduler, "schedules")).get(job.getId());

        assertThat(first.isDisposed()).isTrue();
        assertThat(second).isNotSameAs(first);
        assertThat(second.isDisposed()).isFalse();
    }

    @Test
    void shouldPreserveRunningGuardWhenRegisteringExecutingJobAgain() throws Exception {
        JobEntity job = job("tenant-a");
        job.setConcurrent(JobEntity.ConcurrentMode.FORBIDDEN.getCode());
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        AtomicInteger invocations = new AtomicInteger();
        doAnswer(invocation -> {
                    int count = invocations.incrementAndGet();
                    if (count == 1) {
                        started.countDown();
                        release.await(5, TimeUnit.SECONDS);
                        finished.countDown();
                    } else {
                        secondStarted.countDown();
                    }
                    return null;
                })
                .when(jobInvoker)
                .invoke(job);
        when(jobLogService.record(any(JobLogEntity.class))).thenReturn(Mono.empty());

        scheduler.trigger(job).subscribe();
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        scheduler.register(job).block();
        scheduler.trigger(job).block();

        assertThat(invocations).hasValue(1);
        release.countDown();
        assertThat(finished.await(5, TimeUnit.SECONDS)).isTrue();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (invocations.get() < 2 && System.nanoTime() < deadline) {
            scheduler.trigger(job).block();
        }
        assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(invocations).hasValue(2);
    }

    @Test
    void shouldNotLeakCancelledJobTenantIntoNextExecution() throws Exception {
        JobEntity first = job("tenant-a");
        JobEntity second = job("tenant-b");
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<String> secondTenant = new AtomicReference<>();
        doAnswer(invocation -> {
                    JobEntity job = invocation.getArgument(0, JobEntity.class);
                    if ("tenant-a".equals(job.getTenantId())) {
                        started.countDown();
                        try {
                            release.await(5, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    } else {
                        secondTenant.set(TenantContext.currentTenantId().orElse(null));
                    }
                    return null;
                })
                .when(jobInvoker)
                .invoke(any(JobEntity.class));
        when(jobLogService.record(any(JobLogEntity.class))).thenReturn(Mono.empty());

        reactor.core.Disposable subscription = scheduler.trigger(first).subscribe();
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        subscription.dispose();
        release.countDown();
        scheduler.trigger(second).block();

        assertThat(secondTenant).hasValue("tenant-b");
    }

    private JobEntity job(String tenantId) {
        JobEntity job = JobEntity.create("job", "SYSTEM", "target.run()", "0 0 2 * * ?", tenantId);
        job.setId(1L);
        return job;
    }
}
