package com.apex.scaffold.infrastructure.cache.task;

import com.apex.scaffold.infrastructure.cache.loader.BloomFilterCacheWarmer;
import com.apex.scaffold.infrastructure.cache.metrics.BloomFilterRebuildMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * BloomFilter 定时重建任务
 *
 * <p>定期重建 BloomFilter 以解决以下问题：
 * <ul>
 *   <li>清理已删除数据对应的 key，降低假阳性率</li>
 *   <li>适应数据量增长，避免超出预期容量导致误判率上升</li>
 *   <li>修复运行期间可能出现的数据不一致问题</li>
 * </ul>
 *
 * <h2>执行策略</h2>
 * <ul>
 *   <li><b>执行时间</b>：每天凌晨 3 点（业务低峰期）</li>
 *   <li><b>执行方式</b>：串行重建各命名空间，避免同时重建造成数据库压力</li>
 *   <li><b>容错处理</b>：单个命名空间重建失败不影响其他命名空间</li>
 *   <li><b>日志记录</b>：记录重建开始、完成和失败信息</li>
 * </ul>
 *
 * <h2>工作流程</h2>
 * <ol>
 *   <li>记录重建开始时间</li>
 *   <li>调用 BloomFilterCacheWarmer 重新加载数据</li>
 *   <li>计算并记录重建耗时</li>
 *   <li>失败时记录错误日志，不影响应用运行</li>
 * </ol>
 *
 * <h2>配置说明</h2>
 * <p>通过以下配置启用：
 * <pre>
 * apex:
 *   cache:
 *     bloom-filter:
 *       enabled: true
 *       rebuild:
 *         enabled: true
 *         cron: "0 0 3 * * ?"  # 每天凌晨 3 点
 * </pre>
 *
 * <h2>监控建议</h2>
 * <ul>
 *   <li>监控重建任务执行时间，异常耗时需告警</li>
 *   <li>监控重建成功率，连续失败需告警</li>
 *   <li>监控重建后的 BloomFilter 容量变化</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(BloomFilterCacheWarmer.class)
@ConditionalOnProperty(
        prefix = "apex.cache.bloom-filter.rebuild",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class BloomFilterRebuildTask {

    private final BloomFilterCacheWarmer bloomFilterCacheWarmer;
    private final BloomFilterRebuildMetrics rebuildMetrics;

    /**
     * 定时重建 BloomFilter
     *
     * <p>每天凌晨 3 点执行，重新从数据库加载所有有效数据到 BloomFilter。
     * 执行时间可通过配置项 {@code apex.cache.bloom-filter.rebuild.cron} 自定义。
     */
    @Scheduled(cron = "${apex.cache.bloom-filter.rebuild.cron:0 0 3 * * ?}")
    public void rebuildBloomFilters() {
        log.info("开始定时重建 BloomFilter...");
        long startTime = System.currentTimeMillis();

        try {
            // 重新执行预热逻辑，重建所有命名空间的 BloomFilter
            // BloomFilterCacheWarmer 内部会调用 createOrRebuild，自动清空旧数据
            bloomFilterCacheWarmer.run(null);

            long duration = System.currentTimeMillis() - startTime;
            log.info("BloomFilter 定时重建完成，耗时: {} ms", duration);
            rebuildMetrics.recordSuccess();
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("BloomFilter 定时重建失败，耗时: {} ms", duration, e);
            rebuildMetrics.recordFailure();
            // 不抛出异常，避免影响调度器继续执行
        }
    }

    /**
     * 手动触发重建（供运维接口调用）
     *
     * <p>提供手动触发入口，用于以下场景：
     * <ul>
     *   <li>数据库执行了大量删除操作，需要立即清理 BloomFilter</li>
     *   <li>发现 BloomFilter 误判率异常，需要立即重建</li>
     *   <li>新增了大量数据，需要扩容 BloomFilter</li>
     * </ul>
     *
     * <p>与定时任务不同，本方法直接执行重建并将结果返回给调用方，
     * 预热器异常不会向调用方抛出，而是转化为失败返回值。
     *
     * @return 重建是否成功
     */
    public boolean manualRebuild() {
        log.info("手动触发 BloomFilter 重建");
        long startTime = System.currentTimeMillis();
        try {
            // 重新执行预热逻辑，重建所有命名空间的 BloomFilter
            // BloomFilterCacheWarmer 内部会调用 createOrRebuild，自动清空旧数据
            bloomFilterCacheWarmer.run(null);

            long duration = System.currentTimeMillis() - startTime;
            log.info("BloomFilter 手动重建完成，耗时: {} ms", duration);
            rebuildMetrics.recordSuccess();
            return true;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("BloomFilter 手动重建失败，耗时: {} ms", duration, e);
            rebuildMetrics.recordFailure();
            return false;
        }
    }
}
