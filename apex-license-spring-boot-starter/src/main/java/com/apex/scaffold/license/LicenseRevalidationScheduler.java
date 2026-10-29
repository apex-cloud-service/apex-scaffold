package com.apex.scaffold.license;

import java.util.concurrent.ScheduledFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * License运行期重检调度器
 *
 * <p>本类负责在应用运行期间定期重新加载并验证License文件,实现License过期即时失效和续期自动生效。
 * 通过守护线程定时执行重检任务,无需重启应用即可响应License文件的变更。</p>
 *
 * <p><strong>主要功能</strong></p>
 * <ul>
 *   <li><b>过期即时失效</b>:License运行期到期后无需重启应用,商业功能立即拒绝访问</li>
 *   <li><b>续期自动生效</b>:替换或续期License文件后无需重启应用,授权自动恢复</li>
 *   <li><b>异常容错</b>:重检任务中的任何异常都被捕获并记录,不会导致调度线程终止</li>
 *   <li><b>优雅关闭</b>:实现{@link AutoCloseable},应用关闭时自动终止调度线程</li>
 * </ul>
 *
 * <p><strong>工作原理</strong></p>
 * <p>调度器按{@code apex.license.recheck-interval}配置的间隔(默认1小时)定时执行以下流程:
 * <ol>
 *   <li>通过{@link LicenseValidator#refresh}触发License文件重新加载</li>
 *   <li>调用{@link LicenseLoader#load}读取并解析License文件</li>
 *   <li>验证签名、检查有效期、更新功能集缓存</li>
 *   <li>如果验证失败,自动切换到降级模式(基础功能可用,商业功能拒绝)</li>
 * </ol>
 *
 * <p><strong>与即时降级的配合</strong></p>
 * <p>每次功能检查({@link LicenseValidator#isFeatureEnabled(String)})同样内置过期即时降级逻辑,
 * 重检间隔仅控制文件重新加载的频率。这种设计确保:
 * <ul>
 *   <li>即使重检间隔较长,一旦License过期,功能检查仍会立即拒绝</li>
 *   <li>重检任务失败时,已缓存的验证器仍可继续工作(直到过期)</li>
 * </ul>
 *
 * <p><strong>线程安全</strong></p>
 * <p>调度器使用单线程{@link ScheduledExecutorService},守护线程命名为{@code apex-license-recheck}。
 * 应用关闭时,通过{@link #close()}方法调用{@code shutdownNow()}立即终止调度线程。</p>
 *
 * <p><strong>配置说明</strong></p>
 * <p>通过{@code apex.license.recheck-interval}配置重检间隔:
 * <ul>
 *   <li>设置为正数(如{@code 1h}、{@code 30m})时启用定时重检</li>
 *   <li>设置为0或负数时禁用重检,调度器不会创建线程</li>
 *   <li>建议值:{@code 1h}(每小时重检一次),平衡及时性和系统开销</li>
 * </ul>
 *
 * <p><strong>使用示例</strong></p>
 * <pre>{@code
 * // application.yml配置
 * apex:
 *   license:
 *     recheck-interval: 1h  # 每小时重检一次
 *
 * // 由LicenseAutoConfiguration自动创建,无需手动实例化
 * @Bean(destroyMethod = "close")
 * public LicenseRevalidationScheduler licenseRevalidationScheduler(
 *         LicenseValidator validator) {
 *     return new LicenseRevalidationScheduler(validator, properties);
 * }
 * }</pre>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 * @see LicenseValidator
 * @see LicenseAutoConfiguration
 * @see LicenseLoader
 */
@Slf4j
public class LicenseRevalidationScheduler implements AutoCloseable {

    private final ThreadPoolTaskScheduler executor;
    private ScheduledFuture<?> refreshTask;

    /**
     * 创建License运行期重检调度器
     *
     * <p>根据{@code apex.license.recheck-interval}配置决定是否启动定时重检:
     * <ul>
     *   <li>间隔>0时,创建守护线程并以固定延迟模式定时执行重检任务</li>
     *   <li>间隔≤0时,不创建线程,调度器处于关闭状态</li>
     * </ul>
     *
     * @param validator 共享的验证器实例,与{@link FeatureGateAspect}切面使用同一实例
     * @param properties License配置,包含重检间隔等参数
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
        this.executor = new ThreadPoolTaskScheduler();
        this.executor.setPoolSize(1);
        this.executor.setThreadNamePrefix("apex-license-recheck-");
        this.executor.setDaemon(true);
        this.executor.initialize();
        this.refreshTask = this.executor.scheduleWithFixedDelay(
                () -> safeRefresh(validator, properties), java.time.Duration.ofMillis(intervalMs));
        log.info("License 运行期重检已启动: 间隔 {}ms", intervalMs);
    }

    /**
     * 执行单次License重检任务
     *
     * <p>捕获所有异常并记录WARN日志,确保任何异常都不会导致调度任务终止。
     * 这种容错设计保证即使某次重检失败(如文件被占用、网络异常),后续重检仍能正常执行。</p>
     *
     * @param validator 验证器实例,用于触发文件重新加载
     * @param properties License配置,传递给加载器
     */
    private void safeRefresh(LicenseValidator validator, LicenseProperties properties) {
        // enabled=false 为强制降级（不可绕过授权）：禁止重检把随包有效 License 重新加载而解除降级
        if (!properties.isEnabled()) {
            log.warn("apex.license.enabled=false —— 跳过运行期重检，保持强制降级模式");
            return;
        }
        try {
            validator.refresh(() -> LicenseLoader.load(properties));
        } catch (Exception e) {
            log.warn("License 重检任务异常: {}", e.getMessage());
        }
    }

    @Override
    public void close() {
        if (refreshTask != null) {
            refreshTask.cancel(false);
        }
        if (executor != null) {
            executor.shutdown();
        }
    }
}
