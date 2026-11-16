package com.apex.scaffold.common.scheduler;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;

/**
 * 虚拟线程版阻塞任务执行器（JDK 21 {@code Thread.ofVirtual}）。
 *
 * <p>用于承接 {@link BlockingIoSchedulers} 中"等待时间远大于计算时间"的阻塞池
 * （JDBC、对象存储、远程 SDK）。平台线程池的并发上限受线程栈与内存约束，
 * 阻塞等待期间还占着载体线程；虚拟线程在阻塞 I/O 上会卸载（unmount）载体线程，
 * 因此可以在同等内存下承载高一个数量级的并发等待任务。</p>
 *
 * <h2>线程安全性</h2>
 * <p>虚拟线程不可池化：池化会让"每任务一个虚拟线程"的卸载优势退化，且池上限又会
 * 把并发重新绑死到线程数上。本实现改为两层结构：</p>
 * <ol>
 *   <li><strong>并发闸门</strong>：{@link Semaphore} 限制同时在跑的任务数，替代线程池上限，
 *       真正需要保护的稀缺资源（数据库连接、对象存储连接、下游配额）由这里兜住。</li>
 *   <li><strong>有界排队</strong>：计数型队列上限控制"已提交未完成"任务总量，
 *       超限直接抛 {@link RejectedExecutionException}，行为与
 *       {@code Schedulers.newBoundedElastic} 队列打满时一致，避免无界堆积掩盖下游故障。</li>
 * </ol>
 * <p>排队等待发生在虚拟线程内部的 {@link Semaphore#acquire()} 上，此时虚拟线程会卸载载体线程，
 * 因此"排队中的任务数"只消耗堆内存（每线程约 1KB），不会占用平台线程。</p>
 *
 * <h2>职责范围</h2>
 * <p>本执行器同时实现 {@link java.util.concurrent.ScheduledExecutorService}，
 * 因为 Reactor 的 {@code Mono.delay(d, BlockingIoSchedulers.cache())}、
 * {@code retryWhen} 与 {@code Scheduler#schedulePeriodically} 都要求底层执行器支持延迟调度，
 * 否则 {@code Schedulers.fromExecutorService} 会退化为
 * {@code UnsupportedScheduledExecutorService} 并在运行期抛异常。</p>
 * <ul>
 *   <li>延迟任务由一条独立的守护计时线程 {@code <pool>-timer} 负责到期唤醒，
 *       到期后仍交给虚拟线程 + 信号量闸门执行，不占用计时线程。</li>
 *   <li>固定频率/固定延迟的周期任务每次触发都重新走闸门，因此周期任务不会串行阻塞计时线程；
 *       闸门打满时该次触发记录告警并跳过。</li>
 * </ul>
 *
 * <h2>线程安全性</h2>
 * <p>JDK 21 中虚拟线程在 {@code synchronized} 块内阻塞会钉住（pin）载体线程，无法卸载。
 * 本项目阻塞 I/O 边界已由 {@code BlockingIoSchedulers} 统一收口，
 * {@code BlockingIoSchedulers.activeScheduler} 的 {@code synchronized} 块内只做 Scheduler 构造，
 * 不含 I/O 等待；启用虚拟线程后如怀疑钉住，可在启动参数加
 * {@code -Djdk.tracePinnedThreads=full} 观测。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.1
 */
@Slf4j
public final class VirtualThreadBoundedExecutor extends AbstractExecutorService implements ScheduledExecutorService {

    /** 池名称，用于虚拟线程命名与指标标签。 */
    private final String poolName;

    /** 虚拟线程工厂，线程名形如 {@code apex.jdbc-vt-0}，便于 jstack 与火焰图区分。 */
    private final ThreadFactory threadFactory;

    /** 延迟/周期任务的计时器：单条守护平台线程，只负责到期唤醒，不执行业务任务。 */
    private final ScheduledThreadPoolExecutor timer;

    /** 并发闸门：同时在跑的任务上限，取代平台线程池的线程数上限。 */
    private final Semaphore permits;

    /** 最大并发任务数（对外暴露为 maxThreads，保持与平台线程池指标口径一致）。 */
    private final int maxConcurrency;

    /** 排队上限：已提交未完成任务数减去并发上限后的可排队余量。 */
    private final int queueCapacity;

