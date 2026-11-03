package com.apex.scaffold.infrastructure.event;

import com.apex.scaffold.common.event.AbstractDomainEvent;
import com.apex.scaffold.common.event.DistributedDomainEvent;
import com.apex.scaffold.common.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * 分布式领域事件 MQ 桥（方案 D5 主数据分发的跨服务传输段）。
 *
 * <p>领域事件经 {@link DomainEventPublisherImpl} 写入 Outbox 后由
 * {@link DomainEventOutboxDispatcher} 在本进程内重放；本桥接器在重放点上把
 * 实现了 {@link DistributedDomainEvent} 标记的事件以 {@code topic:tag} 形式
 * 异步发送到分布式事件 Topic（{@code apex.mq.domain-event-topic}，默认
 * {@code apex-domain-event}，tag = {@code mqTag()} 即事件类型），供其他服务的
 * MQ 订阅方消费。未实现标记的事件保持既有进程内语义，不跨服务广播。
 *
 * <p><b>投递语义</b>：Outbox 保证"先落库再投递"，本桥为进程内重放点的旁路
 * 异步发送——发送失败仅记录 ERROR 日志（Outbox 已标记 DELIVERED，不做重发），
 * 事件消费方以"失效 + 回源"模式消费，可容忍个别事件丢失（下次 TTL 到期自然回源）。
 * 需要严格不丢的订阅方应改用内部端点回源校验，不依赖事件必达。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Slf4j
@Component
@ConditionalOnBean(RocketMQTemplate.class)
@ConditionalOnProperty(
        prefix = "apex.events.distributed-bridge",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class DomainEventMqBridge {

    private final RocketMQTemplate rocketMQTemplate;

    private final String domainEventTopic;

    /**
     * 构造桥接器。
     *
     * @param rocketMQTemplate RocketMQ 发送模板
     * @param domainEventTopic 分布式事件 Topic（{@code apex.mq.domain-event-topic}）
     */
    public DomainEventMqBridge(
            RocketMQTemplate rocketMQTemplate,
            @Value("${apex.mq.domain-event-topic:apex-domain-event}") String domainEventTopic) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.domainEventTopic = domainEventTopic;
    }

    /**
     * 领域事件进程内重放监听：标记事件跨服务广播.
     *
     * @param event 领域事件（Outbox 重放或进程内同步发布）
     */
    @EventListener
    public void onDomainEvent(AbstractDomainEvent event) {
        if (!(event instanceof DistributedDomainEvent distributed)) {
            return;
        }
        String tag = distributed.mqTag();
        if (tag == null || tag.isBlank()) {
            return;
        }
        Message<String> message = MessageBuilder.withPayload(JsonUtil.toJson(event))
                .setHeader(RocketMQHeaders.KEYS, event.getEventId())
                .build();
        try {
            rocketMQTemplate.asyncSend(domainEventTopic + ":" + tag, message, new SendCallback() {

                @Override
                public void onSuccess(SendResult sendResult) {
                    log.debug(
                            "分布式事件桥发送成功: type={}, eventId={}, msgId={}",
                            tag,
                            event.getEventId(),
                            sendResult.getMsgId());
                }

                @Override
                public void onException(Throwable e) {
                    log.error(
                            "分布式事件桥发送失败（消费方依赖 TTL 回源兜底）: type={}, eventId={}, reason={}",
                            tag,
                            event.getEventId(),
                            e.getMessage());
                }
            });
        } catch (RuntimeException e) {
            log.error("分布式事件桥发送提交失败: type={}, eventId={}, reason={}", tag, event.getEventId(), e.getMessage());
        }
    }
}
