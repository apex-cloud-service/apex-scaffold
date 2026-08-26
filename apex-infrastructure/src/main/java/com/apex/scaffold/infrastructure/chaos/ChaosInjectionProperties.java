package com.apex.scaffold.infrastructure.chaos;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 混沌工程注入配置属性类。
 *
 * <p>通过 {@code apex.chaos} 前缀绑定配置，控制各类故障注入的开关、持续时间和参数。
 * 所有注入默认关闭，仅在接受到显式开启指令时激活，确保生产环境安全。
 *
 * <h2>配置示例</h2>
 * <pre>{@code * apex:
 *   chaos:
 *     enabled: false
 *     redis:
 *       disconnect-enabled: false
 *       disconnect-duration-seconds: 30
 *     database:
 *       latency-enabled: false
 *       latency-millis: 2000
 *     remote-api:
 *       failure-rate-enabled: false
 *       failure-rate: 0.5
 * }</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "apex.chaos")
public class ChaosInjectionProperties {

    /** 全局混沌注入总开关，关闭时所有子开关均不生效 */
    private boolean enabled = false;

    /** Redis 故障注入配置 */
    private RedisChaos redis = new RedisChaos();

    /** 数据库故障注入配置 */
    private DatabaseChaos database = new DatabaseChaos();

    /** 外部 API 故障注入配置 */
    private RemoteApiChaos remoteApi = new RemoteApiChaos();

    /**
     * Redis 故障注入配置。
     *
     * <p>用于模拟 Redis 断连场景，验证缓存降级逻辑是否生效。
     *
     * @author Apex Team
     */
    @Data
    public static class RedisChaos {

        /** 是否启用 Redis 断连注入 */
        private boolean disconnectEnabled = false;

        /** Redis 断连持续时间（秒），超时后自动恢复 */
        private int disconnectDurationSeconds = 30;
    }

    /**
     * 数据库故障注入配置。
     *
     * <p>用于模拟数据库延迟场景，验证慢查询降级和连接池超时处理。
     *
     * @author Apex Team
     */
    @Data
    public static class DatabaseChaos {

        /** 是否启用数据库延迟注入 */
        private boolean latencyEnabled = false;

        /** 注入的延迟时间（毫秒） */
        private long latencyMillis = 2000;
    }

    /**
     * 外部 API 故障注入配置。
     *
     * <p>用于模拟外部 API 部分失败场景，验证熔断器和重试机制。
     *
     * @author Apex Team
     */
    @Data
    public static class RemoteApiChaos {

        /** 是否启用外部 API 失败率注入 */
        private boolean failureRateEnabled = false;

        /** 失败率（0.0 ~ 1.0），0.5 表示 50% 请求失败 */
        private double failureRate = 0.5;
    }
}
