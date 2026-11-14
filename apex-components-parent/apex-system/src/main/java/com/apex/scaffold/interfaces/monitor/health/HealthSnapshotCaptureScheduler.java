package com.apex.scaffold.interfaces.monitor.health;

import com.apex.scaffold.application.dto.admin.HealthSnapshotCommand;
import com.apex.scaffold.application.job.ManagedJob;
import com.apex.scaffold.application.service.OperationsHealthService;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.monitor.HealthSnapshotEntity;
import com.apex.scaffold.infrastructure.config.OperationsHealthProperties;
import com.apex.scaffold.interfaces.dto.monitor.HealthReportResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 体检快照定时留存调度器。
 *
 * <p>按 cron 周期执行与手工采集完全相同的实例内体检，并把结论级字段固化到历史快照，
 * 使趋势分析在没有人工触发时也能连续取样。</p>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>采集在通用阻塞池执行（含磁盘与文件描述符读取），不占用调度线程</li>
 *   <li>定时任务无请求上下文，租户取自 {@code apex.operations.health.schedule.tenant-id}</li>
 *   <li>快照来源标记为 {@code SCHEDULED}，与手工采集区分</li>
 *   <li>体检装配器与报告 DTO 属于接口适配层，本调度器随之位于 interfaces 层，
 *       避免 infrastructure 反向依赖 interfaces</li>
 * </ul>
 *
 * <p><b>范围约束</b>：采集结果只代表当前实例，多实例部署会各自落一条快照。</p>
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "apex.operations.health.schedule",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class HealthSnapshotCaptureScheduler {

    private final HealthReportAssembler assembler;

    private final OperationsHealthService operationsHealthService;

    private final OperationsHealthProperties properties;

    /**
     * 定时采集体检快照。
     */
    @Scheduled(cron = "${apex.operations.health.schedule.cron:0 0 * * * ?}")
    @ManagedJob
    public void capture() {
        String tenant = properties.getSchedule().getTenantId();
        if (tenant == null || tenant.isBlank()) {
            log.warn("体检快照定时留存未配置平台租户，跳过本轮采集");
            return;
        }
        String resolvedTenant = tenant.trim();
        Mono.fromSupplier(assembler::report)
                .subscribeOn(BlockingIoSchedulers.general())
                .map(HealthSnapshotCaptureScheduler::toCommand)
                .flatMap(operationsHealthService::capture)
                .contextWrite(TenantContext.withTenant(resolvedTenant))
                .doOnNext(dto -> log.info("体检快照定时留存完成: tenant={}, status={}", resolvedTenant, dto.getOverallStatus()))
                .subscribe(null, error -> log.error("体检快照定时留存失败: tenant={}", resolvedTenant, error));
    }

    /** 把体检报告收敛为结论级快照命令；逐项证据不落库。 */
    private static HealthSnapshotCommand toCommand(HealthReportResponse report) {
        HealthReportResponse.StatusCounts counts = report.counts();
        return HealthSnapshotCommand.builder()
                .captureSource(HealthSnapshotEntity.CAPTURE_SOURCE_SCHEDULED)
                .serviceName(report.service())
                .instanceId(report.instance())
                .scope(report.scope())
                .ruleVersion(report.ruleVersion())
                .overallStatus(report.overallStatus())
                .summary(report.summary())
                .upCount(counts == null ? null : counts.up())
                .degradedCount(counts == null ? null : counts.degraded())
                .downCount(counts == null ? null : counts.down())
                .unknownCount(counts == null ? null : counts.unknown())
                .notConfiguredCount(counts == null ? null : counts.notConfigured())
                .generatedAt(parseReportTime(report.generatedAt()))
                .build();
    }

    /** 报告生成时间为 ISO-8601 文本，无法解析时以当前时间兜底。 */
    private static LocalDateTime parseReportTime(String generatedAt) {
        if (generatedAt == null || generatedAt.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(generatedAt, DateTimeFormatter.ISO_DATE_TIME);
        } catch (DateTimeParseException e) {
            return LocalDateTime.now();
        }
    }
}
