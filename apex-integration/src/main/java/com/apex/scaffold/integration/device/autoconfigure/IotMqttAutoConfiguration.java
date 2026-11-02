package com.apex.scaffold.integration.device.autoconfigure;

import com.apex.scaffold.integration.device.application.DeviceTelemetryService;
import com.apex.scaffold.integration.device.domain.service.CommandDownlinkPort;
import com.apex.scaffold.integration.device.mqtt.IotMqttProperties;
import com.apex.scaffold.integration.device.mqtt.IotMqttSession;
import com.apex.scaffold.integration.device.mqtt.MqttCommandDownlinkBridge;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * MQTT 桥接自动装配（方案 M5 / 批次 E3，optional 裁剪）。
 *
 * <p>HiveMQ 客户端为 optional 依赖：classpath 缺失时 {@code @ConditionalOnClass}
 * 使本装配整体跳过（零 Bean），模块其余能力不受影响。启用条件：
 * classpath 存在客户端且 {@code apex.integration.iot.mqtt.enabled=true}（默认关闭）。
 *
 * <p>装配产物：MQTT 会话（{@link IotMqttSession}，SmartLifecycle 管理连接与上行
 * 共享订阅）与命令下行端口（{@link MqttCommandDownlinkBridge}，发布到设备下行 topic）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@AutoConfiguration
@ConditionalOnClass(Mqtt3AsyncClient.class)
@ConditionalOnProperty(prefix = "apex.integration.iot.mqtt", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(IotMqttProperties.class)
public class IotMqttAutoConfiguration {

    /**
     * MQTT 会话（连接、自动重连与上行共享订阅）。
     *
     * @param properties       桥接配置
     * @param telemetryService 遥测受理用例
     * @return 会话
     */
    @Bean
    public IotMqttSession iotMqttSession(IotMqttProperties properties, DeviceTelemetryService telemetryService) {
        return new IotMqttSession(properties, telemetryService);
    }

    /**
     * 命令下行端口（供 {@code DeviceCommandService} 经 ObjectProvider 发现）。
     *
     * @param session MQTT 会话
     * @return 下行端口实现
     */
    @Bean
    public CommandDownlinkPort mqttCommandDownlinkPort(IotMqttSession session) {
        return new MqttCommandDownlinkBridge(session);
    }
}
