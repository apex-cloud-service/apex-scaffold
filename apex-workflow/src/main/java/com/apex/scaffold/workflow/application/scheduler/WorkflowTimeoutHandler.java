package com.apex.scaffold.workflow.application.scheduler;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.workflow.application.service.WorkflowService;
import com.apex.scaffold.workflow.domain.model.Task;
import com.apex.scaffold.workflow.domain.repository.ScanLease;
import com.apex.scaffold.workflow.domain.repository.TimeoutTaskScan;
import java.net.InetAddress;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 工作流后台超时扫描入口。
 *
 * <p>互斥分两层：进程内 {@link AtomicBoolean} 阻止同一 JVM 重入，跨实例由
 * {@link ScanLease} 在 TTL 内限制跨实例扫描；租约到期后可被接管，当前不自动续租。
 * 取消会清理租约，但不保证已提交的同步 JDBC 操作立即停止。</p>
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
@Component
public class WorkflowTimeoutHandler {
    private static final String LEASE_NAME = "workflow-timeout-scan";

    private final TimeoutTaskScan taskScan;
    private final WorkflowService workflowService;
    private final ScanLease scanLease;
    private final boolean externalScheduling;
    private final Duration leaseTtl;
    private final AtomicBoolean running = new AtomicBoolean();

    /**
     * 构造工作流超时扫描入口。
     *
     * @param taskScan 超时任务扫描端口
     * @param workflowService 工作流服务
     * @param scanLease 跨实例扫描租约
     * @param executorEnabled 是否启用 XXL 执行器；与 {@code workflowEnabled} 同时为真时才跳过本地 {@code @Scheduled}
     * @param workflowEnabled 是否为工作流任务开启执行器托管
     * @param leaseSeconds 租约有效期（秒）
     */
    public WorkflowTimeoutHandler(
            TimeoutTaskScan taskScan,
            WorkflowService workflowService,
            ScanLease scanLease,
            @Value("${apex.job.executor.enabled:false}") boolean executorEnabled,
            @Value("${apex.job.workflow.enabled:false}") boolean workflowEnabled,
            @Value("${apex.workflow.timeout-scan.lease-seconds:300}") long leaseSeconds) {
        this.taskScan = taskScan;
        this.workflowService = workflowService;
        this.scanLease = scanLease;
        this.externalScheduling = executorEnabled && workflowEnabled;
        this.leaseTtl = Duration.ofSeconds(leaseSeconds);
    }

    /** 本地定时桥接；失败仅在此调度边界记录，业务 Publisher 保留失败信号。 */
    @Scheduled(fixedDelay = 300000, initialDelay = 60000)
    public void handleTimeoutTasks() {
        if (!externalScheduling) {
            scanTimeoutTasks().subscribe(ignored -> {}, error -> log.error("扫描超时任务失败", error));
        }
    }

    /** 专用端口读取持久租户，逐任务覆盖上下文；单项失败延迟至其他任务完成后传播。 */
    public Mono<Void> scanTimeoutTasks() {
        return Mono.defer(() -> {
            if (!running.compareAndSet(false, true)) {
                return Mono.error(new IllegalStateException("超时扫描正在执行"));
            }
            if (scanLease == null) {
                return scan().doFinally(signal -> running.set(false));
            }
            return scanWithLease();
        });
    }

    /** 每轮独立归属；取消后仍观察抢占结果，避免 JDBC 抢占晚到时遗留租约。 */
    private Mono<Void> scanWithLease() {
        String owner = instanceOwner();
        // cache 保留已启动的抢占订阅，取消扫描不丢失其最终结果；清理复用同一次抢占。
        Mono<Boolean> acquisition = Mono.defer(() -> scanLease.acquire(LEASE_NAME, owner, leaseTtl))
                .defaultIfEmpty(false)
                .cache();
        return Mono.usingWhen(
                Mono.just(acquisition),
                pending -> pending.flatMap(acquired -> {
                    if (!acquired) {
                        log.debug("超时扫描租约由其他实例持有，本轮跳过，owner={}", owner);
                        return Mono.empty();
                    }
                    return scan();
                }),
                pending -> finishLease(pending, owner),
                (pending, error) -> finishLease(pending, owner),
                pending -> finishLease(pending, owner));
    }

    /** 无论成功、失败还是取消，抢占和释放结束前均禁止本实例重入。 */
    private Mono<Void> finishLease(Mono<Boolean> acquisition, String owner) {
        return acquisition
                .onErrorReturn(false)
                .flatMap(acquired -> acquired ? releaseLease(owner) : Mono.<Void>empty())
                .doOnTerminate(() -> running.set(false));
    }

    /** 释放失败不掩盖扫描结果；残留租约由 TTL 兜底。 */
    private Mono<Void> releaseLease(String owner) {
        return Mono.defer(() -> scanLease.release(LEASE_NAME, owner)).onErrorResume(error -> {
            log.warn("释放超时扫描租约失败，等待租约到期，owner={}", owner, error);
            return Mono.empty();
        });
    }

    private Mono<Void> scan() {
        return Flux.defer(taskScan::findPendingTasks)
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .filter(task ->
                        task.getTimeoutAt() != null && !task.getTimeoutAt().isAfter(LocalDateTime.now()))
                .flatMapDelayError(
                        task -> Mono.defer(() -> {
                            if (task.getTenantId() == null || task.getTenantId().isBlank()) {
                                return Mono.error(new IllegalStateException("超时任务缺少持久租户归属"));
                            }
                            return Mono.defer(() -> handleTimeoutTask(task))
                                    .contextWrite(TenantContext.withTenant(task.getTenantId()));
                        }),
                        1,
                        1)
                .then();
    }

    private Mono<Void> handleTimeoutTask(Task task) {
        String strategy = task.getTimeoutStrategy() == null ? "ESCALATE" : task.getTimeoutStrategy();
        Mono<Void> result =
                switch (strategy) {
                    case "ESCALATE", "AUTO_COMPLETE" -> workflowService.handleTimeoutTask(task);
                    case "NOTIFY" -> {
                        log.info("超时任务仅通知策略尚未接入通知通道，taskId={}", task.getId());
                        yield Mono.empty();
                    }
                    default -> Mono.error(new IllegalArgumentException("未知的超时策略: " + strategy));
                };
        return result;
    }

    /** 每轮使用主机名加随机后缀，避免旧轮释放误清新轮租约。 */
    private static String instanceOwner() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception error) {
            host = "unknown-host";
        }
        return host + "-" + UUID.randomUUID();
    }
}
