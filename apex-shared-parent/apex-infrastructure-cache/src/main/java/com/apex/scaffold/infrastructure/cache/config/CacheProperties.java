package com.apex.scaffold.infrastructure.cache.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 缓存配置属性
 *
 * <p>管理多级缓存的高级特性开关，包括故障降级、预热、监控等。
 *
 * @author Apex Team
 * @version 1.3.0
 * @since 1.3.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "apex.cache")
public class CacheProperties {

    /**
     * 读降级配置
     */
    private ReadDegradation readDegradation = new ReadDegradation();

    /**
     * 布隆过滤器配置
     *
     * <p>用于缓存穿透防护，前置判断 key 是否可能存在，避免无效请求打到数据库。
     */
    private BloomFilter bloomFilter = new BloomFilter();

    /**
     * 缓存预热配置
     *
     * <p>应用启动后按策略预热热点数据到多级缓存。
     */
    private Prewarm prewarm = new Prewarm();

    /**
     * 延迟双删配置
     *
     * <p>用于解决并发读写导致的缓存脏数据问题（Cache-Aside 模式的经典缺陷）：
     * 线程 A 读 DB 旧值后、写回缓存前，线程 B 更新 DB 并删除缓存，
     * 导致线程 A 将旧值写回缓存，形成脏数据。
     *
     * <p>延迟双删策略：更新 DB 后立即删除缓存 → 延迟一段时间 → 再删除一次缓存，
     * 以覆盖延迟到达的旧值写回请求。当禁用时退化为单次删除（仅立即删除）。
     */
    private DoubleDelete doubleDelete = new DoubleDelete();

    /**
     * DB 故障读降级配置（Enterprise License 特性）
     *
     * <p>当数据库不可用时，允许从缓存（L1/L2）读取过期数据作为降级兜底，
     * 避免服务完全不可用。适用于对数据一致性要求不严格、可用性优先的场景。
     */
    @Data
    public static class ReadDegradation {

        /**
         * 是否启用读降级（默认 false，Enterprise 版本建议 true）
         *
         * <p>启用后，当 DB 查询失败且缓存未命中时，会尝试从过期缓存中读取旧数据返回。
         */
        private boolean enabled = false;

        /**
         * 降级时允许返回的最大过期时间（默认 1 小时）
         *
         * <p>只有在此时间窗口内过期的缓存才会被降级返回，避免返回过于陈旧的数据。
         */
        private long maxStaleSeconds = 3600;
    }

    /**
     * 布隆过滤器配置项
     *
     * <p>基于 Redisson RBloomFilter 实现的缓存穿透防护参数。
     */
    @Data
    public static class BloomFilter {

        /**
         * 预期插入数量
         *
         * <p>布隆过滤器预计存放的元素总数，影响位数组大小。
         * 设置过小会提高误判率，过大则占用更多 Redis 内存。
         */
        private long expectedInsertions = 1_000_000L;

        /**
         * 期望误判率（False Positive Probability）
         *
         * <p>取值范围 (0, 1)，越低误判越少但内存占用越高。常用 0.01（1%）。
         */
        private double fpp = 0.01;

        /**
         * Redis 键前缀
         *
         * <p>布隆过滤器在 Redis 中的键前缀，实际键为 {@code prefix + filterName}。
         * 必须包含租户或业务维度隔离标识时，由服务层自行拼接。
         */
        private String redisKeyPrefix = "apex:cache:bloom:";
    }

    /**
     * 缓存预热配置项
     *
     * <p>控制应用启动后的缓存预热行为（{@code apex.cache.prewarm.*}）。
     */
    @Data
    public static class Prewarm {

        /**
         * 是否启用缓存预热（默认 false）
         *
         * <p>启用后，应用启动完成将按注册的预热策略加载数据到缓存。
         */
        private boolean enabled = false;

        /**
         * 预热启动延迟（秒）
         *
         * <p>应用启动完成后延迟指定秒数再执行预热，避开启动高峰期，
         * 确保基础组件（数据源、Redis 连接）已就绪。
         */
        private long startupDelaySeconds = 30;
    }

    /**
     * 延迟双删配置项
     *
     * <p>控制并发读写场景下的一致性保障行为（{@code apex.cache.double-delete.*}）。
     */
    @Data
    public static class DoubleDelete {

        /**
         * 是否启用延迟双删（默认 true）
         *
         * <p>启用后，删除缓存将在「立即删除 → 延迟 → 再删除」两阶段执行，
         * 以覆盖并发场景下延迟写回的旧值。禁用后仅执行一次立即删除。
         */
        private boolean enabled = true;

        /**
         * 第二次删除的延迟时间（毫秒，默认 500ms）
         *
         * <p>延迟时间应略大于「一次数据库读 + 写回缓存」的耗时，
         * 通常与主从复制延迟或业务读耗时相当。
         */
        private long delayMillis = 500;

        /**
         * 每次删除的最大重试次数（默认 3）
         *
         * <p>删除失败时按此值重试；重试仍失败则记录日志，交由补偿机制兜底。
         */
        private int retryTimes = 3;
    }
}
