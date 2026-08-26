package com.apex.scaffold.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 投票候选派生索引配置。
 *
 * <p>将 YAML 中 {@code apex.vote-candidate-index.*} 配置映射为 Java 对象，供投票候选池
 * 构建（批量落库、世代刷新）与近期曝光索引（Redis 查询缓存、ZSET 去重、TTL 与容量控制）
 * 在运行时读取开关与参数。</p>
 *
 * <p>配置路径示例：
 * <pre>{@code * apex:
 *   vote-candidate-index:
 *     candidate-build-enabled: true
 *     candidate-read-enabled: true
 *     candidate-shadow-enabled: true
 *     recent-exposure-enabled: false
 *     generation-ttl-hours: 24
 *     query-cache-ttl-seconds: 300
 *     max-recent-entries: 200
 *     recent-exposure-ttl-days: 30
 * }</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.1.0
 */
@ConfigurationProperties(prefix = "apex.vote-candidate-index")
@Data
public class VoteCandidateIndexProperties {

    /** 候选池批量构建开关；关闭时不再刷新候选世代。 */
    private boolean candidateBuildEnabled = false;

    /** 正式候选读开关；关闭时候选查询直接回源 MySQL。 */
    private boolean candidateReadEnabled = false;

    /** Shadow 模式开关；开启时仅记录差异、不改写 MySQL 正式结果。 */
    private boolean candidateShadowEnabled = false;

    /** 近期曝光索引总开关；关闭时查询与写入均直接短路。 */
    private boolean recentExposureEnabled = false;

    /** 候选世代缓存 TTL（小时）；控制候选集合重建频率。 */
    private int generationTtlHours = 24;

    /** 近期曝光查询结果缓存 TTL（秒）；与业务冷却窗口取较小值。 */
    private int queryCacheTtlSeconds = 300;

    /** 近期曝光 ZSET 保留的最大条目数；超出后裁剪最早成员。 */
    private int maxRecentEntries = 200;

    /** 近期曝光索引 Key 的 TTL（天）。 */
    private int recentExposureTtlDays = 30;
}
