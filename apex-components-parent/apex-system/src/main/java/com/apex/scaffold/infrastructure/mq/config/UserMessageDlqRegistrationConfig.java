package com.apex.scaffold.infrastructure.mq.config;

import com.apex.scaffold.infrastructure.mq.listener.ConsumerGroupRegistration;
import com.apex.scaffold.infrastructure.mq.listener.DeadLetterQueueProcessor;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 用户消息监听器的死信消费组声明装配。
 *
 * <p>{@code UserMessageListener} 是普通 Spring 组件，由构造器注入创建。本配置以
 * {@link ObjectProvider} 间接引用 {@link DeadLetterQueueProcessor}：当死信开关
 * （{@code apex.mq.dlq.enabled}）、MQ 消费开关或消息队列特性关闭导致处理器 Bean 缺席时，
 * 回调退化为空操作，监听器装配不受阻断；处理器存在时则把实际消费组声明转发给它登记。
 *
 * @author Apex Team
 * @version 1.0.1
 * @since 1.3.1
 * @see DeadLetterQueueProcessor#registerConsumerGroup(String, String)
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class UserMessageDlqRegistrationConfig {

    /**
     * 创建死信消费组声明回调。
     *
     * <p>回调在调用时解析处理器：处理器缺席（如 DLQ 关闭）时丢弃声明并记录调试日志，
     * 不抛出异常；存在时登记原始 Topic 与消费组的映射。
     *
     * @param processorProvider 死信队列处理器的延迟提供者
     * @return 接收消费组声明的回调
     */
    @Bean
    public Consumer<ConsumerGroupRegistration> userMessageDlqConsumerGroupRegistrar(
            ObjectProvider<DeadLetterQueueProcessor> processorProvider) {
        return registration -> {
            DeadLetterQueueProcessor processor = processorProvider.getIfAvailable();
            if (processor == null) {
                log.debug(
                        "DLQ 处理器缺席, 丢弃死信消费组声明, topic: {}, consumerGroup: {}",
                        registration.originalTopic(),
                        registration.consumerGroup());
                return;
            }
            processor.registerConsumerGroup(registration.originalTopic(), registration.consumerGroup());
        };
    }
}
