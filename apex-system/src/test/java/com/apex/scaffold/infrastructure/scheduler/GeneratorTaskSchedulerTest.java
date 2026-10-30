package com.apex.scaffold.infrastructure.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.application.service.GeneratorTaskService;
import com.apex.scaffold.application.service.GeneratorWizardService;
import com.apex.scaffold.domain.model.generator.GenTaskEntity;
import com.apex.scaffold.domain.repository.GenTaskRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

/** 验证异步生成任务的认领式调度、租户异常隔离与保留期清理。 */
class GeneratorTaskSchedulerTest {

    private static final int MAX_ATTEMPTS = 3;

    private static final int LEASE_MINUTES = 10;

    private static final int TENANT_BATCH = 20;

    private static final int RETENTION_HOURS = 24;

    private final GeneratorTaskService taskService = mock(GeneratorTaskService.class);

    private final GenTaskRepository genTaskRepository = mock(GenTaskRepository.class);

    private GeneratorTaskScheduler scheduler() {
        return new GeneratorTaskScheduler(
                taskService, genTaskRepository, MAX_ATTEMPTS, LEASE_MINUTES, TENANT_BATCH, RETENTION_HOURS);
    }

    @Test
    void pollSkipsWhenNoCandidateTenants() {
        when(genTaskRepository.findCandidateTenants(eq(MAX_ATTEMPTS), any(LocalDateTime.class), eq(TENANT_BATCH)))
                .thenReturn(Mono.just(List.of()));

        scheduler().poll();

        verify(genTaskRepository, timeout(2000))
                .findCandidateTenants(eq(MAX_ATTEMPTS), any(LocalDateTime.class), eq(TENANT_BATCH));
        verify(genTaskRepository, after(200).never()).findNextCandidate(any(), anyInt(), any());
    }

    @Test
    void pollClaimsAndProcessesCandidate() {
        GenTaskEntity candidate = candidate(7L, "t-1");
        when(genTaskRepository.findCandidateTenants(eq(MAX_ATTEMPTS), any(LocalDateTime.class), eq(TENANT_BATCH)))
                .thenReturn(Mono.just(List.of("t-1")));
        when(genTaskRepository.findNextCandidate(eq("t-1"), eq(MAX_ATTEMPTS), any(LocalDateTime.class)))
                .thenReturn(Mono.just(candidate));
        when(genTaskRepository.claim(
                        eq(7L), eq("t-1"), anyString(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Mono.just(true));
        when(taskService.processTask(candidate, "t-1"))
                .thenReturn(Mono.just(new GeneratorWizardService.TaskOutcome(55L, 4, 0, false)));

        scheduler().poll();

        ArgumentCaptor<LocalDateTime> deadline = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(genTaskRepository, timeout(2000))
                .claim(eq(7L), eq("t-1"), anyString(), any(LocalDateTime.class), deadline.capture());
        verify(taskService, timeout(2000)).processTask(candidate, "t-1");
        assertThat(deadline.getValue()).isBefore(LocalDateTime.now().minusMinutes(LEASE_MINUTES - 1));
    }

    @Test
    void pollSkipsProcessingWhenClaimLoses() {
        when(genTaskRepository.findCandidateTenants(eq(MAX_ATTEMPTS), any(LocalDateTime.class), eq(TENANT_BATCH)))
                .thenReturn(Mono.just(List.of("t-1")));
        when(genTaskRepository.findNextCandidate(eq("t-1"), eq(MAX_ATTEMPTS), any(LocalDateTime.class)))
                .thenReturn(Mono.just(candidate(7L, "t-1")));
        when(genTaskRepository.claim(any(), any(), anyString(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Mono.just(false));

        scheduler().poll();

        verify(genTaskRepository, timeout(2000))
                .claim(eq(7L), eq("t-1"), anyString(), any(LocalDateTime.class), any(LocalDateTime.class));
        verify(taskService, after(300).never()).processTask(any(), any());
    }

    @Test
    void pollIsolatesFailingTenant() {
        GenTaskEntity candidate = candidate(8L, "b");
        when(genTaskRepository.findCandidateTenants(eq(MAX_ATTEMPTS), any(LocalDateTime.class), eq(TENANT_BATCH)))
                .thenReturn(Mono.just(List.of("a", "b")));
        when(genTaskRepository.findNextCandidate(eq("a"), eq(MAX_ATTEMPTS), any(LocalDateTime.class)))
                .thenReturn(Mono.error(new IllegalStateException("db down")));
        when(genTaskRepository.findNextCandidate(eq("b"), eq(MAX_ATTEMPTS), any(LocalDateTime.class)))
                .thenReturn(Mono.just(candidate));
        when(genTaskRepository.claim(eq(8L), eq("b"), anyString(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Mono.just(true));
        when(taskService.processTask(candidate, "b"))
                .thenReturn(Mono.just(new GeneratorWizardService.TaskOutcome(66L, 1, 0, false)));

        scheduler().poll();

        verify(taskService, timeout(2000)).processTask(candidate, "b");
    }

    @Test
    void purgeDeletesFinishedTasksByRetention() {
        when(genTaskRepository.findFinishedTenants(any(LocalDateTime.class), eq(TENANT_BATCH)))
                .thenReturn(Mono.just(List.of("t-1")));
        when(genTaskRepository.purgeFinishedBefore(eq("t-1"), any(LocalDateTime.class), eq(500)))
                .thenReturn(Mono.just(3));

        scheduler().purge();

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(genTaskRepository, timeout(2000)).purgeFinishedBefore(eq("t-1"), cutoff.capture(), eq(500));
        assertThat(cutoff.getValue()).isBefore(LocalDateTime.now().minusHours(RETENTION_HOURS - 1));
    }

    @Test
    void purgeSkipsWhenNoFinishedTenants() {
        when(genTaskRepository.findFinishedTenants(any(LocalDateTime.class), eq(TENANT_BATCH)))
                .thenReturn(Mono.just(List.of()));

        scheduler().purge();

        verify(genTaskRepository, timeout(2000)).findFinishedTenants(any(LocalDateTime.class), eq(TENANT_BATCH));
        verify(genTaskRepository, after(200).never()).purgeFinishedBefore(any(), any(), anyInt());
    }

    private static GenTaskEntity candidate(long id, String tenant) {
        GenTaskEntity entity = new GenTaskEntity();
        entity.setId(id);
        entity.setTenantId(tenant);
        entity.setStatus("PENDING");
        entity.setTotalCount(1);
        entity.setProcessedCount(0);
        entity.setFailedCount(0);
        entity.setAttempts(0);
        return entity;
    }
}
