package com.apex.scaffold.authservice.infrastructure.mq;

import com.apex.scaffold.authservice.config.RemoteTenantStatusAdapter;
import com.apex.scaffold.common.util.JsonUtil;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 租户主数据变更事件监听器（方案 D5 主数据分发的消费方）。
 *
 * <p>消费分布式事件 Topic（{@code apex.mq.domain-event-topic}，tag =
 * {@code TENANT_CHANGED}，由 apex-system 经 Outbox + MQ 桥发布），失效登录前置
 * 租户校验（A6 {@link RemoteTenantStatusAdapter}）的本地判定缓存：租户禁用/过期后
 * 新登录最迟在一个事件投递周期内被拒绝，而不必等待判定缓存 TTL 自然过期。
 *
 * <p><b>幂等口径</b>：消费动作是"按键失效缓存"，重复投递重复失效同一键，天然幂等；
 * 事件丢失可容忍（TTL 到期自然回源），见 {@code DomainEventMqBridge} 投递语义说明。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "apex.features", name = "message-queue", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass(RocketMQTemplate.class)
@ConditionalOnBean(RocketMQTemplate.class)
@ConditionalOnProperty(name = "apex.mq.consumer.enabled", havingValue = "true", matchIfMissing = true)
@RocketMQMessageListener(
        consumerGroup = "${apex.mq.consumer-groups.master-data-auth:apex-auth-master-data}",
        topic = "${apex.mq.domain-event-topic:apex-domain-event}",
        selectorExpression = "TENANT_CHANGED",
        consumeMode = ConsumeMode.CONCURRENTLY,
        maxReconsumeTimes = 3)
public class TenantChangedEventListener implements RocketMQListener<String> {

    private final RemoteTenantStatusAdapter tenantStatusAdapter;

    /**
     * 构造监听器。
     *
     * @param tenantStatusAdapter 登录前置租户校验适配器（判定缓存持有方）
     */
    public TenantChangedEventListener(RemoteTenantStatusAdapter tenantStatusAdapter) {
        this.tenantStatusAdapter = tenantStatusAdapter;
    }

    /**
     * 消费 {@code TENANT_CHANGED} 事件并失效对应租户的判定缓存.
     *
     * @param message 事件 JSON（{@code Result} 信封外的原始事件负载）
     */
    @Override
    public void onMessage(String message) {
        try {
            Map<?, ?> payload = JsonUtil.fromJson(message, Map.class);
            String tenantCode = payload == null ? null : (String) payload.get("tenantCode");
            tenantStatusAdapter.invalidate(tenantCode);
            log.info("租户主数据变更事件已消费，判定缓存失效: tenantCode={}", tenantCode);
        } catch (RuntimeException e) {
            // 单条消息解析失败仅记录告警：失效语义可由 TTL 回源兜底，不阻断消费位点
            log.warn("租户变更事件解析失败，跳过失效: reason={}", e.getMessage());
        }
    }
}
