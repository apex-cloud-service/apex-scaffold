package com.apex.scaffold.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.domain.model.monitor.HealthExportTaskEntity;
import com.apex.scaffold.domain.model.monitor.HealthSnapshotEntity;
import com.apex.scaffold.domain.repository.HealthExportTaskRepository;
import com.apex.scaffold.domain.repository.HealthSnapshotRepository;
import com.apex.scaffold.domain.service.HealthExportSettingsPort;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

/** 验证异步导出的提交校验、任务生成与结果清理。 */
class HealthExportServiceTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_ROWS = 1000;

    private final HealthExportTaskRepository taskRepository = mock(HealthExportTaskRepository.class);
    private final HealthSnapshotRepository snapshotRepository = mock(HealthSnapshotRepository.class);
    private final HealthExportSettingsPort settings = mock(HealthExportSettingsPort.class);
    private final HealthExportService service =
            new HealthExportService(taskRepository, snapshotRepository, settings, () -> "test-secret");

    HealthExportServiceTest() {
        when(settings.maxRows()).thenReturn(MAX_ROWS);
        when(settings.maxAttempts()).thenReturn(3);
        when(settings.resultTtlHours()).thenReturn(24);
        when(taskRepository.insert(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
    }

    @Test
    void submitRejectsMissingTenantContext() {
        StepVerifier.create(service.submit(null, null, null))
                .expectErrorSatisfies(error ->
                        assertThat(error).isInstanceOf(BusinessException.class).hasMessageContaining("租户上下文不能为空"))
                .verify(TIMEOUT);

        verifyNoInteractions(snapshotRepository);
    }

    @Test
    void submitRejectsIllegalStatusBeforeCounting() {
        StepVerifier.create(service.submit(null, null, "BROKEN").contextWrite(tenantContext()))
                .expectErrorSatisfies(error ->
                        assertThat(error).isInstanceOf(BusinessException.class).hasMessageContaining("总体结论取值非法"))
                .verify(TIMEOUT);

        verifyNoInteractions(snapshotRepository);
    }

    @Test
    void submitRejectsReversedRange() {
        LocalDateTime now = LocalDateTime.now();

        StepVerifier.create(service.submit(now, now.minusDays(1), null).contextWrite(tenantContext()))
                .expectErrorSatisfies(error ->
                        assertThat(error).isInstanceOf(BusinessException.class).hasMessageContaining("起始时间不能晚于结束时间"))
                .verify(TIMEOUT);

        verifyNoInteractions(snapshotRepository);
    }

    @Test
    void submitRejectsOversizedRangeWithoutCreatingTask() {
        when(snapshotRepository.countByRange(any(), any())).thenReturn(Mono.just((long) MAX_ROWS + 1));

        StepVerifier.create(service.submit(null, null, null).contextWrite(tenantContext()))
                .expectErrorSatisfies(error ->
                        assertThat(error).isInstanceOf(BusinessException.class).hasMessageContaining("导出记录数超过上限"))
                .verify(TIMEOUT);

        verify(taskRepository, never()).insert(any());
    }

    @Test
    void submitPersistsPendingTaskWithNormalizedStatusAndTenant() {
        when(snapshotRepository.countByRange(any(), any())).thenReturn(Mono.just(10L));
        LocalDateTime from = LocalDateTime.of(2026, 10, 1, 0, 0);
        LocalDateTime to = from.plusDays(1);

        StepVerifier.create(service.submit(from, to, " degraded ").contextWrite(tenantContext()))
                .assertNext(dto -> {
                    assertThat(dto.getStatus()).isEqualTo(HealthExportTaskEntity.STATUS_PENDING);
                    assertThat(dto.getQueryStatus()).isEqualTo("DEGRADED");
                    assertThat(dto.getQueryFrom()).isEqualTo(from);
                    assertThat(dto.getAttempts()).isZero();
                })
                .verifyComplete();

        ArgumentCaptor<HealthExportTaskEntity> captor = ArgumentCaptor.forClass(HealthExportTaskEntity.class);
        verify(taskRepository).insert(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo("t-1");
    }

    @Test
    void generateBuildsCsvFromRepositoryPage() {
        when(snapshotRepository.countByRange(any(), any())).thenReturn(Mono.just(1L));
        when(snapshotRepository.search(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(Mono.just(PageResponse.of(List.of(snapshot()), 1L, 1, MAX_ROWS)));

        StepVerifier.create(service.generate(task()))
                .assertNext(file -> {
                    assertThat(file.rowCount()).isEqualTo(1);
                    String csv = new String(file.content(), StandardCharsets.UTF_8);
                    assertThat(csv).startsWith("generatedAt,overallStatus,captureSource");
                    assertThat(csv).contains("DEGRADED");
                })
                .verifyComplete();
    }

    @Test
    void findDownloadRejectsTaskWithoutContent() {
        when(taskRepository.findById(eq(7L))).thenReturn(Mono.just(task()));

        StepVerifier.create(service.findDownload(7L))
                .expectErrorSatisfies(error ->
                        assertThat(error).isInstanceOf(BusinessException.class).hasMessageContaining("尚未完成"))
                .verify(TIMEOUT);
    }

    @Test
    void findDownloadReturnsStoredFile() {
        HealthExportTaskEntity completed = task();
        completed.setFileName("health-snapshots-x.csv");
        completed.setContent("a,b".getBytes(StandardCharsets.UTF_8));
        when(taskRepository.findById(eq(7L))).thenReturn(Mono.just(completed));

        StepVerifier.create(service.findDownload(7L))
                .assertNext(result -> {
                    assertThat(result.fileName()).isEqualTo("health-snapshots-x.csv");
                    assertThat(result.contentType()).isEqualTo(OperationsHealthService.CSV_CONTENT_TYPE);
                    assertThat(new String(result.content(), StandardCharsets.UTF_8))
                            .isEqualTo("a,b");
                })
                .verifyComplete();
    }

    @Test
    void purgeExpiredLoopsUntilBatchNotFull() {
        when(taskRepository.purgeCompletedBefore(eq("t-1"), any(), eq(100)))
                .thenReturn(Mono.just(100), Mono.just(100), Mono.just(3));

        StepVerifier.create(service.purgeExpired("t-1", 100))
                .assertNext(total -> assertThat(total).isEqualTo(203))
                .verifyComplete();

        verify(taskRepository, org.mockito.Mockito.times(3)).purgeCompletedBefore(eq("t-1"), any(), anyInt());
    }

    private static Context tenantContext() {
        return Context.of(TenantContext.REACTOR_CONTEXT_KEY, "t-1");
    }

    private static HealthExportTaskEntity task() {
        HealthExportTaskEntity task = new HealthExportTaskEntity();
        task.setId(7L);
        task.setStatus(HealthExportTaskEntity.STATUS_RUNNING);
        task.setAttempts(1);
        task.setQueryStatus("DEGRADED");
        task.setTenantId("t-1");
        return task;
    }

    private static HealthSnapshotEntity snapshot() {
        HealthSnapshotEntity entity = new HealthSnapshotEntity();
        entity.setId(1L);
        entity.setCaptureSource(HealthSnapshotEntity.CAPTURE_SOURCE_MANUAL);
        entity.setServiceName("apex-system-service");
        entity.setInstanceId("1234@host");
        entity.setScope(HealthSnapshotEntity.SCOPE_INSTANCE);
        entity.setRuleVersion("health-report-v1");
        entity.setOverallStatus("DEGRADED");
        entity.setSummary("summary");
        entity.setUpCount(1);
        entity.setDegradedCount(1);
        entity.setDownCount(0);
        entity.setUnknownCount(0);
        entity.setNotConfiguredCount(0);
        entity.setGeneratedAt(LocalDateTime.of(2026, 10, 1, 8, 0));
        return entity;
    }
}
