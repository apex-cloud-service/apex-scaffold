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
}
