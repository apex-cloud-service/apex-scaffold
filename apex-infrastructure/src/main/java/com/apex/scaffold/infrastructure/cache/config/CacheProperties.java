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
}
