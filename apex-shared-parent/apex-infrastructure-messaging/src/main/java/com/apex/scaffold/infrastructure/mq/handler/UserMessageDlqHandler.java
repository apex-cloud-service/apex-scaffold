package com.apex.scaffold.infrastructure.mq.handler;

import com.apex.scaffold.infrastructure.mq.constant.MqConstant;
import com.apex.scaffold.infrastructure.mq.listener.DeadLetterQueueProcessor;
import com.apex.scaffold.infrastructure.outbox.entity.MessageOutboxEntity;
import com.apex.scaffold.infrastructure.outbox.service.MessageOutboxService;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * 用户消息死信队列处理器
 *
 * <p>处理用户相关消息(注册、登录、更新等)消费失败后进入死信队列的情况。
 * 采用重新投递到 Outbox 表的策略,由后台调度器自动重试。
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>提取死信消息的内容和元数据</li>
 *   <li>重新写入 Outbox 表,增加重试次数和延迟</li>
 *   <li>记录告警日志,便于问题排查</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>最大重试次数: 10次(可配置)</li>
 *   <li>重试延迟: 60秒起步,指数退避</li>
 *   <li>自动注册到 DeadLetterQueueProcessor</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see DeadLetterQueueProcessor
 * @see MessageOutboxService
 */
@Slf4j
@Component
@ConditionalOnBean(DeadLetterQueueProcessor.class)
public class UserMessageDlqHandler implements DeadLetterQueueProcessor.DlqHandler {

    private final DeadLetterQueueProcessor deadLetterQueueProcessor;

    private final MessageOutboxService messageOutboxService;

    /** 业务实际使用的用户消息 Topic，与监听器注解占位符同源。 */
    private final String userTopic;

    /** 业务实际使用的用户消费组，与监听器注解占位符同源。 */
    private final String userConsumerGroup;

    /**
     * 创建用户消息死信处理器。
     *
     * @param deadLetterQueueProcessor 死信队列处理器
     * @param messageOutboxService Outbox 服务
     * @param userTopic 实际用户消息 Topic，缺省回退 {@link MqConstant#TOPIC_USER}
     * @param userConsumerGroup 实际用户消费组，缺省回退 {@link MqConstant#CONSUMER_GROUP_USER}
     */
    public UserMessageDlqHandler(
            DeadLetterQueueProcessor deadLetterQueueProcessor,
            MessageOutboxService messageOutboxService,
            @Value("${apex.mq.topics.user:" + MqConstant.TOPIC_USER + "}") String userTopic,
            @Value("${apex.mq.consumer-groups.user:" + MqConstant.CONSUMER_GROUP_USER + "}") String userConsumerGroup) {
        this.deadLetterQueueProcessor = deadLetterQueueProcessor;
        this.messageOutboxService = messageOutboxService;
        this.userTopic = userTopic;
        this.userConsumerGroup = userConsumerGroup;
    }

    /**
     * 初始化时按实际 Topic 与消费组注册到死信处理器
     */
    @PostConstruct
    public void register() {
        deadLetterQueueProcessor.registerHandler(userTopic, userConsumerGroup, this);
        log.info("UserMessageDlqHandler 已注册, topic: {}, consumerGroup: {}", userTopic, userConsumerGroup);
    }

    /**
     * 处理用户消息死信
     *
     * <p>将死信消息重新写入 Outbox 表,等待后台调度器重试。
     * 采用更宽松的重试策略(10次、60秒延迟)给予更多恢复机会。
     *
     * @param messageExt RocketMQ 消息扩展对象
     * @return true 表示处理成功,false 表示处理失败
     */
    @Override
    public boolean handle(MessageExt messageExt) {
        String tenantId = extractTenantId(messageExt);
        String messageId = messageExt.getKeys();
        String body = new String(messageExt.getBody(), StandardCharsets.UTF_8);

        log.error("用户消息进入死信队列: messageId={}, topic={}, tag={}", messageId, messageExt.getTopic(), messageExt.getTags());

        MessageOutboxEntity outbox = MessageOutboxEntity.builder()
                .messageId(messageId + ":dlq-retry")
                .topic(userTopic)
                .tag(messageExt.getTags())
                .payload(body)
                .messageType(MessageOutboxEntity.MessageType.BUSINESS_MESSAGE)
                .maxAttempts(10)
                .retryDelaySeconds(60)
                .tenantId(tenantId)
                .build();

        messageOutboxService.append(outbox).block();

        log.info("死信消息已重新写入Outbox: messageId={}", messageId);

        return true;
    }

    /**
     * 从消息头中提取租户ID
     *
     * @param messageExt RocketMQ 消息
     * @return 消息中明确指定的非空白租户ID
     * @throws IllegalArgumentException 当消息未携带租户时抛出
     */
    private String extractTenantId(MessageExt messageExt) {
        String tenantId = messageExt.getUserProperty("tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("用户死信消息缺少租户ID");
        }
        return tenantId;
    }
}
