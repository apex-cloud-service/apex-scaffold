package com.apex.scaffold.integration.device.mqtt;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MQTT 桥接配置（方案 M5 / 批次 E3）。
 *
 * <p>{@code apex.integration.iot.mqtt.*}；{@code enabled} 默认关闭，
 * 显式启用后由 {@code IotMqttAutoConfiguration} 装配会话（classpath 缺少
 * HiveMQ 客户端时自动裁剪为零 Bean）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Data
@ConfigurationProperties(prefix = "apex.integration.iot.mqtt")
public class IotMqttProperties {

    /** 是否启用 MQTT 桥接（默认关闭）。 */
    private boolean enabled = false;

    /** broker 地址（tcp://host:port）。 */
    private String brokerUrl = "tcp://127.0.0.1:1883";

    /** 客户端 ID（空则自动生成，避免多实例冲突）。 */
    private String clientId;

    /** broker 用户名（可选）。 */
    private String username;

    /** broker 密码（可选）。 */
    private String password;

    /** 共享订阅组（水平扩展时同组实例均分上行消息）。 */
    private String sharedSubscriptionGroup = "apex-integration";

    /** keepAlive 间隔（秒）。 */
    private int keepAliveSeconds = 30;
}