    /** 已提交未完成任务总数（运行中 + 排队中）。 */
    private final AtomicInteger inFlight = new AtomicInteger();

    /** 已获得许可并正在执行的任务数。 */
    private final AtomicInteger active = new AtomicInteger();

    /** 已完成（正常结束或异常结束）的任务累计数。 */
    private final AtomicLong completed = new AtomicLong();

    /** 已提交但被拒的任务累计数，用于定位下游容量不足。 */
    private final AtomicLong rejected = new AtomicLong();

    /** 停机标记；置位后新任务一律拒绝。 */
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    /**
     * 构造虚拟线程执行器。
     *
     * @param poolName       池名称（如 {@code apex.jdbc}），用于线程命名与日志
     * @param maxConcurrency 最大并发任务数，必须大于 0
     * @param queueCapacity  排队容量，必须大于 0；超限提交将被拒绝
     * @param fair           并发闸门是否公平模式，公平模式降低吞吐但避免长任务饥饿
     * @throws IllegalArgumentException 并发上限或排队容量非正
     */
    public VirtualThreadBoundedExecutor(String poolName, int maxConcurrency, int queueCapacity, boolean fair) {
        if (maxConcurrency <= 0) {
            throw new IllegalArgumentException(poolName + " maxConcurrency 必须大于 0");
        }
        if (queueCapacity <= 0) {
            throw new IllegalArgumentException(poolName + " queueCapacity 必须大于 0");
        }
        this.poolName = Objects.requireNonNull(poolName, "poolName");
        this.maxConcurrency = maxConcurrency;
        this.queueCapacity = queueCapacity;
        this.permits = new Semaphore(maxConcurrency, fair);
        this.threadFactory = Thread.ofVirtual().name(poolName + "-vt-", 0L).factory();
        this.timer = newTimer(poolName);
    }

