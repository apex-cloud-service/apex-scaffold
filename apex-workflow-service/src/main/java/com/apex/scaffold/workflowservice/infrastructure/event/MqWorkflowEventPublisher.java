package com.apex.scaffold.workflowservice.infrastructure.event;

import com.apex.scaffold.common.event.AbstractDomainEvent;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.common.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import reactor.core.publisher.Mono;

/**
 * 基于 RocketMQ 的工作流事件发布器（生产实现）.
 *
 * <p>将工作流领域事件序列化为 JSON 后，通过 {@link RocketMQTemplate} 异步直发到主题
 * {@code apex-workflow-events}，实现跨服务的事件广播（迁移方案 §6.4 / 执行计划 Phase 3.4）。
 *
 * <h2>投递语义</h2>
 * <ul>
 *   <li>消息体为事件的 JSON 序列化结果（含 eventId / createdAt / traceId / tenantId 信封字段）；</li>
 *   <li>以事件 ID 作为消息 KEYS，便于订阅方按事件幂等去重与审计检索；</li>
 *   <li>采用 {@link RocketMQTemplate#asyncSend} 异步发送，并借助
 *       {@link BlockingIoSchedulers#event()} 承载阻塞式回调线程，
 *       避免占用 Netty 事件循环。</li>
 * </ul>
 *
 * <p>本实现由 {@link WorkflowEventConfig} 在 {@code apex.events.outbox.enabled=true} 且
 * 存在 {@link RocketMQTemplate} Bean 时装配；basic/H2 环境改由
 * {@link LocalWorkflowEventPublisher} 兜底。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.1
 * @see WorkflowEventPublisher
 * @see LocalWorkflowEventPublisher
 * @see RocketMQTemplate
 */
@RequiredArgsConstructor
public class MqWorkflowEventPublisher implements WorkflowEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(MqWorkflowEventPublisher.class);

    /** RocketMQ 模板，用于分布式事件发布 */
    private final RocketMQTemplate rocketMQTemplate;

    /** 工作流事件主题名称（契约固定为 apex-workflow-events） */
    @Value("${apex.mq.workflow-event-topic:apex-workflow-events}")
    private String workflowEventTopic;

    /**
     * 发布工作流领域事件到 RocketMQ。
     *
     * @param event 工作流领域事件对象
     * @return 空 {@link Mono}，异步发送成功后完成；发送失败则发出错误信号
     */
    @Override
    public Mono<Void> publish(AbstractDomainEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("待发布的工作流事件不能为空");
        }

        log.debug("发布工作流事件: {}, eventId: {}", event.getClass().getSimpleName(), event.getEventId());

        String eventJson = JsonUtil.toJson(event);

        Message<String> message = MessageBuilder.withPayload(eventJson)
                .setHeader(RocketMQHeaders.KEYS, event.getEventId())
                .build();

        return Mono.<Void>create(sink -> {
                    try {
                        rocketMQTemplate.asyncSend(workflowEventTopic, message, new SendCallback() {
                            /** {@inheritDoc} */
                            @Override
                            public void onSuccess(SendResult sendResult) {
                                log.info(
                                        "工作流事件发送成功: {} (eventId={}, msgId={})",
                                        event.getEventName(),
                                        event.getEventId(),
                                        sendResult.getMsgId());
                                sink.success();
                            }

                            /** {@inheritDoc} */
                            @Override
                            public void onException(Throwable e) {
                                log.error(
                                        "工作流事件发送失败: {} (eventId={}), error: {}",
                                        event.getEventName(),
                                        event.getEventId(),
                                        e.getMessage(),
                                        e);
                                sink.error(e);
                            }
                        });
                    } catch (Exception e) {
                        sink.error(e);
                    }
                })
                .subscribeOn(BlockingIoSchedulers.event())
                .doOnSuccess(v -> log.info("工作流事件发布完成: {} (eventId={})", event.getEventName(), event.getEventId()))
                .doOnError(e -> log.error(
                        "工作流事件发布失败: {} (eventId={}), error: {}",
                        event.getEventName(),
                        event.getEventId(),
                        e.getMessage(),
                        e));
    }
}
