package com.apex.scaffold.common.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * {@link VirtualThreadBoundedExecutor} 单元测试（不启动 Spring 上下文）。
 *
 * <h2>覆盖场景</h2>
 * <ol>
 *   <li>任务确实运行在虚拟线程上，而非平台载体线程。</li>
 *   <li>并发闸门生效：同时执行的任务数不超过 maxConcurrency，超出的任务进入排队。</li>
 *   <li>排队上限生效：超过 {@code maxConcurrency + queueCapacity} 的提交被拒绝。</li>
 *   <li>运行态统计（active / pending / completed）与停机后拒绝语义正确。</li>
 *   <li>可被 Reactor {@link Schedulers#fromExecutorService} 包装并正常调度与释放。</li>
 *   <li>延迟任务（{@code Mono.delay} / {@code schedule(Callable)}) 仍在虚拟线程上执行，
 *       验证 {@link java.util.concurrent.ScheduledExecutorService} 语义完整。</li>
 * </ol>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.1
 */
class VirtualThreadBoundedExecutorTest {

    /** 断言等待上限，避免执行器行为异常时测试挂死。 */
    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(5);

    /**
     * 任务应运行在虚拟线程上。
     */
    @Test
    @DisplayName("任务运行在虚拟线程上")
    void tasksRunOnVirtualThreads() throws Exception {
        VirtualThreadBoundedExecutor executor = new VirtualThreadBoundedExecutor("test-vt", 2, 4, false);
        AtomicReference<Thread> runner = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        executor.execute(() -> {
            runner.set(Thread.currentThread());
            done.countDown();
        });

        assertThat(done.await(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
        assertThat(runner.get().isVirtual()).as("应运行在虚拟线程上").isTrue();
        executor.shutdown();
    }

    /**
     * 并发任务数不得超过 maxConcurrency，多余任务进入排队。
     */
    @Test
    @DisplayName("并发闸门限制同时执行的任务数")
    void enforcesMaxConcurrency() throws Exception {
        VirtualThreadBoundedExecutor executor = new VirtualThreadBoundedExecutor("test-concurrency", 2, 8, false);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger running = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();

        for (int i = 0; i < 4; i++) {
            executor.execute(() -> {
                peak.accumulateAndGet(running.incrementAndGet(), Math::max);
                awaitQuietly(release);
                running.decrementAndGet();
            });
        }

        assertThat(awaitUntil(() -> executor.activeTasks() == 2, AWAIT_TIMEOUT))
                .as("并发上限为 2 时应有 2 个任务在执行")
                .isTrue();
        assertThat(executor.pendingTasks()).as("另外 2 个任务应在排队").isEqualTo(2);

        release.countDown();
        assertThat(awaitUntil(() -> executor.completedTasks() == 4, AWAIT_TIMEOUT))
                .as("放行后 4 个任务应全部完成")
                .isTrue();
        assertThat(peak.get()).as("并发峰值不应超过 maxConcurrency").isEqualTo(2);
        executor.shutdown();
    }

    /**
     * 在途任务超过 maxConcurrency + queueCapacity 时应拒绝，而不是无界堆积。
     */
    @Test
    @DisplayName("排队上限满后拒绝新任务")
    void rejectsWhenSaturated() throws Exception {
        VirtualThreadBoundedExecutor executor = new VirtualThreadBoundedExecutor("test-reject", 1, 1, false);
        CountDownLatch release = new CountDownLatch(1);

        executor.execute(() -> awaitQuietly(release));
        executor.execute(() -> awaitQuietly(release));
        assertThat(awaitUntil(() -> executor.activeTasks() == 1, AWAIT_TIMEOUT)).isTrue();

        assertThatThrownBy(() -> executor.execute(() -> {}))
                .as("在途任务达到 1 + 1 后应拒绝新任务")
                .isInstanceOf(RejectedExecutionException.class);

        release.countDown();
        executor.shutdown();
    }

    /**
     * 统计计数应随任务生命周期变化。
     */
    @Test
    @DisplayName("完成数、拒绝数统计正确")
    void reportsStatistics() throws Exception {
        VirtualThreadBoundedExecutor executor = new VirtualThreadBoundedExecutor("test-stats", 2, 2, false);
        CountDownLatch release = new CountDownLatch(1);
        executor.execute(() -> awaitQuietly(release));

        assertThat(awaitUntil(() -> executor.activeTasks() == 1, AWAIT_TIMEOUT)).isTrue();
        release.countDown();
        assertThat(awaitUntil(() -> executor.completedTasks() == 1, AWAIT_TIMEOUT))
                .isTrue();
        assertThat(executor.activeTasks()).isZero();
        assertThat(executor.rejectedTasks()).isZero();
        executor.shutdown();
    }

    /**
     * 停机后提交的任务应被拒绝，且状态查询与 toString 可用。
     */
    @Test
    @DisplayName("停机后拒绝新任务")
    void rejectsAfterShutdown() {
        VirtualThreadBoundedExecutor executor = new VirtualThreadBoundedExecutor("test-shutdown", 1, 1, false);
        executor.shutdown();

        assertThat(executor.isShutdown()).isTrue();
        assertThatThrownBy(() -> executor.execute(() -> {})).isInstanceOf(RejectedExecutionException.class);
        assertThat(executor.rejectedTasks()).isEqualTo(1L);
        assertThat(executor.toString()).contains("test-shutdown");
    }

    /**
     * 被 Reactor 包装后应能正常调度任务，dispose 不抛异常。
     */
    @Test
    @DisplayName("可被 Reactor fromExecutorService 包装")
    void worksAsReactorScheduler() {
        VirtualThreadBoundedExecutor executor = new VirtualThreadBoundedExecutor("test-reactor", 4, 16, false);
        Scheduler scheduler = Schedulers.fromExecutorService(executor, "test-reactor");
        AtomicReference<Thread> runner = new AtomicReference<>();

        String result = Mono.fromCallable(() -> {
                    runner.set(Thread.currentThread());
                    return "ok";
                })
                .subscribeOn(scheduler)
                .block(AWAIT_TIMEOUT);

        assertThat(result).isEqualTo("ok");
        assertThat(runner.get().isVirtual()).as("Reactor 调度应落在虚拟线程上").isTrue();
        scheduler.dispose();
    }

    /**
     * {@code Mono.delay(d, scheduler)} 依赖底层执行器的延迟调度能力，不能退化为不支持。
     */
    @Test
    @DisplayName("支持延迟调度：Mono.delay 正常触发且落在虚拟线程")
    void supportsDelayedSchedulingThroughReactor() {
        VirtualThreadBoundedExecutor executor = new VirtualThreadBoundedExecutor("test-delay", 2, 4, false);
        Scheduler scheduler = Schedulers.fromExecutorService(executor, "test-delay");
        AtomicReference<Thread> runner = new AtomicReference<>();

        String result = Mono.delay(Duration.ofMillis(50), scheduler)
                .then(Mono.fromCallable(() -> {
                    runner.set(Thread.currentThread());
                    return "delayed";
                }))
                .block(AWAIT_TIMEOUT);

        assertThat(result).as("延迟任务应完成").isEqualTo("delayed");
        assertThat(runner.get().isVirtual()).as("延迟任务后续处理应落在虚拟线程上").isTrue();
        scheduler.dispose();
    }

    /**
     * 延迟 Callable 应在虚拟线程执行，并把结果回填给等待方。
     */
    @Test
    @DisplayName("延迟 Callable 在虚拟线程执行并回填结果")
    void schedulesCallableOnVirtualThread() throws Exception {
        VirtualThreadBoundedExecutor executor = new VirtualThreadBoundedExecutor("test-callable", 1, 2, false);
        AtomicReference<Thread> runner = new AtomicReference<>();

        ScheduledFuture<String> future = executor.schedule(
                () -> {
                    runner.set(Thread.currentThread());
                    return "value";
                },
                20L,
                TimeUnit.MILLISECONDS);

        assertThat(future.get(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)).isEqualTo("value");
        assertThat(runner.get().isVirtual()).as("Callable 应运行在虚拟线程上").isTrue();
        executor.shutdownNow();
    }

    /**
     * 轮询等待条件成立。
     *
     * @param condition 判定条件
     * @param timeout   最长等待时间
     * @return 条件在超时前成立返回 {@code true}
     * @throws InterruptedException 等待期间被中断
     */
    private static boolean awaitUntil(BooleanSupplier condition, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(10L);
        }
        return condition.getAsBoolean();
    }

    /**
     * 等待闩锁放行，忽略中断但保留中断标记。
     *
     * @param latch 待等待的闩锁
     */
    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