    /**
     * 创建延迟任务计时器：单条守护平台线程，停用时丢弃未到期任务。
     *
     * @param poolName 池名称，用于线程命名
     * @return 计时器实例
     */
    private static ScheduledThreadPoolExecutor newTimer(String poolName) {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, poolName + "-timer");
            thread.setDaemon(true);
            return thread;
        });
        // 取消的延迟任务立即移出队列，避免长延迟任务长期占用堆内存
        executor.setRemoveOnCancelPolicy(true);
        // 停机后不再执行未到期的延迟任务，与 dispose() 的"立即释放"语义一致
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        return executor;
    }

    /**
     * 提交任务：先占排队名额，再启动一个虚拟线程在并发闸门内执行。
     *
     * <p>方法本身不阻塞调用线程（可能是 Netty EventLoop），
     * 阻塞等待只发生在虚拟线程内部，符合"不再用线程池上限做并发控制"的虚拟线程语义。</p>
     *
     * @param task 待执行任务
     * @throws RejectedExecutionException 执行器已停机，或已提交未完成任务数超过
     *                                    {@code maxConcurrency + queueCapacity}
     */
    @Override
    public void execute(Runnable task) {
        Objects.requireNonNull(task, "task");
        if (shutdown.get()) {
            rejected.incrementAndGet();
            throw new RejectedExecutionException(poolName + " 已停止，拒绝新任务");
        }
        int submitted = inFlight.incrementAndGet();
        int limit = maxConcurrency + queueCapacity;
        if (submitted > limit) {
            inFlight.decrementAndGet();
            rejected.incrementAndGet();
            throw new RejectedExecutionException(poolName + " 已饱和：在途任务 " + submitted + " 超过上限 " + limit + "（并发 "
                    + maxConcurrency + " + 排队 " + queueCapacity + "）");
        }
        try {
            threadFactory.newThread(() -> runGuarded(task)).start();
        } catch (Throwable failure) {
            inFlight.decrementAndGet();
            rejected.incrementAndGet();
            throw new RejectedExecutionException(poolName + " 无法启动虚拟线程", failure);
        }
    }

    /**
     * 在并发闸门内执行任务，并保证配额、计数在异常路径下正确归还。
     *
     * @param task 待执行任务
     */
    private void runGuarded(Runnable task) {
        boolean acquired = false;
        try {
            // 虚拟线程在此阻塞时会卸载载体线程，排队不占用平台线程
            permits.acquire();
            acquired = true;
            active.incrementAndGet();
            task.run();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Throwable failure) {
            // 任务异常不静默丢失：记录池名与异常，供排障定位，不改变任务间隔离
            log.error("虚拟线程池 {} 任务执行异常", poolName, failure);
        } finally {
            if (acquired) {
                active.decrementAndGet();
                permits.release();
            }
            completed.incrementAndGet();
            inFlight.decrementAndGet();
        }
    }

    /**
     * 延迟执行任务：到期后转入虚拟线程并在闸门内执行。
     *
     * @param command 待执行任务
     * @param delay   延迟时间；负数按 0 处理
     * @param unit    时间单位
     * @return 延迟任务句柄，取消后不会再提交执行
     */
    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(unit, "unit");
        return timer.schedule(() -> dispatch(command), Math.max(0L, delay), unit);
    }

    /**
     * 延迟执行有返回值的任务：到期后在虚拟线程中执行并回填结果。
     *
     * <p>Reactor 的 {@code Mono.delay(d, scheduler)} 与 {@code retryWhen} 走的就是本方法，
     * 因此返回句柄同时代理计时 Future 与任务结果，保证 {@link ScheduledFuture#get()} 语义正确。</p>
     *
     * @param callable 待执行任务
     * @param delay    延迟时间；负数按 0 处理
     * @param unit     时间单位
     * @param <V>      返回值类型
     * @return 延迟任务句柄
     */
    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        Objects.requireNonNull(callable, "callable");
        Objects.requireNonNull(unit, "unit");
        FutureTask<V> task = new FutureTask<>(callable);
        ScheduledFuture<?> scheduled = timer.schedule(
                () -> {
                    try {
                        execute(task);
                    } catch (RejectedExecutionException rejected) {
                        // 闸门打满时让等待方立即得到异常，避免在 get() 上永久挂起
                        task.cancel(false);
                        log.warn("虚拟线程池 {} 延迟任务被拒绝：{}", poolName, rejected.getMessage());
                    }
                },
                Math.max(0L, delay),
                unit);
        return new GatedScheduledFuture<>(task, scheduled);
    }

    /**
     * 固定频率提交任务：每次触发都重新进入闸门，互不阻塞计时线程。
     *
     * @param command      待执行任务
     * @param initialDelay 首次触发延迟
     * @param period       触发周期，必须大于 0
     * @param unit         时间单位
     * @return 周期任务句柄，取消后停止后续触发
     */
    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(unit, "unit");
        return timer.scheduleAtFixedRate(() -> dispatch(command), Math.max(0L, initialDelay), period, unit);
    }

    /**
     * 固定延迟提交任务：每次触发都重新进入闸门，互不阻塞计时线程。
     *
     * @param command      待执行任务
     * @param initialDelay 首次触发延迟
     * @param delay        两次触发之间的间隔，必须大于 0
     * @param unit         时间单位
     * @return 周期任务句柄，取消后停止后续触发
     */
    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(unit, "unit");
        return timer.scheduleWithFixedDelay(() -> dispatch(command), Math.max(0L, initialDelay), delay, unit);
    }

    /**
     * 把到期任务提交到虚拟线程；被拒时记录告警，不让计时线程因异常静默丢任务。
     *
     * @param task 到期任务
     */
    private void dispatch(Runnable task) {
        try {
            execute(task);
        } catch (RejectedExecutionException rejected) {
            log.warn("虚拟线程池 {} 到期任务被拒绝：{}", poolName, rejected.getMessage());
        }
    }

    /**
     * 返回最大并发任务数。
     *
     * @return 并发闸门许可总数
     */
    public int maxConcurrency() {
        return maxConcurrency;
    }

    /**
     * 返回排队容量。
     *
     * @return 允许排队等待的最大任务数
     */
    public int queueCapacity() {
        return queueCapacity;
    }

    /**
     * 返回正在执行的任务数。
     *
     * @return 已持有许可的任务数
     */
    public int activeTasks() {
        return active.get();
    }

    /**
     * 返回排队等待执行的任务数。
     *
     * @return 已提交未完成数减去正在执行数，非负
     */
    public int pendingTasks() {
        return Math.max(0, inFlight.get() - active.get());
    }

    /**
     * 返回累计完成任务数。
     *
     * @return 正常或异常结束的任务总数
     */
    public long completedTasks() {
        return completed.get();
    }

    /**
     * 返回累计被拒任务数。
     *
     * @return 因停机或饱和而拒绝的任务总数
     */
    public long rejectedTasks() {
        return rejected.get();
    }

    /**
     * 返回池名称。
     *
     * @return 构造时传入的池名称
     */
    public String poolName() {
        return poolName;
    }

    /**
     * 停止接收新任务；已提交任务继续执行完毕，未到期的延迟任务不再触发。
     */
    @Override
    public void shutdown() {
        shutdown.set(true);
        timer.shutdown();
    }

    /**
     * 停止接收新任务，丢弃未到期的延迟任务。
     *
     * <p>与平台线程池 {@code shutdownNow} 的差异：虚拟线程按任务创建、执行器不持有可枚举的
     * 工作线程集合，因此本实现不强制中断已在执行的任务，只停止计时器；
     * 需要立即中断在途任务时应由任务自身响应超时或取消信号。
     * 返回值为被丢弃的未到期定时任务。</p>
     *
     * @return 尚未执行的定时任务列表（可能为空）
     */
    @Override
    public List<Runnable> shutdownNow() {
        shutdown.set(true);
        return timer.shutdownNow();
    }

    /**
     * 返回执行器是否已停止。
     *
     * @return 已调用 {@link #shutdown()} 或 {@link #shutdownNow()}
     */
    @Override
    public boolean isShutdown() {
        return shutdown.get();
    }

    /**
     * 返回执行器是否已终止。
     *
     * @return 已停止、无在途任务且计时线程已退出
     */
    @Override
    public boolean isTerminated() {
        return shutdown.get() && inFlight.get() <= 0 && timer.isTerminated();
    }

    /**
     * 等待执行器终止。
     *
     * @param timeout 最长等待时间
     * @param unit    时间单位
     * @return 在超时前终止返回 {@code true}
     * @throws InterruptedException 等待期间被中断
     */
    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (inFlight.get() > 0) {
            if (System.nanoTime() >= deadline) {
                return false;
            }
            Thread.sleep(10L);
        }
        if (!shutdown.get()) {
            return false;
        }
        long remaining = deadline - System.nanoTime();
        return remaining <= 0L ? timer.isTerminated() : timer.awaitTermination(remaining, TimeUnit.NANOSECONDS);
    }

    /**
     * 返回可读的池状态，便于日志与健康检查。
     *
     * @return 包含并发、排队、完成与拒绝计数的描述
     */
    @Override
    public String toString() {
        return poolName + "{maxConcurrency=" + maxConcurrency + ", queueCapacity=" + queueCapacity + ", active="
                + active.get() + ", pending=" + pendingTasks() + ", completed=" + completed.get() + ", rejected="
                + rejected.get() + '}';
    }

    /**
     * 延迟任务句柄：计时状态取自计时器，执行结果取自虚拟线程任务。
     *
     * @param <V> 返回值类型
     */
    private static final class GatedScheduledFuture<V> implements ScheduledFuture<V> {

        /** 在虚拟线程中执行的任务。 */
        private final FutureTask<V> task;

        /** 计时器侧的延迟/周期句柄，负责到期与取消。 */
        private final ScheduledFuture<?> scheduled;

        /**
         * 构造延迟任务句柄。
         *
         * @param task      虚拟线程任务
         * @param scheduled 计时器句柄
         */
        private GatedScheduledFuture(FutureTask<V> task, ScheduledFuture<?> scheduled) {
            this.task = task;
            this.scheduled = scheduled;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return scheduled.getDelay(unit);
        }

        @Override
        public int compareTo(Delayed other) {
            Delayed target = other instanceof GatedScheduledFuture<?> gated ? gated.scheduled : other;
            return scheduled.compareTo(target);
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            scheduled.cancel(false);
            return task.cancel(mayInterruptIfRunning);
        }

        @Override
        public boolean isCancelled() {
            return task.isCancelled();
        }

        @Override
        public boolean isDone() {
            return task.isDone();
        }

        @Override
        public V get() throws InterruptedException, ExecutionException {
            return task.get();
        }

        @Override
        public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return task.get(timeout, unit);
        }
    }
}
