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
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 遥测上行吞吐基线测试（方案 M5 / 批次 E3 基线项）。
 *
 * <p>在真实 EMQX broker 上测量单连接、单订阅方的上行吞吐基线，为容量规划提供
 * 参考数据。<b>本测试只记录基线，不构成 SLA 承诺</b>——结果输出到标准输出，
 * 断言仅校验"消息无丢失"这一功能性事实。
 *
 * <p>可通过 {@code -Dapex.mqtt.baseline.count=2000} 调整消息数（默认 1000）。
 * broker 不可达时跳过（与 {@code IotMqttBrokerLiveTest} 一致）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@DisplayName("遥测上行吞吐基线测试")
class IotMqttThroughputBaselineTest {

    private static final String HOST = System.getProperty("apex.mqtt.host", "127.0.0.1");

    private static final int PORT = Integer.getInteger("apex.mqtt.port", 1883);

    private static final int MESSAGE_COUNT = Integer.getInteger("apex.mqtt.baseline.count", 1000);

    @Test
    @DisplayName("单连接上行吞吐基线：消息不丢失并记录吞吐")
    void measureSingleConnectionThroughput() throws Exception {
        boolean reachable;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(HOST, PORT), 2000);
            reachable = true;
        } catch (Exception unreachable) {
            reachable = false;
        }
        assumeTrue(reachable, "MQTT broker 不可达，跳过吞吐基线测试");

        String tenant = "tenant-baseline";
        String device = "device-baseline-1";
        String upTopic = MqttTopicPolicy.upTopic(tenant, device);

        Mqtt5AsyncClient subscriber = MqttClient.builder()
                .useMqttVersion5()
                .identifier("baseline-subscriber-" + UUID.randomUUID())
                .serverHost(HOST)
                .serverPort(PORT)
                .buildAsync();
        Mqtt5AsyncClient publisher = MqttClient.builder()
                .useMqttVersion5()
                .identifier("baseline-publisher-" + UUID.randomUUID())
                .serverHost(HOST)
                .serverPort(PORT)
                .buildAsync();
        try {
            subscriber.connect().get(10, TimeUnit.SECONDS);
            subscriber
                    .subscribeWith()
                    .topicFilter(upTopic)
                    .qos(MqttQos.AT_LEAST_ONCE)
                    .send()
                    .get(10, TimeUnit.SECONDS);

            CountDownLatch latch = new CountDownLatch(MESSAGE_COUNT);
            AtomicInteger received = new AtomicInteger();
            subscriber.publishes(MqttGlobalPublishFilter.ALL, publish -> {
                received.incrementAndGet();
                latch.countDown();
            });

            publisher.connect().get(10, TimeUnit.SECONDS);
            byte[] payload = "{\"temperature\":23.5}".getBytes(StandardCharsets.UTF_8);

            long startNanos = System.nanoTime();
            for (int index = 0; index < MESSAGE_COUNT; index++) {
                publisher
                        .publishWith()
                        .topic(upTopic)
                        .qos(MqttQos.AT_LEAST_ONCE)
                        .payload(payload)
                        .send();
            }
            boolean completed = latch.await(60, TimeUnit.SECONDS);
            long elapsedNanos = System.nanoTime() - startNanos;

            assertThat(completed).as("全部 %d 条上行消息应在 60 秒内到达", MESSAGE_COUNT).isTrue();
            assertThat(received.get()).isEqualTo(MESSAGE_COUNT);

            double elapsedSeconds = elapsedNanos / 1_000_000_000.0;
            double throughput = MESSAGE_COUNT / elapsedSeconds;
            System.out.printf(
                    "【吞吐基线】broker=%s:%d, 消息数=%d, QoS=1, 耗时=%.3fs, 吞吐=%.0f msg/s%n",
                    HOST, PORT, MESSAGE_COUNT, elapsedSeconds, throughput);
            assertThat(throughput).as("基线吞吐应为正值").isGreaterThan(0);
        } finally {
            try {
                publisher.disconnect().get(5, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                // 清理失败不影响结论
            }
            try {
                subscriber.disconnect().get(5, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                // 清理失败不影响结论
            }
        }
    }
}
