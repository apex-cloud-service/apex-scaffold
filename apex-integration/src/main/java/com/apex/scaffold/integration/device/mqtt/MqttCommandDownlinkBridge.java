package com.apex.scaffold.integration.device.mqtt;

import com.apex.scaffold.integration.device.domain.DeviceCommand;
import com.apex.scaffold.integration.device.domain.MqttTopicPolicy;
import com.apex.scaffold.integration.device.domain.service.CommandDownlinkPort;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * 命令下行 MQTT 桥（方案 M5 / 批次 E3）。
 *
 * <p>实现 {@link CommandDownlinkPort}：将命令载荷发布到
 * {@code apex/{tenantCode}/{deviceKey}/down/{commandId}}（QoS1）。
 * 会话未就绪或发布失败时返回 {@code false}，由用例层保持命令 PENDING、
 * 超时扫描兜底，不向上抛错。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
@RequiredArgsConstructor
public class MqttCommandDownlinkBridge implements CommandDownlinkPort {

    private final IotMqttSession session;

    @Override
    public Mono<Boolean> publish(DeviceCommand command) {
        String topic = MqttTopicPolicy.downTopic(command.getTenantId(), command.getDeviceKey(), command.getCommandId());
        String payload = command.getPayload() == null || command.getPayload().isBlank() ? "{}" : command.getPayload();
        return session.publish(topic, payload.getBytes(StandardCharsets.UTF_8)).doOnNext(published -> {
            if (!published) {
                log.warn(
                        "命令下行发布未成功（保持 PENDING）: tenant={}, commandId={}, topic={}",
                        command.getTenantId(),
                        command.getCommandId(),
                        topic);
            }
        });
    }
}
