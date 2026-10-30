package com.apex.scaffold.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.apex.scaffold.application.dto.admin.HealthSnapshotCommand;
import com.apex.scaffold.application.dto.admin.HealthTrendDTO;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.domain.model.monitor.HealthSnapshotEntity;
import com.apex.scaffold.domain.repository.HealthSnapshotRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

/** 验证体检快照的租户写入、趋势口径、导出上限与 CSV 转义。 */
class OperationsHealthServiceTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final HealthSnapshotRepository repository = mock(HealthSnapshotRepository.class);
    private final OperationsHealthService service = new OperationsHealthService(repository, () -> "test-secret");

    @Test
    void captureRejectsMissingTenantContext() {
        HealthSnapshotCommand command = command();

        StepVerifier.create(service.capture(command))
                .expectErrorSatisfies(error ->
                        assertThat(error).isInstanceOf(BusinessException.class).hasMessageContaining("租户上下文不能为空"))
                .verify(TIMEOUT);

        verifyNoInteractions(repository);
    }

    @Test
    void captureWritesTenantFromContextAndDefaultsScopeAndSource() {
        when(repository.insert(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        HealthSnapshotCommand command = HealthSnapshotCommand.builder()
                .overallStatus("UP")
                .summary("ok")
                .build();

        StepVerifier.create(service.capture(command).contextWrite(Context.of(TenantContext.REACTOR_CONTEXT_KEY, "t-1")))
                .assertNext(dto -> {
                    assertThat(dto.getOverallStatus()).isEqualTo("UP");
                    assertThat(dto.getCaptureSource()).isEqualTo(HealthSnapshotEntity.CAPTURE_SOURCE_MANUAL);
                    assertThat(dto.getScope()).isEqualTo(HealthSnapshotEntity.SCOPE_INSTANCE);
                })
                .verifyComplete();

        verify(repository)
                .insert(org.mockito.ArgumentMatchers.argThat(
                        entity -> "t-1".equals(entity.getTenantId()) && entity.getGeneratedAt() != null));
    }

    @Test
    void searchRejectsIllegalStatusBeforeHittingRepository() {
        StepVerifier.create(service.search(null, null, "BROKEN", 1, 20))
                .expectErrorSatisfies(error ->
                        assertThat(error).isInstanceOf(BusinessException.class).hasMessageContaining("总体结论取值非法"))
                .verify(TIMEOUT);

        verifyNoInteractions(repository);
    }

    @Test
    void searchNormalizesStatusAndMapsPage() {
        PageResponse<HealthSnapshotEntity> page = PageResponse.of(List.of(snapshot("DEGRADED", 2)), 1L, 1, 20);
        when(repository.search(isNull(), isNull(), eq("DEGRADED"), eq(1), eq(20)))
                .thenReturn(Mono.just(page));

        StepVerifier.create(service.search(null, null, " degraded ", 1, 20))
                .assertNext(result -> {
                    assertThat(result.getTotal()).isEqualTo(1L);
                    assertThat(result.getList()).hasSize(1);
                    assertThat(result.getList().get(0).getOverallStatus()).isEqualTo("DEGRADED");
                })
                .verifyComplete();
    }

    @Test
    void trendRejectsReversedRange() {
        LocalDateTime now = LocalDateTime.now();

        StepVerifier.create(service.trend(now, now.minusDays(1), 10))
                .expectErrorSatisfies(error ->
                        assertThat(error).isInstanceOf(BusinessException.class).hasMessageContaining("起始时间不能晚于结束时间"))
                .verify(TIMEOUT);
    }

    @Test
    void trendReportsImprovementWhenSeverityDrops() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 1, 8, 0);
        LocalDateTime end = start.plusDays(1);
        when(repository.findForTrend(any(), any(), anyInt()))
                .thenReturn(Mono.just(List.of(snapshot("DOWN", 10), snapshot("UP", 10))));

        StepVerifier.create(service.trend(start, end, 100))
                .assertNext(trend -> {
                    assertThat(trend.getSampleCount()).isEqualTo(2);
                    assertThat(trend.getStartStatus()).isEqualTo("DOWN");
                    assertThat(trend.getEndStatus()).isEqualTo("UP");
                    assertThat(trend.getImproving()).isTrue();
                    assertThat(trend.getDistribution())
                            .extracting(HealthTrendDTO.StatusCount::getStatus)
                            .containsExactly("UP", "DOWN");
                })
                .verifyComplete();
    }

    @Test
    void trendLeavesImprovingUnknownForSingleSample() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 1, 8, 0);
        LocalDateTime end = start.plusDays(1);
        when(repository.findForTrend(any(), any(), anyInt())).thenReturn(Mono.just(List.of(snapshot("UP", 10))));

        StepVerifier.create(service.trend(start, end, 0))
                .assertNext(trend -> {
                    assertThat(trend.getSampleCount()).isEqualTo(1);
                    assertThat(trend.getImproving()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void exportRejectsWhenRowCountExceedsLimit() {
        when(repository.countByRange(any(), any())).thenReturn(Mono.just(5001L));

        StepVerifier.create(service.export(null, null, null))
                .expectErrorSatisfies(error ->
                        assertThat(error).isInstanceOf(BusinessException.class).hasMessageContaining("导出记录数超过上限"))
                .verify(TIMEOUT);

        verify(repository, never()).search(any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void exportBuildsCsvWithHeaderAndFormulaInjectionGuard() {
        HealthSnapshotEntity risky = snapshot("DEGRADED", 7);
        risky.setSummary("=HYPERLINK(\"http://evil\"),x");
        when(repository.countByRange(any(), any())).thenReturn(Mono.just(1L));
        when(repository.search(isNull(), isNull(), isNull(), eq(1), eq(OperationsHealthService.EXPORT_MAX_ROWS)))
                .thenReturn(Mono.just(PageResponse.of(List.of(risky), 1L, 1, OperationsHealthService.EXPORT_MAX_ROWS)));

        StepVerifier.create(service.export(null, null, null))
                .assertNext(file -> {
                    assertThat(file.fileName()).startsWith("health-snapshots-").endsWith(".csv");
                    assertThat(file.contentType()).isEqualTo(OperationsHealthService.CSV_CONTENT_TYPE);
                    String csv = new String(file.content(), StandardCharsets.UTF_8);
                    assertThat(csv).startsWith("generatedAt,overallStatus,captureSource");
                    assertThat(csv).contains("\"'=HYPERLINK(\"\"http://evil\"\"),x\"");
                })
                .verifyComplete();
    }

    @Test
    void tenantSummaryFallsBackToUnknownWithoutSnapshots() {
        when(repository.findForTrend(any(), any(), anyInt())).thenReturn(Mono.just(List.of()));

        StepVerifier.create(service.tenantSummary())
                .assertNext(summary -> {
                    assertThat(summary.getOverallStatus()).isEqualTo("UNKNOWN");
                    assertThat(summary.getSnapshotAt()).isNull();
                    assertThat(summary.getRecentTrend()).isEmpty();
                    assertThat(summary.getScope()).isEqualTo(OperationsHealthService.SCOPE_INSTANCE);
                })
                .verifyComplete();
    }

    @Test
    void tenantSummaryUsesLatestSnapshotAndHidesEvidence() {
        HealthSnapshotEntity intermediate = snapshot("DEGRADED", 30);
        HealthSnapshotEntity latest = snapshot("UP", 20);
        when(repository.findForTrend(any(), any(), anyInt())).thenReturn(Mono.just(List.of(intermediate, latest)));

        StepVerifier.create(service.tenantSummary())
                .assertNext(summary -> {
                    assertThat(summary.getOverallStatus()).isEqualTo("UP");
                    assertThat(summary.getSnapshotAt()).isEqualTo(latest.getGeneratedAt());
                    assertThat(summary.getRiskCounts().getHealthy()).isEqualTo(10);
                    assertThat(summary.getRecentTrend()).hasSize(2);
                })
                .verifyComplete();
    }

    private static HealthSnapshotCommand command() {
        return HealthSnapshotCommand.builder()
                .serviceName("apex-system-service")
                .overallStatus("UP")
                .build();
    }

    private static HealthSnapshotEntity snapshot(String status, int minutesAgo) {
        HealthSnapshotEntity entity = new HealthSnapshotEntity();
        entity.setId(1L);
        entity.setCaptureSource(HealthSnapshotEntity.CAPTURE_SOURCE_MANUAL);
        entity.setServiceName("apex-system-service");
        entity.setInstanceId("1234@host");
        entity.setScope(HealthSnapshotEntity.SCOPE_INSTANCE);
        entity.setRuleVersion("health-report-v1");
        entity.setOverallStatus(status);
        entity.setSummary("summary-" + status);
        entity.setUpCount(10);
        entity.setDegradedCount(0);
        entity.setDownCount(0);
        entity.setUnknownCount(0);
        entity.setNotConfiguredCount(0);
        entity.setGeneratedAt(LocalDateTime.of(2026, 10, 1, 8, 0).plusMinutes(minutesAgo));
        return entity;
    }
}
