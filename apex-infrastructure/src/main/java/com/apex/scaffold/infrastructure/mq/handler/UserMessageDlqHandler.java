package com.apex.scaffold.infrastructure.mq.handler;

import com.apex.scaffold.infrastructure.mq.constant.MqConstant;
import com.apex.scaffold.infrastructure.mq.listener.DeadLetterQueueProcessor;
import com.apex.scaffold.infrastructure.outbox.entity.MessageOutboxEntity;
import com.apex.scaffold.infrastructure.outbox.service.MessageOutboxService;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * 用户消息死信队列处理器
 *
 * <p>处理用户相关消息(注册、登录、更新等)消费失败后进入死信队列的情况。
 * 采用重新投递到 Outbox 表的策略,由后台调度器自动重试。
 *
 * <h2>处理策略</h2>
 * <ul>
 *   <li>提取死信消息的内容和元数据</li>
 *   <li>重新写入 Outbox 表,增加重试次数和延迟</li>
 *   <li>记录告警日志,便于问题排查</li>
 * </ul>
 *
 * <h2>配置说明</h2>
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
@RequiredArgsConstructor
@ConditionalOnBean(DeadLetterQueueProcessor.class)
public class UserMessageDlqHandler implements DeadLetterQueueProcessor.DlqHandler {

    private final DeadLetterQueueProcessor deadLetterQueueProcessor;

    private final MessageOutboxService messageOutboxService;

    /**
     * 初始化时自动注册到死信处理器
     */
    @PostConstruct
    public void register() {
        deadLetterQueueProcessor.registerHandler(MqConstant.TOPIC_USER, MqConstant.CONSUMER_GROUP_USER, this);
        log.info("UserMessageDlqHandler 已注册");
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
        String messageId = messageExt.getKeys();
        String body = new String(messageExt.getBody(), StandardCharsets.UTF_8);

        log.error(
                "用户消息进入死信队列: messageId={}, topic={}, tag={}, body={}",
                messageId,
                messageExt.getTopic(),
                messageExt.getTags(),
                body);

        MessageOutboxEntity outbox = MessageOutboxEntity.builder()
                .messageId(messageId + ":dlq-retry")
                .topic(MqConstant.TOPIC_USER)
                .tag(messageExt.getTags())
                .payload(body)
                .messageType(MessageOutboxEntity.MessageType.BUSINESS_MESSAGE)
                .maxAttempts(10)
                .retryDelaySeconds(60)
                .tenantId(extractTenantId(messageExt))
                .build();

        messageOutboxService.append(outbox).block();

        log.info("死信消息已重新写入Outbox: messageId={}", messageId);

        return true;
    }

    /**
     * 从消息头中提取租户ID
     *
     * @param messageExt RocketMQ 消息
     * @return 租户ID,如果不存在则返回 "default"
     */
    private String extractTenantId(MessageExt messageExt) {
        String tenantId = messageExt.getUserProperty("tenantId");
        return tenantId != null ? tenantId : "default";
    }
}
