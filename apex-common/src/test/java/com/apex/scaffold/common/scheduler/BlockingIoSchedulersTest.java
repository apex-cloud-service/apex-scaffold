package com.apex.scaffold.common.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers.SchedulerPoolSnapshot;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.scheduler.Scheduler;

/**
 * {@code BlockingIoSchedulers} 纯单元测试类（不启动 Spring 上下文）。
 *
 * <p>验证六大专用阻塞 I/O Scheduler 的创建、任务调度能力以及运行态指标快照。
 * 由于 {@code BlockingIoSchedulers} 对外暴露全部为静态方法且内部使用静态
 * {@code AtomicReference} 持有 Scheduler，本测试直接通过静态方法调用完成验证，
 * 无需引入 Spring Test 框架，执行速度毫秒级。
 *
 * <h2>覆盖场景</h2>
 * <ol>
 *   <li>Scheduler 创建与可调度性：六类池均返回非空 Scheduler，且
 *       {@code schedule(Runnable)} 能在 1 秒内完成一个 CountDownLatch 任务。</li>
 *   <li>整体快照能力：{@code snapshotAll()} 返回的 Map 包含全部六类池键，
 *       每个 {@link SchedulerPoolSnapshot} 的 maxThreads / queueCapacity 与
 *       BlockingIoSchedulers 内部默认常量一致。</li>
 *   <li>饱和度算法边界：{@link SchedulerPoolSnapshot#saturation()} 在
 *       零线程、满负载、半负载与超饱和四种情况下计算结果符合预期。</li>
 * </ol>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
class BlockingIoSchedulersTest {

    /** 任务调度等待超时时间（秒）。防止测试卡死，取值略大于典型调度延迟。 */
    private static final int SCHEDULE_TIMEOUT_SECONDS = 1;

    /**
     * 六大专用池均应返回非空 Scheduler 且能够成功调度任务。
     *
     * <p>依次调用 {@code jdbc() / cache() / storage() / event() / remote() / general()}
     * 六个静态方法：
     * <ol>
     *   <li>断言返回值非 {@code null}，避免静态初始化或 dispose 重建失败。</li>
     *   <li>对每个 Scheduler 调用 {@code schedule(Runnable)} 提交一个递减
     *       {@link CountDownLatch} 的任务，等待最长 1 秒后断言 Latch 归零，
     *       证明任务确实被目标池线程成功执行（而非被丢弃或 Reject）。</li>
     * </ol>
     *
     * <p>注意：即使 Spring 未注入自定义配置，BlockingIoSchedulers 的静态字段
     * 也会在类加载时以默认常量构造 Scheduler，因此保证本测试无需上下文即可运行。
     *
     * @throws InterruptedException 若等待 Latch 期间线程被中断
     */
    @Test
    @DisplayName("六个专用池均返回非空 Scheduler 并能调度任务")
    void allSixPoolsShouldReturnNonNullScheduler() throws InterruptedException {
        assertSchedulerWorks("jdbc", BlockingIoSchedulers.jdbc());
        assertSchedulerWorks("cache", BlockingIoSchedulers.cache());
        assertSchedulerWorks("storage", BlockingIoSchedulers.storage());
        assertSchedulerWorks("event", BlockingIoSchedulers.event());
        assertSchedulerWorks("remote", BlockingIoSchedulers.remote());
        assertSchedulerWorks("general", BlockingIoSchedulers.general());
    }

    /**
     * 辅助断言：验证单个 Scheduler 非空并能在超时内完成一次任务调度。
     *
     * @param poolName  池名称（仅用于断言失败消息，不参与逻辑）
     * @param scheduler 待验证的 Scheduler
     * @throws InterruptedException 若等待 Latch 期间线程被中断
     */
    private void assertSchedulerWorks(String poolName, Scheduler scheduler) throws InterruptedException {
        assertNotNull(scheduler, poolName + " Scheduler 不应为 null");
        CountDownLatch latch = new CountDownLatch(1);
        scheduler.schedule(latch::countDown);
        boolean completed = latch.await(SCHEDULE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertTrue(completed, poolName + " Scheduler 未能在 " + SCHEDULE_TIMEOUT_SECONDS + "s 内完成任务调度");
    }

    /**
     * {@code snapshotAll()} 应返回全部六个专用池的快照，且参数与默认常量一致。
     *
     * <p>测试步骤：
     * <ol>
     *   <li>先调用六类池的静态方法触发初始化（避免静态字段仍处于空快照）。</li>
     *   <li>调用 {@code BlockingIoSchedulers#snapshotAll()} 获取不可变 Map。</li>
     *   <li>断言 {@code Map.size()=6} 且 keySet 精确包含
     *       {@code [jdbc, cache, storage, event, remote, general]}。</li>
     *   <li>遍历每个快照，断言 {@code maxThreads} 与 {@code queueCapacity}
     *       与 BlockingIoSchedulers 内部常量一致：
     *       <ul>
     *         <li>jdbc：max=32，cap=128</li>
     *         <li>cache：max=4，cap=512</li>
     *         <li>storage：max=8，cap=1024</li>
     *         <li>event：max=2，cap=2048</li>
     *         <li>remote：max=16，cap=1024</li>
     *         <li>general：max=16，cap=1024</li>
     *       </ul>
     *   </li>
     * </ol>
     */
    @Test
    @DisplayName("snapshotAll 返回六个专用池快照且参数匹配默认常量")
    void snapshotAllShouldReturnSixPools() {
        BlockingIoSchedulers.jdbc();
        BlockingIoSchedulers.cache();
        BlockingIoSchedulers.storage();
        BlockingIoSchedulers.event();
        BlockingIoSchedulers.remote();
        BlockingIoSchedulers.general();

        Map<String, SchedulerPoolSnapshot> snapshots = BlockingIoSchedulers.snapshotAll();
        assertEquals(6, snapshots.size(), "snapshotAll() 应返回 6 个专用池");

        Set<String> expectedKeys = Set.of("jdbc", "cache", "storage", "event", "remote", "general");
        assertEquals(expectedKeys, snapshots.keySet(), "snapshotAll() 键集合应包含全部六个专用池名称");

        assertSnapshot(snapshots.get("jdbc"), "jdbc", 32, 128);
        assertSnapshot(snapshots.get("cache"), "cache", 4, 512);
        assertSnapshot(snapshots.get("storage"), "storage", 8, 1024);
        assertSnapshot(snapshots.get("event"), "event", 2, 2048);
        assertSnapshot(snapshots.get("remote"), "remote", 16, 1024);
        assertSnapshot(snapshots.get("general"), "general", 16, 1024);
    }

    /**
     * 辅助断言：验证单个 SchedulerPoolSnapshot 的名称、maxThreads 与 queueCapacity。
     *
     * @param snapshot       待校验的快照对象
     * @param expectedName   预期池名称
     * @param expectedMax    预期最大线程数
     * @param expectedCap    预期队列容量
     */
    private void assertSnapshot(SchedulerPoolSnapshot snapshot, String expectedName, int expectedMax, int expectedCap) {
        assertNotNull(snapshot, expectedName + " 快照不应为 null");
        assertEquals(expectedName, snapshot.poolName(), expectedName + ".poolName 不匹配");
        assertEquals(expectedMax, snapshot.maxThreads(), expectedName + ".maxThreads 不匹配默认常量");
        assertEquals(expectedCap, snapshot.queueCapacity(), expectedName + ".queueCapacity 不匹配默认常量");
    }

    /**
     * {@link SchedulerPoolSnapshot#saturation()} 饱和度计算在边界条件下应正确。
     *
     * <p>饱和度 = {@code min(1.0, activeTasks / maxThreads)}，特殊情况
     * {@code maxThreads <= 0} 时直接返回 0.0，避免除零。本测试覆盖四种典型边界：
     * <ol>
     *   <li>{@code maxThreads=0} → 防御性返回 0.0。</li>
     *   <li>{@code active=max=1} → 刚好占满返回 1.0。</li>
     *   <li>{@code max=2, active=1} → 半负载返回 0.5。</li>
     *   <li>{@code max=4, active=5} → 超饱和时被 clamp 到 1.0（不返回 1.25）。</li>
     * </ol>
     *
     * <p>所有比较均使用 {@code assertEquals} 而非 epsilon，上述算式结果在 double
     * 精度下可精确表示，因此无需容错区间。
     */
    @Test
    @DisplayName("SchedulerPoolSnapshot.saturation 边界计算正确")
    void schedulerPoolSnapshotSaturationShouldBeCorrect() {
        assertEquals(
                0.0,
                new SchedulerPoolSnapshot("t", 0, 0, 0, 0, 0L).saturation(),
                "maxThreads=0 时 saturation 应为 0.0（防御性）");
        assertEquals(
                1.0, new SchedulerPoolSnapshot("t", 1, 10, 1, 0, 0L).saturation(), "active=max 时 saturation 应为 1.0");
        assertEquals(
                0.5,
                new SchedulerPoolSnapshot("t", 2, 10, 1, 0, 0L).saturation(),
                "active=1, max=2 时 saturation 应为 0.5");
        assertEquals(
                1.0,
                new SchedulerPoolSnapshot("t", 4, 10, 5, 0, 0L).saturation(),
                "active>max 时 saturation 应被 clamp 到 1.0");
    }
}
