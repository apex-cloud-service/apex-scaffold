package com.apex.scaffold.infrastructure.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.application.service.HealthExportService;
import com.apex.scaffold.application.service.OperationsHealthService;
import com.apex.scaffold.infrastructure.config.OperationsHealthProperties;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

/** 验证体检快照定时留存与保留期清理的租户传播与异常隔离。 */
class HealthSnapshotSchedulerTest {

    private final OperationsHealthService operationsHealthService = mock(OperationsHealthService.class);
    private final HealthExportService exportService = mock(HealthExportService.class);
    private final OperationsHealthProperties properties = new OperationsHealthProperties();

    @BeforeEach
    void setUp() {
        properties.getAsyncExport().setTenantId("platform");
        properties.getRetention().setDays(30);
        properties.getRetention().setBatchSize(100);
        properties.getRetention().setMaxBatches(2);
    }

    @Test
    void retentionPurgesSnapshotsThenExpiredExports() {
        when(operationsHealthService.purge(any(), anyInt(), anyInt())).thenReturn(Mono.just(5));
        when(exportService.purgeExpired(eq("platform"), anyInt())).thenReturn(Mono.just(2));

        new HealthSnapshotRetentionScheduler(exportService, operationsHealthService, properties).purge();

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(operationsHealthService, timeout(2000)).purge(cutoff.capture(), eq(100), eq(2));
        verify(exportService, timeout(2000)).purgeExpired("platform", 200);
        assertThat(cutoff.getValue()).isBefore(LocalDateTime.now().minusDays(29));
    }

    @Test
    void retentionSkipsWhenPlatformTenantMissing() {
        properties.getAsyncExport().setTenantId(" ");

        new HealthSnapshotRetentionScheduler(exportService, operationsHealthService, properties).purge();

        verify(operationsHealthService, after(200).never()).purge(any(), anyInt(), anyInt());
    }

    @Test
    void retentionSwallowsFailureAndStopsBeforeExportCleanup() {
        when(operationsHealthService.purge(any(), anyInt(), anyInt()))
                .thenReturn(Mono.error(new IllegalStateException("db down")));

        new HealthSnapshotRetentionScheduler(exportService, operationsHealthService, properties).purge();

        verify(operationsHealthService, timeout(2000)).purge(any(), anyInt(), anyInt());
        verify(exportService, after(300).never()).purgeExpired(any(), anyInt());
    }
}
