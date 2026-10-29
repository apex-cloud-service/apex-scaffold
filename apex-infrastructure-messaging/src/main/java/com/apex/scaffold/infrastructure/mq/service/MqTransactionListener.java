package com.apex.scaffold.infrastructure.mq.service;

import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

/**
 * RocketMQ 本地事务监听器适配器。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Component
@ConditionalOnProperty(prefix = "apex.features", name = "message-queue", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass(RocketMQLocalTransactionListener.class)
@ConditionalOnBean(org.apache.rocketmq.spring.core.RocketMQTemplate.class)
@RocketMQTransactionListener
public class MqTransactionListener implements RocketMQLocalTransactionListener {
    private final MqLocalTransactionHandler handler;

    /**
     * 创建事务监听器。
     *
     * @param handler 可选的本地事务处理器
     */
    public MqTransactionListener(org.springframework.beans.factory.ObjectProvider<MqLocalTransactionHandler> handler) {
        this.handler = handler.getIfAvailable();
    }

    /**
     * 执行本地事务。
     *
     * @param message 消息
     * @param argument 事务参数
     * @return 事务状态
     */
    @Override
    public RocketMQLocalTransactionState executeLocalTransaction(Message message, Object argument) {
        if (handler == null) {
            return RocketMQLocalTransactionState.UNKNOWN;
        }
        return handler.execute(message, argument);
    }

    /**
     * 检查本地事务。
     *
     * @param message 消息
     * @return 事务状态
     */
    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message message) {
        if (handler == null) {
            return RocketMQLocalTransactionState.UNKNOWN;
        }
        return handler.check(message);
    }
}
