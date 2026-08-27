package com.apex.scaffold.infrastructure.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.Node;
import org.redisson.api.NodesGroup;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Redisson 节点指标绑定器单元测试
 *
 * @author Apex Team
 */
class RedissonMetricsBinderTest {

    /** 指标注册表 */
    private MeterRegistry registry;

    /** 被测绑定器 */
    private RedissonMetricsBinder binder;

    /** 初始化测试环境 */
    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        registry = new SimpleMeterRegistry();
        ObjectProvider<RedissonClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mock(RedissonClient.class));
        binder = new RedissonMetricsBinder(registry, provider);
    }

    /** 无 RedissonClient（dev/测试环境）时应跳过，不注册任何指标 */
    @Test
    @SuppressWarnings("unchecked")
    void bindOnReadySkipsWhenNoRedissonClient() {
        ObjectProvider<RedissonClient> emptyProvider = mock(ObjectProvider.class);
        when(emptyProvider.getIfAvailable()).thenReturn(null);
        RedissonMetricsBinder skippedBinder = new RedissonMetricsBinder(registry, emptyProvider);

        skippedBinder.bindOnReady();

        assertEquals(0, registry.getMeters().size());
    }

    /** 全部节点 PING 通时 healthy 应为 1，并注册节点总数指标 */
    @Test
    @SuppressWarnings("unchecked")
    void bindOnReadyRegistersHealthyMetrics() {
        RedissonClient client = mock(RedissonClient.class);
        NodesGroup<Node> nodesGroup = mock(NodesGroup.class);
        Node node1 = mock(Node.class);
        Node node2 = mock(Node.class);
        when(client.getNodesGroup()).thenReturn(nodesGroup);
        when(nodesGroup.getNodes()).thenReturn(List.of(node1, node2));
        when(node1.ping(anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(true);
        when(node2.ping(anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(true);

        ObjectProvider<RedissonClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(client);
        RedissonMetricsBinder healthyBinder = new RedissonMetricsBinder(registry, provider);
        healthyBinder.bindOnReady();

        assertEquals(2.0, registry.get("redisson.nodes.total").gauge().value());
        assertEquals(1.0, registry.get("redisson.healthy").gauge().value());
    }

    /** 任一节点 PING 失败时 healthy 应为 0 */
    @Test
    @SuppressWarnings("unchecked")
    void bindOnReadyReportsUnhealthyWhenNodePingFails() {
        RedissonClient client = mock(RedissonClient.class);
        NodesGroup<Node> nodesGroup = mock(NodesGroup.class);
        Node node1 = mock(Node.class);
        Node node2 = mock(Node.class);
        when(client.getNodesGroup()).thenReturn(nodesGroup);
        when(nodesGroup.getNodes()).thenReturn(List.of(node1, node2));
        when(node1.ping(anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(true);
        when(node2.ping(anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(false);

        ObjectProvider<RedissonClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(client);
        RedissonMetricsBinder unhealthyBinder = new RedissonMetricsBinder(registry, provider);
        unhealthyBinder.bindOnReady();

        assertEquals(0.0, registry.get("redisson.healthy").gauge().value());
    }

    /** 节点列表为空或探测异常时 healthy 应为 0（防御性） */
    @Test
    @SuppressWarnings("unchecked")
    void bindOnReadyHandlesEmptyNodesAndExceptions() {
        // 空节点列表
        RedissonClient emptyClient = mock(RedissonClient.class);
        NodesGroup<Node> emptyGroup = mock(NodesGroup.class);
        when(emptyClient.getNodesGroup()).thenReturn(emptyGroup);
        when(emptyGroup.getNodes()).thenReturn(List.of());
        ObjectProvider<RedissonClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(emptyClient);
        RedissonMetricsBinder binder1 = new RedissonMetricsBinder(registry, provider);
        binder1.bindOnReady();
        assertEquals(0.0, registry.get("redisson.healthy").gauge().value());

        // 探测抛异常
        registry.getMeters().clear();
        RedissonClient boomClient = mock(RedissonClient.class);
        NodesGroup<Node> boomGroup = mock(NodesGroup.class);
        Node boomNode = mock(Node.class);
        when(boomClient.getNodesGroup()).thenReturn(boomGroup);
        when(boomGroup.getNodes()).thenReturn(List.of(boomNode));
        when(boomNode.ping(anyLong(), eq(TimeUnit.MILLISECONDS))).thenThrow(new RuntimeException("conn refused"));
        ObjectProvider<RedissonClient> boomProvider = mock(ObjectProvider.class);
        when(boomProvider.getIfAvailable()).thenReturn(boomClient);
        RedissonMetricsBinder binder2 = new RedissonMetricsBinder(registry, boomProvider);
        binder2.bindOnReady();
        assertEquals(0.0, registry.get("redisson.healthy").gauge().value());
    }
}
