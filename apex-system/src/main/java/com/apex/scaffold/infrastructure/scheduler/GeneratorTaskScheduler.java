package com.apex.scaffold.infrastructure.scheduler;

import com.apex.scaffold.application.job.ManagedJob;
import com.apex.scaffold.application.service.GeneratorTaskService;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.domain.repository.GenTaskRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 代码生成器异步生成任务调度器。
 *
 * <p>周期性认领待执行的生成任务并在后台渲染写盘，同时对已结束任务做保留期清理。
 * 任务表是跨实例竞争的工作队列，通过条件更新认领保证同一任务同一时刻只被一个实例处理；
 * 认领后长时间未更新的 RUNNING 任务（实例崩溃遗留）允许其他实例接管。</p>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>生成任务由用户提交，租户集合不固定，无法像体检快照导出那样读取平台租户配置。
 *       每轮先用 {@code findCandidateTenants} 取有候选的租户，再逐租户串行认领</li>
 *   <li>保留期清理与认领的候选集合不同：终态任务不参与认领，需单独用
 *       {@code findFinishedTenants} 发现租户</li>
 *   <li>租户在整条链路上以 Reactor Context 传播，确保渲染链内的租户判定一致</li>
 *   <li>后台链路以 {@code subscribe(null, ...)} 收尾，属于调度器桥接边界</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.5.0
 * @see GeneratorTaskService
 * @see GenTaskRepository
 */
@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "apex.generator.async-task",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class GeneratorTaskScheduler {

    /** 本调度器实例唯一标识，用于并发任务认领防冲突。 */
    private static final String WORKER_ID = "generator-task-" + UUID.randomUUID();

    /** 保留期清理单批删除条数。 */
    private static final int PURGE_BATCH_LIMIT = 500;

    private final GeneratorTaskService taskService;

    private final GenTaskRepository genTaskRepository;

    private final int maxAttempts;

    private final int leaseMinutes;

    private final int tenantBatch;

    private final int retentionHours;

    /**
     * 构造生成任务调度器。
     *
     * @param taskService      生成任务应用服务
     * @param genTaskRepository 生成任务仓储
     * @param maxAttempts      最大尝试次数
     * @param leaseMinutes     认领租约时长（分钟）
     * @param tenantBatch      单轮最多处理的租户数
     * @param retentionHours   终态任务保留时长（小时）
     */
    public GeneratorTaskScheduler(
            GeneratorTaskService taskService,
            GenTaskRepository genTaskRepository,
            @Value("${apex.generator.async-task.max-attempts:3}") int maxAttempts,
            @Value("${apex.generator.async-task.lease-minutes:10}") int leaseMinutes,
            @Value("${apex.generator.async-task.tenant-batch:20}") int tenantBatch,
            @Value("${apex.generator.async-task.retention-hours:24}") int retentionHours) {
        this.taskService = taskService;
        this.genTaskRepository = genTaskRepository;
        this.maxAttempts = maxAttempts;
        this.leaseMinutes = leaseMinutes;
        this.tenantBatch = tenantBatch;
        this.retentionHours = retentionHours;
    }

    /**
     * 定时扫描并执行可认领的生成任务。
     */
    @Scheduled(fixedDelayString = "${apex.generator.async-task.poll-delay-ms:5000}")
    @ManagedJob
    public void poll() {
        Mono.defer(() -> genTaskRepository.findCandidateTenants(maxAttempts, leaseDeadline(), tenantBatch))
                .flatMapMany(Flux::fromIterable)
                .concatMap(this::pollTenant)
                .subscribe(null, error -> log.error("生成任务调度失败", error));
    }

    /**
     * 定时清理超出保留期的终态任务。
     */
    @Scheduled(cron = "${apex.generator.async-task.purge-cron:0 40 3 * * ?}")
    @ManagedJob
    public void purge() {
        LocalDateTime before = LocalDateTime.now().minusHours(retentionHours);
        Mono.defer(() -> genTaskRepository.findFinishedTenants(before, tenantBatch))
                .flatMapMany(Flux::fromIterable)
                .concatMap(tenant -> purgeTenant(tenant, before))
                .subscribe(null, error -> log.error("生成任务保留期清理失败", error));
    }

    /**
     * 认领并执行指定租户的一条任务。
     *
     * @param tenant 租户标识
     * @return 执行完成信号
     */
    private Mono<Void> pollTenant(String tenant) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline = now.minusMinutes(leaseMinutes);
        return Mono.defer(() -> genTaskRepository.findNextCandidate(tenant, maxAttempts, deadline))
                .flatMap(candidate -> genTaskRepository
                        .claim(candidate.getId(), tenant, WORKER_ID, now, deadline)
                        .filter(Boolean::booleanValue)
                        .flatMap(claimed -> taskService.processTask(candidate, tenant)))
                .then()
                .onErrorResume(error -> {
                    log.warn("生成任务租户调度失败: tenant={}", tenant, error);
                    return Mono.empty();
                })
                .contextWrite(TenantContext.withTenant(tenant));
    }

    /**
     * 清理指定租户的过期终态任务。
     *
     * @param tenant 租户标识
     * @param before 终态时间截止点
     * @return 清理完成信号
     */
    private Mono<Void> purgeTenant(String tenant, LocalDateTime before) {
        return Mono.defer(() -> genTaskRepository.purgeFinishedBefore(tenant, before, PURGE_BATCH_LIMIT))
                .doOnNext(deleted -> log.info("生成任务保留期清理完成: tenant={}, deleted={}", tenant, deleted))
                .then()
                .onErrorResume(error -> {
                    log.warn("生成任务租户清理失败: tenant={}", tenant, error);
                    return Mono.empty();
                })
                .contextWrite(TenantContext.withTenant(tenant));
    }

    /**
     * 计算当前时刻的租约过期阈值。
     *
     * @return 早于该时间认领的 RUNNING 任务视为过期
     */
    private LocalDateTime leaseDeadline() {
        return LocalDateTime.now().minusMinutes(leaseMinutes);
    }
}
