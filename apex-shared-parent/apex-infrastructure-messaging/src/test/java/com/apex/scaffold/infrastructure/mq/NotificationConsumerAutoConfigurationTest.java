package com.apex.scaffold.infrastructure.mq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.apex.scaffold.infrastructure.mq.listener.NotificationBroadcastListener;
import com.apex.scaffold.infrastructure.mq.service.MqConsumerService;
import com.apex.scaffold.infrastructure.notification.NotificationService;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class NotificationConsumerAutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(NotificationConsumerAutoConfiguration.class))
            .withBean(RedissonClient.class, () -> mock(RedissonClient.class))
            .withBean(NotificationService.class, () -> mock(NotificationService.class));

    @Test
    void registersOneBroadcastListenerWhenTemplateExists() {
        withTemplate().run(context -> {
            assertThat(context).hasSingleBean(MqConsumerService.class);
            assertThat(context).hasSingleBean(NotificationBroadcastListener.class);
        });
    }

    @Test
    void doesNotRegisterListenerWithoutTemplate() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(MqConsumerService.class);
            assertThat(context).doesNotHaveBean(NotificationBroadcastListener.class);
        });
    }

    @Test
    void honorsBothFeatureSwitches() {
        withTemplate().withPropertyValues("apex.features.message-queue=false").run(context -> assertThat(context)
                .doesNotHaveBean(NotificationBroadcastListener.class));
        withTemplate().withPropertyValues("apex.mq.consumer.enabled=false").run(context -> assertThat(context)
                .doesNotHaveBean(NotificationBroadcastListener.class));
    }

    private ApplicationContextRunner withTemplate() {
        return runner.withBean(RocketMQTemplate.class, () -> mock(RocketMQTemplate.class));
    }
}
