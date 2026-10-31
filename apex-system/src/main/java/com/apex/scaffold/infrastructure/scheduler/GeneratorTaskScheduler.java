package com.apex.scaffold.infrastructure.scheduler;

import com.apex.scaffold.application.job.ManagedJob;
import com.apex.scaffold.application.service.GeneratorTaskService;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.domain.model.entity.TenantEntity;
import com.apex.scaffold.domain.repository.GenTaskRepository;
import com.apex.scaffold.domain.repository.TenantRepository;
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
 * 认领后长时间未更新的 RUNNING 任务（实例崩溃遗留）允许其他实例接管，接管候选与
 * 首次认领同样受尝试次数上限约束，耗尽重试次数的任务停在终局等待人工介入。</p>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>生成任务由用户提交，租户集合不固定。租户发现读取平台目录 {@code sys_tenant}
 *       （租户白名单表，不经租户拦截器），再逐租户串行认领；任务表本身是租户数据，
 *       不能做跨租户全表扫描，这与作业调度的「先取租户列表、再逐租户执行」口径一致</li>
 *   <li>每轮逐租户链路以 {@code TenantContext.withTenant(Mono)} 桥接执行线程租户上下文，
 *       保证企业档开启租户拦截器时，任务表上的条件更新仍可携带可信租户执行；
 *       {@code contextWrite} 单独使用只传播 Reactor Context，不满足 JDBC 线程的
 *       ThreadLocal 语义</li>
 *   <li>认领成功后按任务主键重读数据库实体，以数据库中的最新 attempts/totalCount/lockedBy
 *       作为执行依据，不使用候选查询时的内存快照，避免接管后计数漂移</li>
 *   <li>保留期清理与认领的候选集合不同：终态任务不参与认领，清理按租户独立分批执行；
 *       删除采用「先查主键、后按主键删除」的两步实现，双方言语义一致</li>
 *   <li>后台链路以 {@code subscribe(null, ...)} 收尾，属于调度器桥接边界</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.1.0
 * @since 1.5.0
 * @see GeneratorTaskService
 * @see GenTaskRepository
 * @see TenantRepository
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

    private final TenantRepository tenantRepository;

    private final int maxAttempts;

    private final int leaseMinutes;

    private final int tenantBatch;

    private final int retentionHours;

    /**
     * 构造生成任务调度器。
     *
     * @param taskService       生成任务应用服务
     * @param genTaskRepository 生成任务仓储
     * @param tenantRepository  租户仓储，用于读取平台租户目录发现候选租户
     * @param maxAttempts       最大尝试次数
     * @param leaseMinutes      认领租约时长（分钟）
     * @param tenantBatch       单轮最多处理的租户数
     * @param retentionHours    终态任务保留时长（小时）
     */
    public GeneratorTaskScheduler(
            GeneratorTaskService taskService,
            GenTaskRepository genTaskRepository,
            TenantRepository tenantRepository,
            @Value("${apex.generator.async-task.max-attempts:3}") int maxAttempts,
            @Value("${apex.generator.async-task.lease-minutes:10}") int leaseMinutes,
            @Value("${apex.generator.async-task.tenant-batch:20}") int tenantBatch,
            @Value("${apex.generator.async-task.retention-hours:24}") int retentionHours) {
        this.taskService = taskService;
        this.genTaskRepository = genTaskRepository;
        this.tenantRepository = tenantRepository;
        this.maxAttempts = maxAttempts;
        this.leaseMinutes = leaseMinutes;
        this.tenantBatch = tenantBatch;
        this.retentionHours = retentionHours;
    }

    /**
     * 定时扫描并执行可认领的生成任务。
     *
     * <p>先读平台租户目录（白名单表，无需租户上下文），再逐租户串行认领；
     * 单个租户失败仅记录告警，不阻断其余租户。
     */
    @Scheduled(fixedDelayString = "${apex.generator.async-task.poll-delay-ms:5000}")
    @ManagedJob
    public void poll() {
        tenantRepository
                .findAll()
                .flatMapMany(Flux::fromIterable)
                .map(TenantEntity::getTenantCode)
                .filter(code -> code != null && !code.isBlank())
                .take(tenantBatch)
                .concatMap(this::pollTenant)
                .then()
                .subscribe(null, error -> log.error("生成任务调度失败", error));
    }

    /**
     * 定时清理超出保留期的终态任务。
     *
     * <p>候选集合与认领调度独立：按租户目录逐租户分批删除，单批
     * {@value #PURGE_BATCH_LIMIT} 条，避免长事务与全表扫描。
     */
    @Scheduled(cron = "${apex.generator.async-task.purge-cron:0 40 3 * * ?}")
    @ManagedJob
    public void purge() {
        LocalDateTime before = LocalDateTime.now().minusHours(retentionHours);
        tenantRepository
                .findAll()
                .flatMapMany(Flux::fromIterable)
                .map(TenantEntity::getTenantCode)
                .filter(code -> code != null && !code.isBlank())
                .take(tenantBatch)
                .concatMap(tenant -> purgeTenant(tenant, before))
                .then()
                .subscribe(null, error -> log.error("生成任务保留期清理失败", error));
    }

    /**
     * 认领并执行指定租户的一条任务。
     *
     * <p>认领成功后重读任务实体（获取数据库最新 attempts/totalCount/lockedBy）再交由
     * 应用服务执行；整条链路桥接租户上下文，保证任务表 SQL 在租户拦截器下可执行。
     *
     * @param tenant 租户标识
     * @return 执行完成信号
     */
    private Mono<Void> pollTenant(String tenant) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline = now.minusMinutes(leaseMinutes);
        Mono<Void> chain = Mono.defer(() -> genTaskRepository.findNextCandidate(tenant, maxAttempts, deadline))
                .flatMap(candidate -> genTaskRepository
                        .claim(candidate.getId(), tenant, WORKER_ID, now, deadline)
                        .filter(Boolean::booleanValue)
                        .flatMap(claimed -> genTaskRepository
                                .findById(candidate.getId(), tenant)
                                .switchIfEmpty(Mono.defer(() -> {
                                    log.warn("生成任务认领后读取失败，跳过执行: taskId={}", candidate.getId());
                                    return Mono.empty();
                                }))
                                .flatMap(fresh -> taskService.processTask(fresh, tenant))))
                .then()
                .onErrorResume(error -> {
                    log.warn("生成任务租户调度失败: tenant={}", tenant, error);
                    return Mono.empty();
                });
        return TenantContext.withTenant(chain).contextWrite(TenantContext.withTenant(tenant));
    }

    /**
     * 清理指定租户的过期终态任务。
     *
     * @param tenant 租户标识
     * @param before 终态时间截止点
     * @return 清理完成信号
     */
    private Mono<Void> purgeTenant(String tenant, LocalDateTime before) {
        Mono<Void> chain = Mono.defer(() -> genTaskRepository.purgeFinishedBefore(tenant, before, PURGE_BATCH_LIMIT))
                .doOnNext(deleted -> log.info("生成任务保留期清理完成: tenant={}, deleted={}", tenant, deleted))
                .then()
                .onErrorResume(error -> {
                    log.warn("生成任务租户清理失败: tenant={}", tenant, error);
                    return Mono.empty();
                });
        return TenantContext.withTenant(chain).contextWrite(TenantContext.withTenant(tenant));
    }
}
