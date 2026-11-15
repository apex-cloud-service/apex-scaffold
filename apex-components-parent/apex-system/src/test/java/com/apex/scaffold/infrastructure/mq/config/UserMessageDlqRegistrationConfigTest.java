package com.apex.scaffold.infrastructure.mq.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.apex.scaffold.infrastructure.mq.listener.ConsumerGroupRegistration;
import com.apex.scaffold.infrastructure.mq.listener.DeadLetterQueueProcessor;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 用户死信消费组声明装配的条件矩阵测试。
 *
 * <p>验证 {@link UserMessageDlqRegistrationConfig} 在死信处理器存在/缺席两态下的装配行为：
 * 死信开关关闭（或 MQ 消费关闭、消息队列特性关闭）时处理器 Bean 缺席，装配必须退化为
 * 空操作而非上下文启动失败。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.1
 */
@DisplayName("UserMessageDlqRegistrationConfig 条件装配矩阵")
class UserMessageDlqRegistrationConfigTest {

    /** 声明回调在容器中的 Bean 名称（类型擦除后按名获取）。 */
    private static final String REGISTRAR_BEAN = "userMessageDlqConsumerGroupRegistrar";

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(UserMessageDlqRegistrationConfig.class);

    @Test
    @DisplayName("死信处理器存在时应装配真实转发回调并登记消费组映射")
    void shouldAssembleForwardingRegistrarWhenProcessorPresent() {
        runner.withBean(
                        DeadLetterQueueProcessor.class,
                        () -> new DeadLetterQueueProcessor("", "apex-dlq-processor-dlq-consumer", null))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    @SuppressWarnings("unchecked")
                    Consumer<ConsumerGroupRegistration> registrar =
                            (Consumer<ConsumerGroupRegistration>) context.getBean(REGISTRAR_BEAN);

                    registrar.accept(new ConsumerGroupRegistration("biz-topic", "biz-consumer-group"));

                    DeadLetterQueueProcessor processor = context.getBean(DeadLetterQueueProcessor.class);
                    @SuppressWarnings("unchecked")
                    Map<String, String> registry =
                            (Map<String, String>) ReflectionTestUtils.getField(processor, "consumerGroupRegistry");
                    assertThat(registry).containsEntry("biz-topic", "biz-consumer-group");
                });
    }

    @Test
    @DisplayName("死信处理器缺席时装配不得失败且回调为空操作")
    void shouldAssembleNoOpRegistrarWhenProcessorAbsent() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            @SuppressWarnings("unchecked")
            Consumer<ConsumerGroupRegistration> registrar =
                    (Consumer<ConsumerGroupRegistration>) context.getBean(REGISTRAR_BEAN);

            assertThatCode(() -> registrar.accept(new ConsumerGroupRegistration("biz-topic", "biz-consumer-group")))
                    .doesNotThrowAnyException();
        });
    }

    @Test
    @DisplayName("DLQ开关关闭时上下文应正常装配")
    void shouldAssembleWhenDlqDisabled() {
        runner.withPropertyValues("apex.mq.dlq.enabled=false").run(context -> assertThat(context)
                .hasNotFailed()
                .getBean(REGISTRAR_BEAN)
                .isNotNull());
    }

    @Test
    @DisplayName("MQ消费开关关闭时上下文应装配成功")
    void shouldAssembleWhenConsumerDisabled() {
        runner.withPropertyValues("apex.mq.consumer.enabled=false").run(context -> assertThat(context)
                .hasNotFailed()
                .getBean(REGISTRAR_BEAN)
                .isNotNull());
    }
}
