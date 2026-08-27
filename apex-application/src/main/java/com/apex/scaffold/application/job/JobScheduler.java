package com.apex.scaffold.application.job;

import com.apex.scaffold.application.service.JobLogService;
import com.apex.scaffold.domain.model.entity.JobEntity;
import com.apex.scaffold.domain.model.entity.JobLogEntity;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * 定时任务响应式调度器。
 *
 * <p>基于 Reactor 实现的应用内动态调度器，不依赖 Quartz 等外部组件。
 * 每个启用任务维护一条"计算下次触发时间 → 延迟 → 执行 → 记录日志 → 循环"
 * 的响应式调度链，支持动态注册、取消与手动触发。</p>
 *
 * <h2>并发模型</h2>
 * <ul>
 *   <li>调度计时运行在 {@code apex-job} 并行调度器上，不阻塞业务线程</li>
 *   <li>任务执行（含反射调用与日志落库）调度到同一并行池的独立线程</li>
 *   <li>禁止并发的任务通过每任务 {@link AtomicBoolean} 互斥，上一轮未完成时跳过本轮</li>
 * </ul>
 *
 * <h2>生命周期</h2>
 * <p>任务创建/更新/启停时由 {@code JobService} 调用 {@link #register(JobEntity)} 或
 * {@link #unregister(Long)} 同步调度状态；应用关闭时通过
 * {@link #shutdown()} 释放全部调度资源。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobScheduler {

    /**
     * 调度线程池名称前缀。
     */
    private static final String SCHEDULER_NAME = "apex-job";

    /**
     * 并行度：调度与执行共用的工作线程数。
     */
    private static final int SCHEDULER_THREADS = 4;

    /**
     * 任务调用执行器。
     */
    private final JobInvoker jobInvoker;

    /**
     * 任务执行日志服务。
     */
    private final JobLogService jobLogService;

    /**
     * 已注册任务的调度链，key 为任务 ID。
     */
    private final Map<Long, Disposable> schedules = new ConcurrentHashMap<>();

    /**
     * 每任务并发互斥标志，key 为任务 ID。
     */
    private final Map<Long, AtomicBoolean> running = new ConcurrentHashMap<>();

    /**
     * 调度与执行共用的并行调度器。
     */
    private final Scheduler scheduler = Schedulers.newParallel(SCHEDULER_NAME, SCHEDULER_THREADS);

    /**
     * 注册任务调度。
     *
     * <p>重复注册时先取消旧调度；仅启用状态的任务真正进入调度链。
     * 返回的 Mono 在注册完成后完成，供调用方编排后续逻辑。</p>
     *
     * @param job 任务实体
     * @return 注册完成的信号
     */
    public Mono<Void> register(JobEntity job) {
        return Mono.fromRunnable(() -> {
            unregister(job.getId());
            if (job.isEnabled()) {
                schedules.put(job.getId(), scheduleLoop(job));
                log.info("定时任务已注册调度，name: {}, cron: {}", job.getJobName(), job.getCronExpression());
            }
        });
    }

    /**
     * 取消任务调度。
     *
     * @param jobId 任务 ID
     * @return 取消完成的信号
     */
    public Mono<Void> unregister(Long jobId) {
        return Mono.fromRunnable(() -> {
            Disposable disposable = schedules.remove(jobId);
            if (disposable != null && !disposable.isDisposed()) {
                disposable.dispose();
                log.info("定时任务调度已取消，jobId: {}", jobId);
            }
            running.remove(jobId);
        });
    }

    /**
     * 立即触发一次任务执行（手动执行）。
     *
     * <p>不改变调度状态，仅执行一次并记录日志。</p>
     *
     * @param job 任务实体
     * @return 执行完成的信号
     */
    public Mono<Void> trigger(JobEntity job) {
        return execute(job).then();
    }

    /**
     * 停止调度器并释放全部资源。
     *
     * <p>在 Spring 容器销毁时调用，取消所有调度链并关闭线程池。</p>
     */
    @PreDestroy
    public void shutdown() {
        schedules.values().forEach(disposable -> {
            if (!disposable.isDisposed()) {
                disposable.dispose();
            }
        });
        schedules.clear();
        running.clear();
        scheduler.dispose();
        log.info("定时任务调度器已关闭");
    }

    /**
     * 为任务创建递归调度链。
     *
     * @param job 任务实体
     * @return 调度链的可取消句柄
     */
    private Disposable scheduleLoop(JobEntity job) {
        AtomicReference<Disposable> self = new AtomicReference<>();
        Disposable disposable = loop(job, self).subscribe();
        self.set(disposable);
        return disposable;
    }

    /**
     * 单轮调度循环：延迟到下次触发时间 → 执行 → 递归下一轮。
     *
     * <p>递归通过响应式 {@code then} 回调异步展开，不占用调用栈。
     * 调度链被取消（{@link Disposable#isDisposed()}）或无法计算下次
     * 触发时间时终止。</p>
     *
     * @param job 任务实体
     * @param self 当前调度链句柄引用
     * @return 单轮调度链
     */
    private Mono<Void> loop(JobEntity job, AtomicReference<Disposable> self) {
        return Mono.defer(() -> {
            if (self.get() == null || self.get().isDisposed()) {
                return Mono.empty();
            }
            long delayMs = nextDelayMillis(job);
            if (delayMs < 0) {
                log.warn("任务无未来触发时间，调度终止，name: {}, cron: {}", job.getJobName(), job.getCronExpression());
                return Mono.empty();
            }
            return Mono.delay(Duration.ofMillis(delayMs), scheduler)
                    .flatMap(tick -> execute(job))
                    .onErrorResume(error -> {
                        log.error("任务执行异常，name: {}", job.getJobName(), error);
                        return Mono.empty();
                    })
                    .then(Mono.defer(() -> loop(job, self)));
        });
    }

    /**
     * 计算距离下次触发时间的延迟毫秒数。
     *
     * @param job 任务实体
     * @return 延迟毫秒数；表达式非法或无法计算时返回 -1
     */
    private long nextDelayMillis(JobEntity job) {
        try {
            CronExpression cron = new CronExpression(job.getCronExpression());
            LocalDateTime now = LocalDateTime.now();
            Optional<LocalDateTime> next = cron.next(now);
            return next.map(time -> Math.max(Duration.between(now, time).toMillis(), 0))
                    .orElse(-1L);
        } catch (IllegalArgumentException e) {
            log.error("任务 Cron 表达式非法，name: {}, cron: {}", job.getJobName(), job.getCronExpression());
            return -1L;
        }
    }

    /**
     * 执行一次任务并记录执行日志。
     *
     * <p>禁止并发的任务在上一轮未完成时直接跳过本轮；
     * 执行结果（成功/失败/耗时）统一写入 {@link JobLogEntity} 并落库。</p>
     *
     * @param job 任务实体
     * @return 执行完成的信号
     */
    private Mono<Void> execute(JobEntity job) {
        AtomicBoolean flag = running.computeIfAbsent(job.getId(), key -> new AtomicBoolean(false));
        if (!job.isConcurrentAllowed() && !flag.compareAndSet(false, true)) {
            log.warn("任务禁止并发执行且上一轮未完成，跳过本轮，name: {}", job.getJobName());
            return Mono.empty();
        }
        return Mono.fromCallable(() -> {
                    JobLogEntity logEntity = JobLogEntity.start(job);
                    try {
                        jobInvoker.invoke(job);
                        logEntity.complete("任务执行成功", true, null);
                    } catch (Exception e) {
                        log.error("任务执行失败，name: {}", job.getJobName(), e);
                        logEntity.complete("任务执行失败: " + e.getMessage(), false, truncate(e));
                    }
                    return logEntity;
                })
                .subscribeOn(scheduler)
                .flatMap(jobLogService::record)
                .doFinally(signal -> flag.set(false));
    }

    /**
     * 截取异常堆栈摘要。
     *
     * @param e 异常
     * @return 前 2000 字符的堆栈文本
     */
    private String truncate(Exception e) {
        java.io.StringWriter sw = new java.io.StringWriter();
        e.printStackTrace(new java.io.PrintWriter(sw));
        String stack = sw.toString();
        return stack.length() > 2000 ? stack.substring(0, 2000) : stack;
    }
}
