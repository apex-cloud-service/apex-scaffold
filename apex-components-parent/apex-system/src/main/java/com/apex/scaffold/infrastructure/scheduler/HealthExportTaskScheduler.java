package com.apex.scaffold.infrastructure.scheduler;

import com.apex.scaffold.application.job.ManagedJob;
import com.apex.scaffold.application.service.HealthExportService;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.domain.model.monitor.HealthExportTaskEntity;
import com.apex.scaffold.domain.repository.HealthExportTaskRepository;
import com.apex.scaffold.domain.service.HealthExportSettingsPort;
import com.apex.scaffold.infrastructure.config.OperationsHealthProperties;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 体检快照异步导出任务调度器。
 *
 * <p>周期性认领导出任务，生成 CSV 并回写任务状态。任务表是跨实例竞争的工作队列，
 * 通过条件更新认领保证同一任务同一时刻只被一个实例处理；认领后长时间未更新的
 * {@code RUNNING} 任务（实例崩溃遗留）允许其他实例接管。</p>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>定时任务没有请求上下文，租户取自 {@code apex.operations.health.async-export.tenant-id}
 *       配置的平台租户，并在整条链路上以 Reactor Context 传播</li>
 *   <li>每轮最多认领一条，避免长时间占用调度线程</li>
 *   <li>失败原因截断到 500 字符落库，达到最大尝试次数后不再被认领</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 * @see HealthExportService
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "apex.operations.health.async-export",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class HealthExportTaskScheduler {

    /** 本调度器实例唯一标识，用于并发任务认领防冲突。 */
    private static final String WORKER_ID = "health-export-" + UUID.randomUUID();

    /** 认领租约时长：超过该时长未更新的 RUNNING 任务视为实例崩溃遗留。 */
    private static final int STALE_LEASE_MINUTES = 10;

    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;

    private final HealthExportTaskRepository taskRepository;

    private final HealthExportService exportService;

    private final HealthExportSettingsPort settings;

    private final OperationsHealthProperties properties;

    /**
     * 定时扫描并执行一条导出任务。
     *
     * <p>任务表属于租户数据，链路以 {@link TenantContext#withTenant(Mono)} 桥接执行线程
     * 租户上下文：{@code contextWrite} 单独使用只传播 Reactor Context，企业档开启租户
     * 拦截器时 JDBC 线程仍缺少 ThreadLocal 租户，任务表 SQL 会被拒绝执行。
     */
    @Scheduled(fixedDelayString = "${apex.operations.health.async-export.poll-delay-ms:5000}")
    @ManagedJob
    public void poll() {
        String tenantId = properties.getAsyncExport().getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            log.warn("体检快照异步导出未配置平台租户，跳过本轮调度");
            return;
        }
        String tenant = tenantId.trim();
        LocalDateTime staleBefore = LocalDateTime.now().minusMinutes(STALE_LEASE_MINUTES);
        Mono<Void> chain = Mono.defer(
                        () -> taskRepository.findNextCandidate(tenant, settings.maxAttempts(), staleBefore))
                .flatMap(candidate -> taskRepository
                        .claim(candidate.getId(), tenant, WORKER_ID, staleBefore)
                        .filter(Boolean::booleanValue)
                        .flatMap(claimed -> runTask(candidate, tenant)))
                .then();
        TenantContext.withTenant(chain)
                .contextWrite(TenantContext.withTenant(tenant))
                .subscribe(null, error -> log.error("体检快照异步导出调度失败", error));
    }

    private Mono<Void> runTask(HealthExportTaskEntity task, String tenant) {
        return exportService
                .generate(task)
                .flatMap(file -> taskRepository.markCompleted(
                        task.getId(), tenant, file.fileName(), file.rowCount(), file.content()))
                .onErrorResume(error -> markFailed(task.getId(), tenant, error))
                .then();
    }

    private Mono<Boolean> markFailed(Long taskId, String tenant, Throwable error) {
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        if (message.length() > MAX_ERROR_MESSAGE_LENGTH) {
            message = message.substring(0, MAX_ERROR_MESSAGE_LENGTH);
        }
        String finalMessage = message;
        return taskRepository.markFailed(taskId, tenant, finalMessage);
    }
}
