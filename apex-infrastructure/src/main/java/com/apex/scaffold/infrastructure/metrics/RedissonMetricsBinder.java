package com.apex.scaffold.infrastructure.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.Node;
import org.redisson.api.NodesGroup;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Redisson 节点状态 Micrometer 指标绑定器
 *
 * <p>项目在 prod 使用 Redisson（redisson-spring-boot-starter）作为分布式缓存/锁客户端，
 * 但 Redisson 的 Micrometer 集成位于独立的 {@code redisson-micrometer} 模块，本工程未引入，
 * 因此在此手动将节点连通状态绑定为 Gauge，经 {@link MeterRegistry} 暴露给 Prometheus 抓取。
 *
 * <p>指标命名沿用 {@code redisson.*} 前缀（与 Druid 绑定器 {@code druid.*} 风格一致）：
 * <ul>
 *   <li><strong>redisson.nodes.total</strong>：当前 Redis 节点总数（Gauge）</li>
 *   <li><strong>redisson.healthy</strong>：节点健康探针（Gauge，1 = 全部节点 PING 通，0 = 任一节点不可达）</li>
 * </ul>
 *
 * <p>健康探测使用 {@link Node#ping(long, TimeUnit)} 并限定 500ms 超时，避免 Redis 不可用时
 * 阻塞 Prometheus 抓取线程。绑定时机为 {@link ApplicationReadyEvent}。
 *
 * <p>⚠️ 条件装配：dev 环境排除 Redisson 自动配置（使用 Spring Data Redis/Lettuce），
 * 测试环境使用 Mock RedissonClient。本绑定器通过 {@link ObjectProvider} 惰性获取客户端，
 * 无 RedissonClient bean 时自动跳过，不影响 dev/测试启动。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.1.3
 * @see RedissonClient
 * @see MeterRegistry
 */
@Slf4j
@Component
public class RedissonMetricsBinder {

    /** 节点总数指标名 */
    static final String NODES_TOTAL = "redisson.nodes.total";

    /** 健康探针指标名 */
    static final String HEALTHY = "redisson.healthy";

    /** 单节点 PING 超时（毫秒），Redis 不可用时快速失败 */
    private static final long PING_TIMEOUT_MILLIS = 500;

    /** 指标注册表 */
    private final MeterRegistry meterRegistry;

    /** Redisson 客户端提供者（无 bean 时为 null，实现条件装配） */
    private final ObjectProvider<RedissonClient> redissonClientProvider;

    /**
     * 构造 Redisson 指标绑定器
     *
     * @param meterRegistry Micrometer 指标注册表
     * @param redissonClientProvider Redisson 客户端提供者（dev 无 RedissonClient 时 getIfAvailable 返回 null）
     */
    public RedissonMetricsBinder(MeterRegistry meterRegistry, ObjectProvider<RedissonClient> redissonClientProvider) {
        this.meterRegistry = meterRegistry;
        this.redissonClientProvider = redissonClientProvider;
    }

    /**
     * 应用启动完成后注册节点指标；无 RedissonClient 时跳过（dev/测试环境）
     */
    @EventListener(ApplicationReadyEvent.class)
    public void bindOnReady() {
        RedissonClient client = redissonClientProvider.getIfAvailable();
        if (client == null) {
            log.info("未检测到 RedissonClient，跳过 Redis 指标绑定（dev 使用 Spring Data Redis，无 Redisson 客户端）");
            return;
        }
        NodesGroup<Node> nodesGroup = client.getNodesGroup();
        Gauge.builder(NODES_TOTAL, nodesGroup, group -> group.getNodes().size()).register(meterRegistry);
        Gauge.builder(HEALTHY, nodesGroup, this::healthy).register(meterRegistry);
        log.info("Redisson 节点指标绑定完成（{} 个节点）", nodesGroup.getNodes().size());
    }

    /**
     * 计算节点健康状态：全部节点 PING 通为 1，任一节点不可达或探测异常为 0
     *
     * @param nodesGroup 节点组
     * @return 1 = 健康，0 = 不健康
     */
    private double healthy(NodesGroup<Node> nodesGroup) {
        try {
            Collection<Node> nodes = nodesGroup.getNodes();
            if (nodes == null || nodes.isEmpty()) {
                return 0;
            }
            for (Node node : nodes) {
                if (!node.ping(PING_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                    return 0;
                }
            }
            return 1;
        } catch (Exception e) {
            log.debug("Redisson 健康探测异常: {}", e.getMessage());
            return 0;
        }
    }
}
