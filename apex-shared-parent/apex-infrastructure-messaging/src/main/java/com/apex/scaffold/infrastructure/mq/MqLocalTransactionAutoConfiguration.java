package com.apex.scaffold.infrastructure.mq;

import com.apex.scaffold.infrastructure.event.DomainEventOutboxStore;
import com.apex.scaffold.infrastructure.mq.service.DefaultMqLocalTransactionHandler;
import com.apex.scaffold.infrastructure.mq.service.MqLocalTransactionHandler;
import org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * RocketMQ 事务消息的本地事务处理器自动配置。
 *
 * <p>仅在消息队列开启、RocketMQ 客户端与模板可用、且存在 {@link DomainEventOutboxStore}
 * 时注册 {@link DefaultMqLocalTransactionHandler}，避免在未接入 RocketMQ 的服务中留下
 * 无人消费的孤立 Bean。业务方声明自定义 {@link MqLocalTransactionHandler} 时本配置让位。</p>
 *
 * @author Apex Team
 * @version 1.1.0
 * @since 1.3.2
 */
@AutoConfiguration(after = RocketMQAutoConfiguration.class)
@ConditionalOnClass({RocketMQTemplate.class, RocketMQLocalTransactionState.class})
@ConditionalOnBean(RocketMQTemplate.class)
@ConditionalOnProperty(prefix = "apex.features", name = "message-queue", havingValue = "true", matchIfMissing = true)
public class MqLocalTransactionAutoConfiguration {

    /**
     * 注册基于 Outbox 的默认本地事务处理器。
     *
     * @param outboxStore 领域事件 Outbox 存储，用于回查本地事务是否已提交
     * @return 默认本地事务处理器；业务方已声明同类型 Bean 时不注册
     */
    @Bean
    @ConditionalOnMissingBean(MqLocalTransactionHandler.class)
    @ConditionalOnBean(DomainEventOutboxStore.class)
    public MqLocalTransactionHandler defaultMqLocalTransactionHandler(DomainEventOutboxStore outboxStore) {
        return new DefaultMqLocalTransactionHandler(outboxStore);
    }
}
