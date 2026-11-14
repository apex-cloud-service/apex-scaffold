package com.apex.scaffold.infrastructure.mq.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.domain.model.entity.OperationLogEntity;
import com.apex.scaffold.domain.model.entity.SystemMessageEntity;
import com.apex.scaffold.domain.repository.OperationLogRepository;
import com.apex.scaffold.domain.repository.UserRepository;
import com.apex.scaffold.domain.service.CacheService;
import com.apex.scaffold.infrastructure.mq.constant.MqConstant;
import com.apex.scaffold.infrastructure.mq.service.MqConsumerService;
import com.apex.scaffold.infrastructure.persistence.repository.UserRegistrationTransaction;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserMessageListener 单元测试")
class UserMessageListenerTest {

    @Mock
    private MqConsumerService mqConsumerService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserRegistrationTransaction userRegistrationTransaction;

    @Mock
    private OperationLogRepository operationLogRepository;

    @Mock
    private CacheService cacheService;

    private UserMessageListener listener;

    /** 监听器在构造时声明的死信消费组映射。 */
    private final AtomicReference<ConsumerGroupRegistration> registered = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        listener = new UserMessageListener(
                mqConsumerService,
                userRepository,
                userRegistrationTransaction,
                operationLogRepository,
                cacheService,
                registered::set,
                MqConstant.TOPIC_USER,
                MqConstant.CONSUMER_GROUP_USER);
    }

    @Test
    @DisplayName("构造时应声明实际消费组供死信路由使用")
    void shouldDeclareConsumerGroupForDlqRouting() {
        assertThat(registered.get())
                .isEqualTo(new ConsumerGroupRegistration(MqConstant.TOPIC_USER, MqConstant.CONSUMER_GROUP_USER));
    }

    @Test
    @DisplayName("非默认Topic与消费组应按实际值声明死信路由并使用实际幂等身份")
    void shouldUseConfiguredTopicAndConsumerGroup() throws Exception {
        AtomicReference<ConsumerGroupRegistration> customRegistered = new AtomicReference<>();
        UserMessageListener customListener = new UserMessageListener(
                mqConsumerService,
                userRepository,
                userRegistrationTransaction,
                operationLogRepository,
                cacheService,
                customRegistered::set,
                "custom-user-topic",
                "custom-user-group");

        assertThat(customRegistered.get())
                .isEqualTo(new ConsumerGroupRegistration("custom-user-topic", "custom-user-group"));

        Map<String, Object> message = message(MqConstant.TAG_USER_LOGIN);
        when(mqConsumerService.processMessage(eq("custom-user-group"), eq("msg-1"), eq(message), any()))
                .thenReturn(true);

        customListener.onMessage(message);

        verify(mqConsumerService).processMessage(eq("custom-user-group"), eq("msg-1"), eq(message), any());
    }

    @Test
    @DisplayName("应声明顺序消费模式")
    void shouldDeclareOrderlyConsumption() {
        RocketMQMessageListener annotation = UserMessageListener.class.getAnnotation(RocketMQMessageListener.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.consumeMode()).isEqualTo(ConsumeMode.ORDERLY);
    }

    @Test
    @DisplayName("Topic与消费组应声明为可配置占位符且默认值回退到生产常量")
    void shouldDeclareConfigurableTopicAndConsumerGroupWithDefaults() {
        RocketMQMessageListener annotation = UserMessageListener.class.getAnnotation(RocketMQMessageListener.class);

        assertThat(annotation).isNotNull();
        org.springframework.core.env.StandardEnvironment environment =
                new org.springframework.core.env.StandardEnvironment();
        assertThat(environment.resolvePlaceholders(annotation.topic())).isEqualTo(MqConstant.TOPIC_USER);
        assertThat(environment.resolvePlaceholders(annotation.consumerGroup()))
                .isEqualTo(MqConstant.CONSUMER_GROUP_USER);
    }

    @Test
    @DisplayName("注册消息应完成欢迎消息和操作日志后确认")
    void shouldHandleUserRegistrationBeforeAcknowledgement() throws Exception {
        Map<String, Object> message = message(MqConstant.TAG_USER_REGISTER);
        when(userRegistrationTransaction.execute(any(SystemMessageEntity.class), any(OperationLogEntity.class)))
                .thenReturn(Mono.empty());
        when(mqConsumerService.processMessage(eq(MqConstant.CONSUMER_GROUP_USER), eq("msg-1"), eq(message), any()))
                .thenAnswer(invocation -> {
                    MqConsumerService.MessageHandler<Map<String, Object>> handler = invocation.getArgument(3);
                    return handler.handle(message);
                });

        listener.onMessage(message);

        verify(userRegistrationTransaction).execute(any(SystemMessageEntity.class), any(OperationLogEntity.class));
    }

    @Test
    @DisplayName("幂等服务拒绝消息时应抛出异常触发重消费")
    void shouldThrowWhenIdempotentProcessingFails() {
        Map<String, Object> message = message(MqConstant.TAG_USER_LOGIN);
        when(mqConsumerService.processMessage(eq(MqConstant.CONSUMER_GROUP_USER), eq("msg-1"), eq(message), any()))
                .thenReturn(false);

        assertThatThrownBy(() -> listener.onMessage(message))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("msg-1");

        verify(userRepository, never()).findById(any());
    }

    @Test
    @DisplayName("消费处理期间应装载消息租户并在完成后清理")
    void shouldPropagateAndClearTenantContext() throws Exception {
        Map<String, Object> message = message(MqConstant.TAG_USER_LOGIN);
        when(mqConsumerService.processMessage(eq(MqConstant.CONSUMER_GROUP_USER), eq("msg-1"), eq(message), any()))
                .thenAnswer(invocation -> {
                    assertThat(TenantContext.currentTenantId()).contains("tenant-a");
                    return true;
                });

        listener.onMessage(message);

        assertThat(TenantContext.currentTenantId()).isEmpty();
    }

    @Test
    @DisplayName("消费结束应恢复调用前的租户上下文")
    void shouldRestorePreviousTenantContext() throws Exception {
        TenantContext.setTenantId("outer-tenant");
        Map<String, Object> message = message(MqConstant.TAG_USER_LOGIN);
        when(mqConsumerService.processMessage(eq(MqConstant.CONSUMER_GROUP_USER), eq("msg-1"), eq(message), any()))
                .thenAnswer(invocation -> {
                    assertThat(TenantContext.currentTenantId()).contains("tenant-a");
                    return true;
                });

        listener.onMessage(message);

        assertThat(TenantContext.currentTenantId()).contains("outer-tenant");
        TenantContext.clear();
    }

    @Test
    @DisplayName("未知标签应作为消费失败处理")
    void shouldRejectUnknownTag() throws Exception {
        Map<String, Object> message = message("UNKNOWN");
        when(mqConsumerService.processMessage(eq(MqConstant.CONSUMER_GROUP_USER), eq("msg-1"), eq(message), any()))
                .thenAnswer(invocation -> {
                    MqConsumerService.MessageHandler<Map<String, Object>> handler = invocation.getArgument(3);
                    return handler.handle(message);
                });

        assertThatThrownBy(() -> listener.onMessage(message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UNKNOWN");
    }

    private Map<String, Object> message(String tag) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("messageId", "msg-1");
        message.put("tag", tag);
        message.put("tenantId", "tenant-a");
        message.put("userId", 42L);
        message.put("nickName", "Apex");
        return message;
    }
}
