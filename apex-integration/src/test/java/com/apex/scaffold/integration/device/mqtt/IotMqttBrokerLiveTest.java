package com.apex.scaffold.integration.device.mqtt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.apex.scaffold.integration.device.domain.MqttTopicPolicy;
import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * EMQX 真实 broker 端到端测试（方案 M5 / 批次 E3 未验证项收口）。
 *
 * <p>对真实 MQTT broker（默认 {@code 127.0.0.1:1883}，可用 {@code -Dapex.mqtt.host/port}
 * 覆盖）验证：设备上行发布 → 订阅方接收的完整往返、租户 topic 命名空间隔离、
 * 共享订阅消费上行。
 *
 * <p>采用<b>异步客户端 + publishes 流</b>（而非阻塞客户端 callback），
 * 与生产侧 {@code IotMqttSession} 的订阅方式一致。
 *
 * <p><b>可跳过设计</b>：broker 不可达时 {@code assumeTrue} 跳过而非失败，
 * 符合"dev/测试档可内嵌或跳过"口径。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@DisplayName("EMQX 真实 broker 端到端测试")
class IotMqttBrokerLiveTest {

    private static final String HOST = System.getProperty("apex.mqtt.host", "127.0.0.1");

    private static final int PORT = Integer.getInteger("apex.mqtt.port", 1883);

    private Mqtt5AsyncClient deviceClient;

    private Mqtt5AsyncClient subscriberClient;

    @BeforeEach
    void checkBrokerAvailable() {
        boolean reachable;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(HOST, PORT), 2000);
            reachable = true;
        } catch (Exception unreachable) {
            reachable = false;
        }
        assumeTrue(
                reachable,
                "MQTT broker 不可达（" + HOST + ":" + PORT + "），跳过真实 broker 测试；"
                        + "本地可执行 docker run -p 1883:1883 emqx/emqx:5.8 后重跑");
    }

    @AfterEach
    void disconnect() {
        if (deviceClient != null) {
            try {
                deviceClient.disconnect().get(5, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                // 清理失败不影响测试结论
            }
        }
        if (subscriberClient != null) {
            try {
                subscriberClient.disconnect().get(5, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                // 清理失败不影响测试结论
            }
        }
    }

    private Mqtt5AsyncClient newClient(String prefix) {
        return MqttClient.builder()
                .useMqttVersion5()
                .identifier(prefix + "-" + UUID.randomUUID())
                .serverHost(HOST)
                .serverPort(PORT)
                .buildAsync();
    }

    @Test
    @DisplayName("设备上行 → 订阅方收到完整载荷（真实往返）")
    void devicePublishesTelemetryAndSubscriberReceives() throws Exception {
        String tenant = "tenant-live-a";
        String device = "device-live-001";
        String upTopic = MqttTopicPolicy.upTopic(tenant, device);
        String payload = "{\"temperature\":23.5,\"humidity\":61}";

        subscriberClient = newClient("subscriber");
        subscriberClient.connect().get(10, TimeUnit.SECONDS);
        AtomicReference<String> received = new AtomicReference<>();
        subscriberClient
                .subscribeWith()
                .topicFilter(upTopic)
                .qos(MqttQos.AT_LEAST_ONCE)
                .send()
                .get(10, TimeUnit.SECONDS);
        subscriberClient.publishes(
                com.hivemq.client.mqtt.MqttGlobalPublishFilter.ALL,
                publish -> received.set(new String(publish.getPayloadAsBytes(), StandardCharsets.UTF_8)));

        deviceClient = newClient("device");
        deviceClient.connect().get(10, TimeUnit.SECONDS);
        deviceClient
                .publishWith()
                .topic(upTopic)
                .qos(MqttQos.AT_LEAST_ONCE)
                .payload(payload.getBytes(StandardCharsets.UTF_8))
                .send()
                .get(10, TimeUnit.SECONDS);

        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (received.get() == null && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
        assertThat(received.get()).isEqualTo(payload);
    }

    @Test
    @DisplayName("租户 topic 隔离：租户 A 收不到租户 B 的上行")
    void tenantTopicsAreIsolated() throws Exception {
        String tenantA = "tenant-live-a";
        String tenantB = "tenant-live-b";
        String deviceA = "device-a-1";
        String deviceB = "device-b-1";

        subscriberClient = newClient("subscriber-a");
        subscriberClient.connect().get(10, TimeUnit.SECONDS);
        subscriberClient
                .subscribeWith()
                .topicFilter(MqttTopicPolicy.upTopic(tenantA, deviceA))
                .qos(MqttQos.AT_LEAST_ONCE)
                .send()
                .get(10, TimeUnit.SECONDS);
        AtomicReference<String> leaked = new AtomicReference<>();
        subscriberClient.publishes(
                com.hivemq.client.mqtt.MqttGlobalPublishFilter.ALL,
                publish -> leaked.set(new String(publish.getPayloadAsBytes(), StandardCharsets.UTF_8)));

        deviceClient = newClient("device-b");
        deviceClient.connect().get(10, TimeUnit.SECONDS);
        deviceClient
                .publishWith()
                .topic(MqttTopicPolicy.upTopic(tenantB, deviceB))
                .qos(MqttQos.AT_LEAST_ONCE)
                .payload("{\"tenant\":\"B\"}".getBytes(StandardCharsets.UTF_8))
                .send()
                .get(10, TimeUnit.SECONDS);

        Thread.sleep(Duration.ofSeconds(2).toMillis());
        assertThat(leaked.get()).as("租户 A 不应收到租户 B 的消息").isNull();

        assertThat(MqttTopicPolicy.isAllowed(tenantA, deviceA, MqttTopicPolicy.upTopic(tenantB, deviceB)))
                .isFalse();
        assertThat(MqttTopicPolicy.isAllowed(tenantA, deviceA, MqttTopicPolicy.upTopic(tenantA, deviceA)))
                .isTrue();
    }

    @Test
    @DisplayName("共享订阅可消费任意设备上行（水平扩展路径）")
    void sharedSubscriptionConsumesUpstream() throws Exception {
        String tenant = "tenant-live-shared";
        String device = "device-shared-1";
        String upTopic = MqttTopicPolicy.upTopic(tenant, device);

        subscriberClient = newClient("shared-subscriber");
        subscriberClient.connect().get(10, TimeUnit.SECONDS);
        subscriberClient
                .subscribeWith()
                .topicFilter(MqttTopicPolicy.sharedUpFilter("apex-live-test"))
                .qos(MqttQos.AT_LEAST_ONCE)
                .send()
                .get(10, TimeUnit.SECONDS);
        AtomicReference<String> receivedTopic = new AtomicReference<>();
        subscriberClient.publishes(
                com.hivemq.client.mqtt.MqttGlobalPublishFilter.ALL,
                publish -> receivedTopic.set(publish.getTopic().toString()));

        deviceClient = newClient("shared-device");
        deviceClient.connect().get(10, TimeUnit.SECONDS);
        deviceClient
                .publishWith()
                .topic(upTopic)
                .qos(MqttQos.AT_LEAST_ONCE)
                .payload("{}".getBytes(StandardCharsets.UTF_8))
                .send()
                .get(10, TimeUnit.SECONDS);

        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (receivedTopic.get() == null && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
        assertThat(receivedTopic.get()).isEqualTo(upTopic);
    }
}
