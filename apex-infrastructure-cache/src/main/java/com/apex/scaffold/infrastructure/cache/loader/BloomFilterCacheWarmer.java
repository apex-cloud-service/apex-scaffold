package com.apex.scaffold.infrastructure.cache.loader;

import com.apex.scaffold.infrastructure.cache.filter.BloomFilterService;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 布隆过滤器缓存预热器
 *
 * <p>在应用启动时异步加载数据库中的所有有效 key 到 BloomFilter，实现缓存穿透防护的数据预热。
 * 支持多个业务域（用户、订单、商品等）的独立预热。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>异步预热：避免阻塞应用启动，启动后台线程加载数据</li>
 *   <li>多命名空间：支持同时预热多个业务域的 BloomFilter</li>
 *   <li>批量加载：减少数据库查询次数，提升预热效率</li>
 *   <li>启动日志：记录预热进度和结果，便于运维监控</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <pre>
 * 1. 用户域（user）：加载所有 user_id
 * 2. 媒体域（media）：加载所有 media_id
 * 3. 其他业务域：根据业务需求扩展
 * </pre>
 *
 * <h2>设计说明</h2>
 * <p>通过以下配置启用：
 * <ul>
 *   <li>{@code apex.cache.bloom-filter.enabled=true}：启用布隆过滤器</li>
 *   <li>{@code apex.cache.bloom-filter.warm-on-startup=true}：启用启动预热</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>预热时间：100万条数据约需 2-5 秒</li>
 *   <li>内存占用：100万元素、1%误判率约 1.2MB</li>
 *   <li>数据库压力：单次全表扫描，建议在业务低峰期执行</li>
 * </ul>
 *
 * <p>确保在缓存预热之前执行。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(BloomFilterService.class)
@ConditionalOnProperty(prefix = "apex.cache.bloom-filter", name = "enabled", havingValue = "true")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BloomFilterCacheWarmer implements ApplicationRunner {

    private final BloomFilterService bloomFilterService;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 应用启动后执行预热
     *
     * @param args 应用参数
     */
    @Override
    public void run(ApplicationArguments args) {
        log.info("开始 BloomFilter 缓存预热...");
        long startTime = System.currentTimeMillis();

        // 异步预热，避免阻塞启动
        CompletableFuture.runAsync(() -> {
            try {
                warmUserKeys();
                warmMediaKeys();

                long duration = System.currentTimeMillis() - startTime;
                log.info("BloomFilter 缓存预热完成，耗时: {} ms", duration);
            } catch (Exception e) {
                log.error("BloomFilter 缓存预热失败", e);
            }
        });
    }

    /**
     * 预热用户域 BloomFilter
     */
    private void warmUserKeys() {
        try {
            log.info("开始预热用户域 BloomFilter...");
            long start = System.currentTimeMillis();

            // 查询所有有效用户 ID
            String sql = "SELECT id FROM t_user WHERE deleted = 0";
            List<Long> userIds = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong("id"));

            // 创建或重建过滤器（容量为实际数据量的 1.3 倍，预留增长空间）
            int expectedSize = (int) (userIds.size() * 1.3);
            bloomFilterService.createOrRebuild("user", expectedSize, 0.01);

            // 批量添加到过滤器
            bloomFilterService.putAll(
                    "user", userIds.stream().map(String::valueOf).toList());

            long duration = System.currentTimeMillis() - start;
            log.info("用户域 BloomFilter 预热完成，数据量: {}，耗时: {} ms", userIds.size(), duration);
        } catch (Exception e) {
            log.error("用户域 BloomFilter 预热失败", e);
        }
    }

    /**
     * 预热媒体域 BloomFilter
     */
    private void warmMediaKeys() {
        try {
            log.info("开始预热媒体域 BloomFilter...");
            long start = System.currentTimeMillis();

            String sql = "SELECT id FROM t_media WHERE deleted = 0";
            List<Long> mediaIds = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong("id"));

            int expectedSize = (int) (mediaIds.size() * 1.3);
            // 1.3 为容量预留系数，0.01 为期望误判率
            bloomFilterService.createOrRebuild("media", expectedSize, 0.01);

            bloomFilterService.putAll(
                    "media", mediaIds.stream().map(String::valueOf).toList());

            long duration = System.currentTimeMillis() - start;
            log.info("媒体域 BloomFilter 预热完成，数据量: {}，耗时: {} ms", mediaIds.size(), duration);
        } catch (Exception e) {
            log.error("媒体域 BloomFilter 预热失败", e);
        }
    }
}
