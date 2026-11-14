package com.apex.scaffold.integration.device.mqtt;

import com.apex.scaffold.integration.device.application.DeviceTelemetryService;
import com.apex.scaffold.integration.device.domain.MqttTopicPolicy;
import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import reactor.core.publisher.Mono;

/**
 * MQTT 会话（方案 M5 / 批次 E3，optional 桥接）。
 *
 * <p>SmartLifecycle 管理生命周期：启动时建立连接并按共享订阅组消费上行遥测
 * （{@code $share/{group}/apex/+/+/up/#}），断线由客户端自动重连（重连后自动恢复订阅），
 * 启动不因 broker 暂不可达而失败；停止时显式断开。
 *
 * <p>上行分发：按 topic 段解析租户与设备标识（{@code apex/{tenant}/{device}/up[/...]}），
 * 载荷整体作为遥测指标 JSON 交由 {@link DeviceTelemetryService#ingest} 受理；
 * 受理失败仅记录告警（QoS1 语义下由 broker 重投策略兜底，此处不放大重试）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
public class IotMqttSession implements SmartLifecycle {

    private final IotMqttProperties properties;

    private final DeviceTelemetryService telemetryService;

    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile Mqtt3AsyncClient client;

    /**
     * 构造 MQTT 会话。
     *
     * @param properties       桥接配置
     * @param telemetryService 遥测受理用例
     */
    public IotMqttSession(IotMqttProperties properties, DeviceTelemetryService telemetryService) {
        this.properties = properties;
        this.telemetryService = telemetryService;
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        Mqtt3AsyncClient built = buildClient();
        this.client = built;
        var connectBuilder =
                built.connectWith().keepAlive(properties.getKeepAliveSeconds()).cleanSession(true);
        if (properties.getUsername() != null && !properties.getUsername().isBlank()) {
            connectBuilder = connectBuilder
                    .simpleAuth()
                    .username(properties.getUsername())
                    .password((properties.getPassword() == null ? "" : properties.getPassword())
                            .getBytes(StandardCharsets.UTF_8))
                    .applySimpleAuth();
        }
        connectBuilder.send().whenComplete((ack, error) -> {
            if (error != null) {
                log.error("MQTT broker 连接失败（自动重连将持续重试）: {}", properties.getBrokerUrl(), error);
                return;
            }
            log.info("MQTT broker 已连接: {}", properties.getBrokerUrl());
            subscribeUpstream();
        });
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        Mqtt3AsyncClient current = this.client;
        this.client = null;
        if (current == null) {
            return;
        }
        try {
            current.disconnect().get(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("MQTT 断开异常（忽略）", e);
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    /**
     * 向指定 topic 发布载荷（QoS1）。
     *
     * @param topic   目标 topic
     * @param payload 载荷字节
     * @return 会话就绪且发布成功为 true；未就绪或失败为 false
     */
    public Mono<Boolean> publish(String topic, byte[] payload) {
        Mqtt3AsyncClient current = this.client;
        if (!running.get() || current == null) {
            return Mono.just(false);
        }
        return Mono.fromFuture(() -> current.publishWith()
                        .topic(topic)
                        .payload(payload)
                        .qos(MqttQos.AT_LEAST_ONCE)
                        .send()
                        .thenApply(result -> true))
                .onErrorResume(error -> {
                    log.warn("MQTT 发布失败: topic={}", topic, error);
                    return Mono.just(false);
                });
    }

    private Mqtt3AsyncClient buildClient() {
        URI uri = parseBrokerUrl(properties.getBrokerUrl());
        String clientId =
                properties.getClientId() == null || properties.getClientId().isBlank()
                        ? "apex-integration-" + UUID.randomUUID()
                        : properties.getClientId();
        return MqttClient.builder()
                .useMqttVersion3()
                .identifier(clientId)
                .serverHost(uri.getHost())
                .serverPort(uri.getPort() == -1 ? 1883 : uri.getPort())
                .automaticReconnectWithDefaultConfig()
                .buildAsync();
    }

    private URI parseBrokerUrl(String brokerUrl) {
        URI uri = URI.create(brokerUrl);
        String scheme = uri.getScheme();
        if (!"tcp".equalsIgnoreCase(scheme) && !"ssl".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("MQTT broker 地址仅支持 tcp:// 或 ssl://: " + brokerUrl);
        }
        return uri;
    }

    private void subscribeUpstream() {
        String filter = MqttTopicPolicy.sharedUpFilter(properties.getSharedSubscriptionGroup());
        client.subscribeWith()
                .topicFilter(filter)
                .qos(MqttQos.AT_LEAST_ONCE)
                .callback(this::onUpstreamPublish)
                .send()
                .whenComplete((subAck, error) -> {
                    if (error != null) {
                        log.error("MQTT 上行共享订阅失败: {}", filter, error);
                    } else {
                        log.info("MQTT 上行共享订阅已建立: {}", filter);
                    }
                });
    }

    /** 上行消息分发（共享订阅前缀已被 broker 剥离，收到的为原始 topic）。 */
    private void onUpstreamPublish(Mqtt3Publish publish) {
        String topic = publish.getTopic().toString();
        String[] segments = topic.split("/");
        if (segments.length < 4 || !"apex".equals(segments[0]) || !"up".equals(segments[3])) {
            log.warn("忽略命名空间外的上行 topic: {}", topic);
            return;
        }
        String tenant = segments[1];
        String deviceKey = segments[2];
        String metric = new String(publish.getPayloadAsBytes(), StandardCharsets.UTF_8);
        telemetryService
                .ingest(tenant, deviceKey, List.of(new DeviceTelemetryService.TelemetryPoint(metric, null)))
                .doOnSuccess(ack -> log.debug(
                        "上行遥测已受理: topic={}, count={}, shadowVersion={}",
                        topic,
                        ack.acceptedCount(),
                        ack.shadowVersion()))
                .doOnError(error -> log.warn("上行遥测受理失败: topic={}", topic, error))
                .subscribe();
    }
}
