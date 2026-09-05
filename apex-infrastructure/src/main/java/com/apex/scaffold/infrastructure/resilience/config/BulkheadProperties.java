package com.apex.scaffold.infrastructure.resilience.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 分布式舱壁配置属性
 *
 * <p>管理分布式信号量隔离舱的并发控制参数。
 *
 * @author Apex Team
 * @version 1.3.0
 * @since 1.3.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "apex.bulkhead")
public class BulkheadProperties {

    /**
     * 是否启用分布式舱壁（默认 false，Enterprise 版本建议 true）
     *
     * <p>启用后，通过 Redisson 分布式信号量实现跨实例的并发控制。
     */
    private boolean enabled = false;

    /**
     * 最大并发调用数（默认 25）
     *
     * <p>所有实例共享此上限。超限请求会快速失败，不会排队等待。
     */
    private int maxConcurrentCalls = 25;

    /**
     * 许可证超时时间（毫秒，默认 60000 = 1 分钟）
     *
     * <p>防止许可证泄漏。如果操作在此时间内未完成，许可证会自动释放。
     */
    private long permitTimeoutMs = 60000;
}
