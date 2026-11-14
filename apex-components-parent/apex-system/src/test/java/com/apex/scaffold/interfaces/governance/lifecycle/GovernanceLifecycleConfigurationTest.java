package com.apex.scaffold.interfaces.governance.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.apex.scaffold.infrastructure.mq.listener.DeadLetterQueueProcessor;
import com.apex.scaffold.infrastructure.mq.service.MqConsumerService;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.support.GenericApplicationContext;

/**
 * 验证 MQ 可选依赖在最终容器状态下的排空注册行为。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
class GovernanceLifecycleConfigurationTest {

    /**
     * 验证任一 MQ 依赖缺失时不注册不完整的排空参与者。
     *
     * @param consumerPresent 是否注册普通消费者
     * @param deadLetterPresent 是否注册死信处理器
     */
    @ParameterizedTest
    @CsvSource({"false,false", "true,false", "false,true"})
    void shouldSkipIncompleteMqDependencies(boolean consumerPresent, boolean deadLetterPresent) {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            ResourceDrainRegistry registry = new ResourceDrainRegistry();
            context.registerBean(ResourceDrainRegistry.class, () -> registry);
            if (consumerPresent) {
                context.registerBean(MqConsumerService.class, () -> mock(MqConsumerService.class));
            }
            if (deadLetterPresent) {
                context.registerBean(DeadLetterQueueProcessor.class, () -> mock(DeadLetterQueueProcessor.class));
            }
            context.refresh();

            new GovernanceLifecycleConfiguration().registerMqParticipant(readyEvent(context));

            assertThat(registry.participants()).isEmpty();
        }
    }

    /** 验证两项依赖齐全时只注册一次，并将停止接收操作委托给真实适配器。 */
    @Test
    void shouldRegisterAvailableMqDependenciesOnce() {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            ResourceDrainRegistry registry = new ResourceDrainRegistry();
            MqConsumerService consumer = mock(MqConsumerService.class);
            DeadLetterQueueProcessor deadLetter = mock(DeadLetterQueueProcessor.class);
            context.registerBean(ResourceDrainRegistry.class, () -> registry);
            context.registerBean(MqConsumerService.class, () -> consumer);
            context.registerBean(DeadLetterQueueProcessor.class, () -> deadLetter);
            context.refresh();
            GovernanceLifecycleConfiguration configuration = new GovernanceLifecycleConfiguration();

            configuration.registerMqParticipant(readyEvent(context));
            configuration.registerMqParticipant(readyEvent(context));

            assertThat(registry.participants()).hasSize(1);
            assertThat(registry.participants().get(0).name()).isEqualTo("rocketmq");
            registry.stopAccepting();
            verify(consumer).stopAccepting();
            verify(deadLetter).stopAccepting();
        }
    }

    /**
     * 创建已完成上下文刷新后的应用就绪事件。
     *
     * @param context 测试容器
     * @return 应用就绪事件
     */
    private ApplicationReadyEvent readyEvent(GenericApplicationContext context) {
        return new ApplicationReadyEvent(new SpringApplication(), new String[0], context, Duration.ZERO);
    }
}
