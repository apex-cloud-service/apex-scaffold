package com.apex.scaffold.license;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * License 运行期重检调度器
 *
 * <p>按 {@code apex.license.recheck-interval}（默认 1 小时）定时重新加载并验证
 * License 文件，实现：
 * <ul>
 *   <li><b>过期失效</b>：License 运行期到期后无需重启，商业功能即时拒绝</li>
 *   <li><b>续期生效</b>：替换/续期 License 文件后无需重启，自动恢复授权</li>
 * </ul>
 *
 * <p>每次功能检查（{@link LicenseValidator#isFeatureEnabled(String)}）同样内置
 * 过期即时降级，重检间隔仅控制文件重新加载的频率。调度线程为守护线程，
 * 应用关闭时由 {@link #close()} 终止。</p>
 *
 * @author Apex Team
 * @version 2.2.0
 */
@Slf4j
public class LicenseRevalidationScheduler implements AutoCloseable {

    private final ScheduledExecutorService executor;

    /**
     * 创建调度器
     *
     * @param validator 共享的验证器实例（与切面使用同一实例）
     * @param properties License 配置
     */
    public LicenseRevalidationScheduler(LicenseValidator validator, LicenseProperties properties) {
        long intervalMs = properties.getRecheckInterval() == null
                ? 0
                : properties.getRecheckInterval().toMillis();
        if (intervalMs <= 0) {
            log.info("License 运行期重检已关闭 (apex.license.recheck-interval={})", properties.getRecheckInterval());
            this.executor = null;
            return;
        }
        this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "apex-license-recheck");
            thread.setDaemon(true);
            return thread;
        });
        this.executor.scheduleWithFixedDelay(
                () -> safeRefresh(validator, properties), intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        log.info("License 运行期重检已启动: 间隔 {}ms", intervalMs);
    }

    /** 单次重检：任何异常都被吞掉（调度任务不允许因异常终止）。 */
    private void safeRefresh(LicenseValidator validator, LicenseProperties properties) {
        try {
            validator.refresh(() -> LicenseLoader.load(properties));
        } catch (Exception e) {
            log.warn("License 重检任务异常: {}", e.getMessage());
        }
    }

    @Override
    public void close() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }
}
