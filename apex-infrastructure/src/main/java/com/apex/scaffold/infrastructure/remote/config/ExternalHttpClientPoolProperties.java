package com.apex.scaffold.infrastructure.remote.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 外部 HTTP 客户端连接池配置属性。
 *
 * <p>将 YAML 中 {@code remote.api.pool-*} 配置映射为 Java 对象。
 * 在 Spring 容器启动后通过 {@link ManagedWebClientFactory} 的 {@code initialize} 方法注入。
 *
 * <p>配置路径示例：
 * <pre>{@code * remote:
 *   api:
 *     pool-max-connections: 100
 *     pool-pending-acquire-max-count: 500
 *     pool-max-idle-time-seconds: 30
 *     pool-max-life-time-minutes: 5
 * }</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.1.0
 */
@ConfigurationProperties(prefix = "remote.api")
@Validated
@Data
public class ExternalHttpClientPoolProperties {

    /** 连接池最大连接数，默认 100 */
    private int poolMaxConnections = 100;

    /** 等待获取连接的最大数量，默认 500 */
    private int poolPendingAcquireMaxCount = 500;

    /** 连接最大空闲时间（秒），默认 30 */
    private int poolMaxIdleTimeSeconds = 30;

    /** 连接最大生命周期（分钟），默认 5 */
    private int poolMaxLifeTimeMinutes = 5;

    /** IO 读写超时（秒），默认 10 */
    private int ioTimeoutSeconds = 10;

    /** 连接建立超时（毫秒），默认 5000 */
    private int connectTimeoutMillis = 5000;
}
