package com.apex.scaffold.application.job;

import com.apex.scaffold.domain.search.IndexSyncPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 搜索索引校准任务
 *
 * <p>定时执行增量同步，确保搜索索引与数据库保持一致。
 *
 * <h2>执行时机</h2>
 * <ul>
 *   <li>每天凌晨 2 点自动执行（cron: 0 0 2 * * ?）</li>
 *   <li>同步最近 24 小时内更新的资源</li>
 * </ul>
 *
 * <h2>同步策略</h2>
 * <ul>
 *   <li>遍历所有资源类型（user/role/menu/dept/dict/config）</li>
 *   <li>查询 updated_at > (当前时间 - 24小时) 的资源ID</li>
 *   <li>批量重新索引这些资源</li>
 * </ul>
 *
 * <h2>配置项</h2>
 * <ul>
 *   <li>启用：{@code meilisearch.enabled=true}</li>
 *   <li>禁用：{@code meilisearch.enabled=false} 或不配置</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "meilisearch", name = "enabled", havingValue = "true")
public class SearchIndexReconciliationJob {

    private final IndexSyncPort indexSyncPort;

    /**
     * 支持的资源类型
     */
    private static final String[] RESOURCE_TYPES = {"user", "role", "menu", "dept", "dict", "config"};

    /**
     * 24小时（毫秒）
     */
    private static final long TWENTY_FOUR_HOURS = 24 * 3600 * 1000;

    /**
     * 执行增量同步
     *
     * <p>每天凌晨 2 点自动执行，同步最近 24 小时内更新的资源。
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void syncIncremental() {
        log.info("Starting incremental index synchronization...");

        long startTime = System.currentTimeMillis();
        long sinceTimestamp = startTime - TWENTY_FOUR_HOURS;
        int totalCount = 0;

        for (String resourceType : RESOURCE_TYPES) {
            try {
                Long count = indexSyncPort.syncIncremental(resourceType, sinceTimestamp)
                        .doOnSuccess(c -> log.info("Incremental sync completed: type={}, count={}", resourceType, c))
                        .doOnError(e -> log.error("Incremental sync failed: type={}", resourceType, e))
                        .onErrorReturn(0L)  // 单个类型失败不影响其他类型
                        .block();  // 阻塞等待完成

                if (count != null) {
                    totalCount += count;
                }
            } catch (Exception e) {
                log.error("Incremental sync error: type={}", resourceType, e);
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("Incremental index synchronization finished: total={}, duration={}ms", totalCount, duration);
    }

    /**
     * 手动触发增量同步
     *
     * <p>通过管理后台或 API 调用，用于测试或紧急修复。
     *
     * @param resourceType 资源类型，null表示同步所有类型
     */
    public void manualSync(String resourceType) {
        log.warn("Manual incremental sync triggered: type={}", resourceType);

        long sinceTimestamp = System.currentTimeMillis() - TWENTY_FOUR_HOURS;

        if (resourceType != null) {
            // 同步指定类型
            indexSyncPort.syncIncremental(resourceType, sinceTimestamp)
                    .doOnSuccess(count -> log.info("Manual sync completed: type={}, count={}", resourceType, count))
                    .doOnError(e -> log.error("Manual sync failed: type={}", resourceType, e))
                    .subscribe();
        } else {
            // 同步所有类型
            syncIncremental();
        }
    }
}
