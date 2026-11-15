package com.apex.scaffold.infrastructure.mq.monitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.apex.scaffold.infrastructure.mq.alert.MqAlertManager;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.remoting.protocol.admin.ConsumeStats;
import org.apache.rocketmq.remoting.protocol.admin.OffsetWrapper;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link MqLagMonitor} 的单元测试。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.2.0
 */
@ExtendWith(MockitoExtension.class)
class MqLagMonitorTest {

    @Mock
    private MqAlertManager alertManager;

    @Mock
    private DefaultMQAdminExt adminClient;

    private MqLagMonitor monitor;

    @BeforeEach
    void setUp() {
        monitor = new MqLagMonitor(
                new SimpleMeterRegistry(), alertManager, adminClient, 10000L, 50000L, true, "localhost:9876");
    }

    @Test
    @DisplayName("应汇总所有队列的非负积压量")
    void shouldSumNonNegativeLagAcrossQueues() throws Exception {
        ConsumeStats stats = new ConsumeStats();
        stats.setOffsetTable(Map.of(
                queue("orders", 0), offset(120, 80),
                queue("orders", 1), offset(40, 60),
                queue("orders", 2), offset(15, 5)));
        when(adminClient.examineConsumeStats("group-a")).thenReturn(stats);

        monitor.registerConsumer("group-a", mock(DefaultMQPushConsumer.class));

        assertThat(monitor.getAllConsumerLags()).containsEntry("group-a", 50L);
        verify(adminClient).start();
    }

    @Test
    @DisplayName("重复查询时应复用已启动的管理客户端")
    void shouldStartAdminClientOnlyOnce() throws Exception {
        ConsumeStats stats = new ConsumeStats();
        stats.setOffsetTable(new LinkedHashMap<>());
        when(adminClient.examineConsumeStats("group-a")).thenReturn(stats);

        monitor.registerConsumer("group-a", mock(DefaultMQPushConsumer.class));
        monitor.getAllConsumerLags();
        monitor.getAllConsumerLags();

        verify(adminClient).setNamesrvAddr("localhost:9876");
        verify(adminClient).start();
    }

    @Test
    @DisplayName("管理客户端返回空结果时应返回零")
    void shouldReturnZeroForEmptyStats() throws Exception {
        when(adminClient.examineConsumeStats("group-a")).thenReturn(null);

        monitor.registerConsumer("group-a", mock(DefaultMQPushConsumer.class));

        assertThat(monitor.getAllConsumerLags()).containsEntry("group-a", 0L);
    }

    @Test
    @DisplayName("管理客户端查询异常时应返回零并保持监控流程可用")
    void shouldReturnZeroWhenAdminQueryFails() throws Exception {
        when(adminClient.examineConsumeStats("group-a")).thenThrow(new IllegalStateException("admin unavailable"));

        monitor.registerConsumer("group-a", mock(DefaultMQPushConsumer.class));

        assertThat(monitor.getAllConsumerLags()).containsEntry("group-a", 0L);
        verifyNoInteractions(alertManager);
    }

    @Test
    @DisplayName("销毁时应关闭已启动的管理客户端")
    void shouldShutdownStartedAdminClient() throws Exception {
        ConsumeStats stats = new ConsumeStats();
        stats.setOffsetTable(new LinkedHashMap<>());
        when(adminClient.examineConsumeStats("group-a")).thenReturn(stats);
        monitor.registerConsumer("group-a", mock(DefaultMQPushConsumer.class));
        monitor.getAllConsumerLags();

        monitor.shutdownAdminClient();

        verify(adminClient).shutdown();
    }

    private static MessageQueue queue(String topic, int queueId) {
        return new MessageQueue(topic, "broker-a", queueId);
    }

    private static OffsetWrapper offset(long brokerOffset, long consumerOffset) {
        OffsetWrapper offset = new OffsetWrapper();
        offset.setBrokerOffset(brokerOffset);
        offset.setConsumerOffset(consumerOffset);
        return offset;
    }
}
