package com.apex.scaffold.infrastructure.mq;

import com.apex.scaffold.infrastructure.mq.listener.NotificationBroadcastListener;
import com.apex.scaffold.infrastructure.mq.service.MqConsumerService;
import com.apex.scaffold.infrastructure.notification.NotificationService;
import org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

/**
 * Registers notification consumers after the RocketMQ template and Redisson client are available.
 *
 * @author Apex Team
 * @since 1.3.1
 */
@AutoConfiguration(
        after = RocketMQAutoConfiguration.class,
        afterName = "org.redisson.spring.starter.RedissonAutoConfigurationV2")
@ConditionalOnClass(RocketMQTemplate.class)
@ConditionalOnBean({RocketMQTemplate.class, RedissonClient.class})
@ConditionalOnProperty(prefix = "apex.features", name = "message-queue", havingValue = "true", matchIfMissing = true)
@Import(MqConsumerService.class)
public class NotificationConsumerAutoConfiguration {

    @ConditionalOnBean(NotificationService.class)
    @ConditionalOnProperty(name = "apex.mq.consumer.enabled", havingValue = "true", matchIfMissing = true)
    @Import(NotificationBroadcastListener.class)
    static class BroadcastConfiguration {}
}
