package com.apex.scaffold.integration.device.mqtt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.apex.scaffold.integration.device.domain.MqttTopicPolicy;
import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 多连接并发上行吞吐基线（方案 M5 / 批次 E3 容量基线项）。
 *
 * <p>测量"多设备并发上行 → 单共享订阅方消费"的吞吐，用于容量规划参考：
 * 连接数由 {@code -Dapex.mqtt.concurrency.devices} 控制（默认 10），
 * 每设备消息数由 {@code -Dapex.mqtt.concurrency.perDevice} 控制（默认 100）。
 *
 * <p><b>只记录基线，不构成 SLA 承诺</b>；断言仅校验"消息无丢失"。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@DisplayName("多连接并发上行基线测试")
class IotMqttConcurrencyBaselineTest {

    private static final String HOST = System.getProperty("apex.mqtt.host", "127.0.0.1");

    private static final int PORT = Integer.getInteger("apex.mqtt.port", 1883);

    private static final int DEVICES = Integer.getInteger("apex.mqtt.concurrency.devices", 10);

    private static final int PER_DEVICE = Integer.getInteger("apex.mqtt.concurrency.perDevice", 100);

    @Test
    @DisplayName("多设备并发上行吞吐基线（消息不丢失）")
    void measureConcurrentMultiDeviceThroughput() throws Exception {
        boolean reachable;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(HOST, PORT), 2000);
            reachable = true;
        } catch (Exception unreachable) {
            reachable = false;
        }
        assumeTrue(reachable, "MQTT broker 不可达，跳过多连接基线测试");

        String tenant = "tenant-concurrency";
        int total = DEVICES * PER_DEVICE;

        Mqtt5AsyncClient subscriber = MqttClient.builder()
                .useMqttVersion5()
                .identifier("conc-subscriber-" + UUID.randomUUID())
                .serverHost(HOST)
                .serverPort(PORT)
                .buildAsync();
        List<Mqtt5AsyncClient> devices = new ArrayList<>();
        try {
            subscriber.connect().get(10, TimeUnit.SECONDS);
            subscriber
                    .subscribeWith()
                    .topicFilter(MqttTopicPolicy.sharedUpFilter("apex-concurrency"))
                    .qos(MqttQos.AT_LEAST_ONCE)
                    .send()
                    .get(10, TimeUnit.SECONDS);

            CountDownLatch latch = new CountDownLatch(total);
            AtomicInteger received = new AtomicInteger();
            subscriber.publishes(MqttGlobalPublishFilter.ALL, publish -> {
                received.incrementAndGet();
                latch.countDown();
            });

            for (int index = 0; index < DEVICES; index++) {
                Mqtt5AsyncClient device = MqttClient.builder()
                        .useMqttVersion5()
                        .identifier("conc-device-" + index + "-" + UUID.randomUUID())
                        .serverHost(HOST)
                        .serverPort(PORT)
                        .buildAsync();
                device.connect().get(10, TimeUnit.SECONDS);
                devices.add(device);
            }

            byte[] payload = "{\"v\":1}".getBytes(StandardCharsets.UTF_8);
            long startNanos = System.nanoTime();
            for (int deviceIndex = 0; deviceIndex < devices.size(); deviceIndex++) {
                Mqtt5AsyncClient device = devices.get(deviceIndex);
                String topic = MqttTopicPolicy.upTopic(tenant, "device-" + deviceIndex);
                for (int message = 0; message < PER_DEVICE; message++) {
                    device.publishWith()
                            .topic(topic)
                            .qos(MqttQos.AT_LEAST_ONCE)
                            .payload(payload)
                            .send();
                }
            }
            boolean completed = latch.await(120, TimeUnit.SECONDS);
            long elapsedNanos = System.nanoTime() - startNanos;

            assertThat(completed)
                    .as("%d 设备 × %d 消息 = %d 条应在 120 秒内全部到达", DEVICES, PER_DEVICE, total)
                    .isTrue();
            assertThat(received.get()).isEqualTo(total);

            double seconds = elapsedNanos / 1_000_000_000.0;
            System.out.printf(
                    "【并发基线】broker=%s:%d, 设备数=%d, 每设备=%d, 总消息=%d, QoS=1, " + "耗时=%.3fs, 吞吐=%.0f msg/s%n",
                    HOST, PORT, DEVICES, PER_DEVICE, total, seconds, total / seconds);
        } finally {
            for (Mqtt5AsyncClient device : devices) {
                try {
                    device.disconnect().get(3, TimeUnit.SECONDS);
                } catch (Exception ignored) {
                    // 清理失败不影响结论
                }
            }
            try {
                subscriber.disconnect().get(5, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                // 清理失败不影响结论
            }
        }
    }
}
