package com.apex.scaffold.interfaces.config;

import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Sentinel 规则文件监听器
 *
 * <p>监听 Sentinel 规则文件变化，自动重新加载规则，实现真正的热更新。
 * 使用 Java NIO WatchService 监听文件系统变化事件。
 *
 * <h2>设计说明</h2>
 * <ol>
 *   <li>启动时注册文件监听器，监听规则文件所在目录</li>
 *   <li>文件发生修改时，触发 ENTRY_MODIFY 事件</li>
 *   <li>自动调用 SentinelDynamicRulesLoader 重新加载规则</li>
 *   <li>新规则立即生效，无需重启应用</li>
 * </ol>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>ENTRY_MODIFY - 文件内容修改</li>
 *   <li>ENTRY_CREATE - 新文件创建（可选）</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <pre>
 * apex:
 *   sentinel:
 *     dynamic-rules:
 *       enabled: true
 *       watch-enabled: true  # 启用文件监听
 *       rules-file: file:/path/to/sentinel-rules.json
 * </pre>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>生产环境动态调整流控规则，无需重启</li>
 *   <li>紧急情况下快速修改降级策略</li>
 *   <li>A/B 测试不同的限流配置</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>规则文件必须是有效的 JSON 格式，否则加载失败</li>
 *   <li>加载失败不会影响当前运行的规则</li>
 *   <li>监听线程在应用关闭时自动停止</li>
 *   <li>支持本地文件和网络文件系统（NFS）</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "apex.sentinel.dynamic-rules",
        name = "watch-enabled",
        havingValue = "true",
        matchIfMissing = false)
public class SentinelRuleFileWatcher {

    private static final int SHUTDOWN_AWAIT_SECONDS = 5;

    private final SentinelDynamicRulesLoader rulesLoader;

    private WatchService watchService;
    private ExecutorService executorService;
    private volatile boolean running = false;

    /**
     * 初始化文件监听器
     *
     * <p>应用启动后自动执行，注册文件监听并启动监听线程。
     * 使用单线程线程池监听 Sentinel 规则文件变化，合理场景下手动创建线程池。
     */
    @SuppressWarnings("PMD.ThreadPoolCreationRule")
    @PostConstruct
    public void init() {
        String rulesFile = rulesLoader.getRulesFile();
        if (rulesFile == null || rulesFile.isEmpty()) {
            log.warn("Sentinel 规则文件未配置，跳过文件监听");
            return;
        }

        try {
            // 解析文件路径（支持 file: 前缀）
            String filePath = rulesFile.startsWith("file:") ? rulesFile.substring(5) : rulesFile;

            Path path = Paths.get(filePath);
            Path dir = path.getParent();
            String fileName = path.getFileName().toString();

            if (dir == null || !Files.exists(dir)) {
                log.error("Sentinel 规则文件目录不存在: {}", dir);
                return;
            }

            // 创建 WatchService
            watchService = FileSystems.getDefault().newWatchService();
            dir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE);

            // 启动监听线程
            executorService = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "sentinel-rule-watcher");
                t.setDaemon(true);
                return t;
            });

            running = true;
            executorService.submit(() -> watchLoop(fileName, filePath));

            log.info("Sentinel 规则文件监听已启动, 监听文件: {}", filePath);
        } catch (Exception e) {
            log.error("启动 Sentinel 规则文件监听失败", e);
        }
    }

    /**
     * 文件监听循环
     *
     * @param fileName 要监听的文件名
     * @param filePath 文件完整路径
     */
    private void watchLoop(String fileName, String filePath) {
        while (running) {
            try {
                // 等待文件系统事件
                WatchKey key = watchService.poll(1, TimeUnit.SECONDS);
                if (key == null) {
                    continue;
                }

                // 处理事件
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();

                    // 忽略 OVERFLOW 事件
                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }

                    // 获取文件名
                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path changedFile = ev.context();

                    // 只处理目标文件
                    if (changedFile.toString().equals(fileName)) {
                        log.info("检测到 Sentinel 规则文件变化: {}, 事件类型: {}", fileName, kind.name());

                        // 延迟 100ms 等待文件写入完成
                        Thread.sleep(100);

                        // 重新加载规则
                        reloadRules(filePath);
                    }
                }

                // 重置 WatchKey
                boolean valid = key.reset();
                if (!valid) {
                    log.warn("WatchKey 失效，停止监听");
                    break;
                }
            } catch (InterruptedException e) {
                log.info("Sentinel 规则文件监听线程被中断");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Sentinel 规则文件监听异常", e);
            }
        }
    }

    /**
     * 重新加载规则
     *
     * @param filePath 规则文件路径
     */
    private void reloadRules(String filePath) {
        try {
            // 记录重载前的规则数量
            int flowRulesBefore = FlowRuleManager.getRules().size();
            int degradeRulesBefore = DegradeRuleManager.getRules().size();

            // 重新加载
            rulesLoader.loadRulesFromFile(filePath);

            // 记录重载后的规则数量
            int flowRulesAfter = FlowRuleManager.getRules().size();
            int degradeRulesAfter = DegradeRuleManager.getRules().size();

            log.info(
                    "Sentinel 规则重载完成 - 流控规则: {} -> {}, 降级规则: {} -> {}",
                    flowRulesBefore,
                    flowRulesAfter,
                    degradeRulesBefore,
                    degradeRulesAfter);
        } catch (Exception e) {
            log.error("重新加载 Sentinel 规则失败，保持原有规则不变", e);
        }
    }

    /**
     * 停止文件监听
     *
     * <p>应用关闭时自动执行，释放资源。
     */
    @PreDestroy
    public void destroy() {
        running = false;

        if (executorService != null) {
            executorService.shutdownNow();
            try {
                if (!executorService.awaitTermination(SHUTDOWN_AWAIT_SECONDS, TimeUnit.SECONDS)) {
                    log.warn("Sentinel 规则监听线程未能在 {} 秒内停止", SHUTDOWN_AWAIT_SECONDS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                log.error("关闭 WatchService 失败", e);
            }
        }

        log.info("Sentinel 规则文件监听已停止");
    }
}
