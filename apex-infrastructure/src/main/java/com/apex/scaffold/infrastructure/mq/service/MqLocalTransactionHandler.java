package com.apex.scaffold.infrastructure.mq.service;

import org.springframework.messaging.Message;

/**
 * 本地事务消息处理器。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface MqLocalTransactionHandler {

    /**
     * 执行本地事务。
     *
     * @param message RocketMQ 消息
     * @param argument 事务参数
     * @return 事务状态
     */
    org.apache.rocketmq.spring.core.RocketMQLocalTransactionState execute(
            Message<?> message, Object argument);

    /**
     * 检查本地事务状态。
     *
     * @param message RocketMQ 消息
     * @return 事务状态
     */
    org.apache.rocketmq.spring.core.RocketMQLocalTransactionState check(Message<?> message);
}
