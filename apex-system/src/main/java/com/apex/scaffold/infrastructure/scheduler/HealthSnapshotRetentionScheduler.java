package com.apex.scaffold.infrastructure.scheduler;

import com.apex.scaffold.application.job.ManagedJob;
import com.apex.scaffold.application.service.HealthExportService;
import com.apex.scaffold.application.service.OperationsHealthService;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.infrastructure.config.OperationsHealthProperties;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 体检快照与导出结果保留期清理调度器。
 *
 * <p>体检快照只增不减会持续占用存储，导出结果还包含整份 CSV 大字段。本调度器按保留期
 * 物理删除过期数据，单批条数与批次数上限限制单次事务规模。</p>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>物理删除而非逻辑删除，才能真正回收容量；快照删除的租户范围由持久化拦截器追加</li>
 *   <li>导出任务清理使用注解 SQL，显式携带 {@code tenant_id}</li>
 *   <li>定时任务无请求上下文，租户取自配置的平台租户</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "apex.operations.health.retention",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class HealthSnapshotRetentionScheduler {

    /** 导出结果单次清理条数。 */
    private static final int EXPORT_PURGE_LIMIT = 200;

    private final HealthExportService exportService;

    private final OperationsHealthService operationsHealthService;

    private final OperationsHealthProperties properties;

    /**
     * 定时清理超出保留期的体检快照与导出结果。
     *
     * <p>快照与导出任务表均属租户数据，链路以 {@link TenantContext#withTenant(Mono)}
     * 桥接执行线程租户上下文：{@code contextWrite} 单独使用只传播 Reactor Context，
     * 企业档开启租户拦截器时 JDBC 线程仍缺少 ThreadLocal 租户，SQL 会被拒绝执行。
     */
    @Scheduled(cron = "${apex.operations.health.retention.cron:0 30 3 * * ?}")
    @ManagedJob
    public void purge() {
        String tenant = properties.getAsyncExport().getTenantId();
        if (tenant == null || tenant.isBlank()) {
            log.warn("体检快照保留期清理未配置平台租户，跳过本轮清理");
            return;
        }
        String resolvedTenant = tenant.trim();
        OperationsHealthProperties.Retention retention = properties.getRetention();
        LocalDateTime before = LocalDateTime.now().minusDays(retention.getDays());
        Mono<Void> chain = Mono.defer(() ->
                        operationsHealthService.purge(before, retention.getBatchSize(), retention.getMaxBatches()))
                .doOnNext(deleted -> log.info("体检快照清理完成: tenant={}, deleted={}", resolvedTenant, deleted))
                .then(Mono.defer(() -> exportService.purgeExpired(resolvedTenant, EXPORT_PURGE_LIMIT)))
                .doOnNext(deleted -> log.info("体检导出结果清理完成: tenant={}, deleted={}", resolvedTenant, deleted))
                .then();
        TenantContext.withTenant(chain)
                .contextWrite(TenantContext.withTenant(resolvedTenant))
                .subscribe(
                        null,
                        error -> log.error("体检快照保留期清理失败: tenant={}", resolvedTenant, error),
                        () -> log.debug(
                                "体检保留期配置: days={}, batchSize={}, maxBatches={}",
                                retention.getDays(),
                                retention.getBatchSize(),
                                retention.getMaxBatches()));
    }
}
