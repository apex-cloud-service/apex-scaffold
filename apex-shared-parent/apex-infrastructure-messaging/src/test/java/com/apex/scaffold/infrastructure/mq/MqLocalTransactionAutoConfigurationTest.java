package com.apex.scaffold.infrastructure.mq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.apex.scaffold.infrastructure.event.DomainEventOutboxStore;
import com.apex.scaffold.infrastructure.mq.service.DefaultMqLocalTransactionHandler;
import com.apex.scaffold.infrastructure.mq.service.MqLocalTransactionHandler;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * {@link MqLocalTransactionAutoConfiguration} 装配条件测试。
 *
 * <p>覆盖默认注册、缺失依赖不注册、特性开关关闭不注册，以及业务自定义实现优先。</p>
 *
 * @author Apex Team
 * @version 1.1.0
 * @since 1.3.2
 */
@DisplayName("RocketMQ 本地事务处理器自动配置")
class MqLocalTransactionAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MqLocalTransactionAutoConfiguration.class));

    @Test
    @DisplayName("模板与 Outbox 存储就绪时注册默认处理器")
    void registersDefaultHandler() {
        withPrerequisites().run(context -> {
            assertThat(context).hasSingleBean(MqLocalTransactionHandler.class);
            assertThat(context.getBean(MqLocalTransactionHandler.class))
                    .isInstanceOf(DefaultMqLocalTransactionHandler.class);
        });
    }

    @Test
    @DisplayName("缺少 Outbox 存储时不注册默认处理器")
    void skipsWithoutOutboxStore() {
        runner.withBean(RocketMQTemplate.class, () -> mock(RocketMQTemplate.class))
                .run(context -> assertThat(context).doesNotHaveBean(MqLocalTransactionHandler.class));
    }

    @Test
    @DisplayName("缺少 RocketMQ 模板时不注册默认处理器")
    void skipsWithoutTemplate() {
        runner.withBean(DomainEventOutboxStore.class, () -> mock(DomainEventOutboxStore.class))
                .run(context -> assertThat(context).doesNotHaveBean(MqLocalTransactionHandler.class));
    }

    @Test
    @DisplayName("消息队列开关关闭时不注册默认处理器")
    void honorsFeatureSwitch() {
        withPrerequisites()
                .withPropertyValues("apex.features.message-queue=false")
                .run(context -> assertThat(context).doesNotHaveBean(MqLocalTransactionHandler.class));
    }

    @Test
    @DisplayName("业务自定义处理器存在时默认实现让位")
    void backsOffWhenCustomHandlerPresent() {
        MqLocalTransactionHandler custom = mock(MqLocalTransactionHandler.class);
        withPrerequisites()
                .withBean(MqLocalTransactionHandler.class, () -> custom)
                .run(context -> {
                    assertThat(context).hasSingleBean(MqLocalTransactionHandler.class);
                    assertThat(context.getBean(MqLocalTransactionHandler.class)).isSameAs(custom);
                });
    }

    private ApplicationContextRunner withPrerequisites() {
        return runner.withBean(RocketMQTemplate.class, () -> mock(RocketMQTemplate.class))
                .withBean(DomainEventOutboxStore.class, () -> mock(DomainEventOutboxStore.class));
    }
}
