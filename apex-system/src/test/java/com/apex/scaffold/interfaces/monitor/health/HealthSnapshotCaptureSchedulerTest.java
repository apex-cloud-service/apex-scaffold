package com.apex.scaffold.interfaces.monitor.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.application.dto.admin.HealthSnapshotCommand;
import com.apex.scaffold.application.dto.admin.HealthSnapshotDTO;
import com.apex.scaffold.application.service.OperationsHealthService;
import com.apex.scaffold.domain.model.monitor.HealthSnapshotEntity;
import com.apex.scaffold.infrastructure.config.OperationsHealthProperties;
import com.apex.scaffold.interfaces.dto.monitor.HealthReportResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

/** 验证体检快照定时留存的任务转换与租户传播。 */
class HealthSnapshotCaptureSchedulerTest {

    private final HealthReportAssembler assembler = mock(HealthReportAssembler.class);
    private final OperationsHealthService operationsHealthService = mock(OperationsHealthService.class);
    private final OperationsHealthProperties properties = new OperationsHealthProperties();

    @Test
    void captureMapsReportIntoConclusionOnlyCommand() {
        properties.getSchedule().setTenantId("platform");
        when(assembler.report()).thenReturn(report());
        when(operationsHealthService.capture(any())).thenReturn(Mono.just(new HealthSnapshotDTO()));

        new HealthSnapshotCaptureScheduler(assembler, operationsHealthService, properties).capture();

        ArgumentCaptor<HealthSnapshotCommand> captor = ArgumentCaptor.forClass(HealthSnapshotCommand.class);
        verify(operationsHealthService, timeout(2000)).capture(captor.capture());
        HealthSnapshotCommand command = captor.getValue();
        assertThat(command.getCaptureSource()).isEqualTo(HealthSnapshotEntity.CAPTURE_SOURCE_SCHEDULED);
        assertThat(command.getOverallStatus()).isEqualTo("DEGRADED");
        assertThat(command.getUpCount()).isEqualTo(8);
        assertThat(command.getDownCount()).isEqualTo(1);
        assertThat(command.getGeneratedAt()).isNotNull();
    }

    @Test
    void captureSkipsWhenPlatformTenantMissing() {
        properties.getSchedule().setTenantId(" ");

        new HealthSnapshotCaptureScheduler(assembler, operationsHealthService, properties).capture();

        verify(assembler, after(200).never()).report();
        verify(operationsHealthService, after(200).never()).capture(any());
    }

    @Test
    void captureSwallowsAssemblyFailure() {
        properties.getSchedule().setTenantId("platform");
        when(assembler.report()).thenThrow(new IllegalStateException("io error"));

        new HealthSnapshotCaptureScheduler(assembler, operationsHealthService, properties).capture();

        verify(assembler, timeout(2000)).report();
        verify(operationsHealthService, after(300).never()).capture(any());
    }

    private static HealthReportResponse report() {
        return new HealthReportResponse(
                "2026-10-29T10:15:30",
                "apex-system-service",
                "1234@host",
                "INSTANCE",
                "单实例结果不代表全栈",
                "health-report-v1",
                "DEGRADED",
                "redis latency high",
                new HealthReportResponse.StatusCounts(8, 1, 1, 2, 2),
                List.of());
    }
}
