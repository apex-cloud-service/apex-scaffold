package com.apex.scaffold.infrastructure.cache.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RKeys;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时 Redis 旧格式数据清理器。
 *
 * <p>当 Redisson 序列化 Codec 发生变更时（如从默认 JsonJacksonCodec 迁移到 StringCodec），
 * Redis 中已存在的旧格式数据无法被新 Codec 正确读取，导致 Token 刷新失败等异常。
 * 本 Runner 在应用启动后自动扫描并清理受影响的 Key，避免手动清 Redis 的操作负担。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>{@code token:access:*} — AccessToken 存储</li>
 *   <li>{@code token:refresh:*} — RefreshToken 存储</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>通过配置项 {@code apex.redis.cleanup-on-startup} 控制开关，生产环境建议关闭</li>
 *   <li>每次启动最多执行一次清理（通过 {@code apex:data:cleanup:v2} 标记 Key 防重）</li>
 *   <li>清理操作通过 SCAN 分批执行，避免阻塞 Redis</li>
 *   <li>单次 SCAN 上限 1000 个 Key，清理失败仅记录日志不影响启动</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Component
public class RedisDataCleanupRunner implements ApplicationRunner {

    /** 清理完成标记 Key，避免重复执行 */
    private static final String CLEANUP_MARKER_KEY = "apex:data:cleanup:v2";

    /** 单次 SCAN 返回 Key 数量上限 */
    private static final int SCAN_COUNT = 200;

    /** 需要清理的 Key 前缀列表 */
    private static final String[] CLEANUP_KEY_PATTERNS = {"token:access:*", "token:refresh:*"};

    private final RedissonClient redissonClient;

    /** 是否在启动时执行清理，默认 true */
    private final boolean cleanupOnStartup;

    /**
     * 构造启动清理器。
     *
     * @param redissonClient Redisson 客户端
     * @param cleanupOnStartup 是否在启动时执行清理，默认 true
     */
    public RedisDataCleanupRunner(
            RedissonClient redissonClient, @Value("${apex.redis.cleanup-on-startup:true}") boolean cleanupOnStartup) {
        this.redissonClient = redissonClient;
        this.cleanupOnStartup = cleanupOnStartup;
    }

    /**
     * 应用启动后执行 Redis 旧格式数据清理。
     *
     * <p>受 {@code apex.redis.cleanup-on-startup} 开关控制，并通过标记 Key 保证
     * 每次启动仅执行一次。</p>
     *
     * @param args 应用启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        if (!cleanupOnStartup) {
            log.info("Redis 数据清理已禁用 (apex.redis.cleanup-on-startup=false)，跳过清理");
            return;
        }

        try {
            if (redissonClient.getBucket(CLEANUP_MARKER_KEY).get() != null) {
                log.info("Redis 数据清理已执行过（标记 Key: {}），跳过重复清理", CLEANUP_MARKER_KEY);
                return;
            }

            log.info("开始执行 Redis 旧格式数据清理...");
            int totalDeleted = 0;
            RKeys rKeys = redissonClient.getKeys();

            for (String pattern : CLEANUP_KEY_PATTERNS) {
                int patternDeleted = deleteKeysByPattern(rKeys, pattern);
                totalDeleted += patternDeleted;
                log.info("已清理 pattern={}, 删除 {} 个 Key", pattern, patternDeleted);
            }

            // 设置清理完成标记（7 天过期，避免永久占用）
            redissonClient.getBucket(CLEANUP_MARKER_KEY).set(String.valueOf(System.currentTimeMillis()));
            redissonClient.getBucket(CLEANUP_MARKER_KEY).expire(Duration.ofDays(7));

            log.info("Redis 数据清理完成，共删除 {} 个旧格式 Key", totalDeleted);
        } catch (Exception e) {
            log.error("Redis 数据清理执行失败，不影响应用启动", e);
        }
    }

    /**
     * 按模式扫描并删除 Key。
     *
     * <p>通过 SCAN 命令分批遍历，避免 KEYS 命令阻塞 Redis。
     * 使用 {@code SCAN_COUNT=200} 在内存与网络开销间取得平衡。</p>
     *
     * @param rKeys   Redisson Keys 接口
     * @param pattern Key 匹配模式（如 {@code token:access:*})
     * @return 实际删除的 Key 数量
     */
    private int deleteKeysByPattern(RKeys rKeys, String pattern) {
        int deleted = 0;
        List<String> batch = new ArrayList<>(SCAN_COUNT);

        for (String key : rKeys.getKeysByPattern(pattern, SCAN_COUNT)) {
            batch.add(key);
            if (batch.size() >= SCAN_COUNT) {
                deleted += rKeys.delete(batch.toArray(new String[0]));
                batch.clear();
            }
        }

        if (!batch.isEmpty()) {
            deleted += rKeys.delete(batch.toArray(new String[0]));
        }

        return deleted;
    }
}
