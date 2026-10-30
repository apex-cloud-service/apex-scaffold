package com.apex.scaffold.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.application.dto.admin.GenGenerateRequest;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.domain.model.generator.GenTaskEntity;
import com.apex.scaffold.domain.repository.GenTaskRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 验证异步生成任务的提交、进度查询、取消与终态收敛。
 *
 * <p>覆盖「进度回写失败即停止」与「未达最大尝试次数保留 RUNNING 重试」两条关键分支。
 */
class GeneratorTaskServiceTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private static final String TENANT = "t-1";

    private static final Long TASK_ID = 99L;

    private static final Long OPERATOR = 42L;

    private static final int MAX_ATTEMPTS = 3;

    private final GeneratorWizardService wizardService = mock(GeneratorWizardService.class);

    private final GenTaskRepository genTaskRepository = mock(GenTaskRepository.class);

    private final GeneratorTaskService service =
            new GeneratorTaskService(wizardService, genTaskRepository, MAX_ATTEMPTS);

    @Test
    void submitPersistsPreparedTaskAndReturnsTaskId() {
        GenGenerateRequest request = GenGenerateRequest.builder().configId(5L).build();
        GenTaskEntity prepared = task();
        when(wizardService.prepareTask(request, OPERATOR)).thenReturn(Mono.just(prepared));
        when(genTaskRepository.insert(prepared)).thenReturn(Mono.just(prepared));

        StepVerifier.create(service.submit(request, OPERATOR))
                .assertNext(response -> {
                    assertThat(response.getTaskId()).isEqualTo(TASK_ID);
                    assertThat(response.getStatus()).isEqualTo("PENDING");
                })
                .verifyComplete();

        verify(genTaskRepository).insert(prepared);
    }

    @Test
    void getProgressMapsTaskFields() {
        GenTaskEntity running = task();
        running.setStatus("RUNNING");
        running.setTotalCount(10);
        running.setProcessedCount(4);
        running.setFailedCount(1);
        when(wizardService.tenantId()).thenReturn(Mono.just(TENANT));
        when(genTaskRepository.findById(TASK_ID, TENANT)).thenReturn(Mono.just(running));

        StepVerifier.create(service.getProgress(TASK_ID))
                .assertNext(dto -> {
                    assertThat(dto.getId()).isEqualTo(TASK_ID);
                    assertThat(dto.getStatus()).isEqualTo("RUNNING");
                    assertThat(dto.getTotalCount()).isEqualTo(10);
                    assertThat(dto.getProcessedCount()).isEqualTo(4);
                    assertThat(dto.getFailedCount()).isEqualTo(1);
                    assertThat(dto.getProgressPercent()).isEqualTo(40);
                })
                .verifyComplete();
    }

    @Test
    void getProgressRejectsUnknownTask() {
        when(wizardService.tenantId()).thenReturn(Mono.just(TENANT));
        when(genTaskRepository.findById(TASK_ID, TENANT)).thenReturn(Mono.empty());

        StepVerifier.create(service.getProgress(TASK_ID))
                .expectErrorSatisfies(error ->
                        assertThat(error).isInstanceOf(BusinessException.class).hasMessageContaining("生成任务不存在"))
                .verify(TIMEOUT);
    }

    @Test
    void cancelSucceedsWhenTaskCancelable() {
        when(wizardService.tenantId()).thenReturn(Mono.just(TENANT));
        when(genTaskRepository.requestCancel(eq(TASK_ID), eq(TENANT), any(LocalDateTime.class)))
                .thenReturn(Mono.just(true));

        StepVerifier.create(service.cancel(TASK_ID)).expectNext(true).verifyComplete();
    }

    @Test
    void cancelRejectsFinishedTask() {
        when(wizardService.tenantId()).thenReturn(Mono.just(TENANT));
        when(genTaskRepository.requestCancel(eq(TASK_ID), eq(TENANT), any(LocalDateTime.class)))
                .thenReturn(Mono.just(false));

        StepVerifier.create(service.cancel(TASK_ID))
                .expectErrorSatisfies(error ->
                        assertThat(error).isInstanceOf(BusinessException.class).hasMessageContaining("任务不存在或已结束"))
                .verify(TIMEOUT);
    }

    @Test
    void processTaskReportsProgressAndMarksCompleted() {
        GenTaskEntity running = task();
        running.setStatus("RUNNING");
        when(genTaskRepository.updateProgress(TASK_ID, TENANT, 4, 2, 1, "boom")).thenReturn(Mono.just(true));
        when(genTaskRepository.markCompleted(
                        eq(TASK_ID), eq(TENANT), eq(55L), eq(4), eq(2), eq(1), any(LocalDateTime.class)))
                .thenReturn(Mono.just(true));
        when(wizardService.runTask(eq(running), eq(OPERATOR), eq(TENANT), any()))
                .thenAnswer(invocation -> {
                    GeneratorWizardService.TaskProgressListener listener = invocation.getArgument(3);
                    return listener.onProgress(2, 1, "boom")
                            .thenReturn(new GeneratorWizardService.TaskOutcome(55L, 2, 1, false));
                });

        StepVerifier.create(service.processTask(running, TENANT))
                .assertNext(outcome -> {
                    assertThat(outcome.historyId()).isEqualTo(55L);
                    assertThat(outcome.processedCount()).isEqualTo(2);
                    assertThat(outcome.stopped()).isFalse();
                })
                .verifyComplete();

        verify(genTaskRepository).updateProgress(TASK_ID, TENANT, 4, 2, 1, "boom");
        verify(genTaskRepository)
                .markCompleted(eq(TASK_ID), eq(TENANT), eq(55L), eq(4), eq(2), eq(1), any(LocalDateTime.class));
    }

    @Test
    void processTaskSkipsTerminalWriteWhenStopped() {
        GenTaskEntity running = task();
        running.setStatus("RUNNING");
        when(genTaskRepository.updateProgress(TASK_ID, TENANT, 4, 1, 0, null)).thenReturn(Mono.just(false));
        when(wizardService.runTask(eq(running), eq(OPERATOR), eq(TENANT), any()))
                .thenAnswer(invocation -> {
                    GeneratorWizardService.TaskProgressListener listener = invocation.getArgument(3);
                    return listener.onProgress(1, 0, null)
                            .thenReturn(new GeneratorWizardService.TaskOutcome(null, 1, 0, true));
                });

        StepVerifier.create(service.processTask(running, TENANT))
                .assertNext(outcome -> assertThat(outcome.stopped()).isTrue())
                .verifyComplete();

        verify(genTaskRepository, never()).markCompleted(any(), any(), any(), anyInt(), anyInt(), anyInt(), any());
        verify(genTaskRepository, never()).markFailed(any(), any(), any(), any());
    }

    @Test
    void processTaskMarksFailedWhenNoArtifactProduced() {
        GenTaskEntity running = task();
        when(genTaskRepository.markFailed(eq(TASK_ID), eq(TENANT), eq("生成未产生任何产物"), any(LocalDateTime.class)))
                .thenReturn(Mono.just(true));
        when(wizardService.runTask(eq(running), eq(OPERATOR), eq(TENANT), any()))
                .thenReturn(Mono.just(new GeneratorWizardService.TaskOutcome(null, 0, 0, false)));

        StepVerifier.create(service.processTask(running, TENANT))
                .assertNext(outcome -> assertThat(outcome.historyId()).isNull())
                .verifyComplete();

        verify(genTaskRepository).markFailed(eq(TASK_ID), eq(TENANT), eq("生成未产生任何产物"), any(LocalDateTime.class));
    }

    @Test
    void processTaskMarksFailedWhenAttemptsExhausted() {
        GenTaskEntity running = task();
        running.setAttempts(MAX_ATTEMPTS);
        when(wizardService.runTask(eq(running), eq(OPERATOR), eq(TENANT), any()))
                .thenReturn(Mono.error(new IllegalStateException("disk full")));
        when(genTaskRepository.markFailed(eq(TASK_ID), eq(TENANT), eq("disk full"), any(LocalDateTime.class)))
                .thenReturn(Mono.just(true));

        StepVerifier.create(service.processTask(running, TENANT)).verifyComplete();

        verify(genTaskRepository).markFailed(eq(TASK_ID), eq(TENANT), eq("disk full"), any(LocalDateTime.class));
    }

    @Test
    void processTaskKeepsRunningWhenAttemptsRemain() {
        GenTaskEntity running = task();
        running.setAttempts(1);
        when(wizardService.runTask(eq(running), eq(OPERATOR), eq(TENANT), any()))
                .thenReturn(Mono.error(new IllegalStateException("disk full")));

        StepVerifier.create(service.processTask(running, TENANT)).verifyComplete();

        verify(genTaskRepository, never()).markFailed(any(), any(), any(), any());
    }

    private static GenTaskEntity task() {
        GenTaskEntity task = new GenTaskEntity();
        task.setId(TASK_ID);
        task.setConfigId(5L);
        task.setStatus("PENDING");
        task.setTotalCount(4);
        task.setProcessedCount(0);
        task.setFailedCount(0);
        task.setOperatorId(OPERATOR);
        task.setAttempts(0);
        task.setTenantId(TENANT);
        return task;
    }
}
