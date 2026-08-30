package com.apex.scaffold.infrastructure.mq.listener;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.domain.model.entity.OperationLogEntity;
import com.apex.scaffold.domain.model.entity.SystemMessageEntity;
import com.apex.scaffold.domain.repository.OperationLogRepository;
import com.apex.scaffold.domain.repository.SystemMessageRepository;
import com.apex.scaffold.domain.repository.UserRepository;
import com.apex.scaffold.domain.service.CacheService;
import com.apex.scaffold.infrastructure.mq.constant.MqConstant;
import com.apex.scaffold.infrastructure.mq.service.MqConsumerService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
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
    private SystemMessageRepository systemMessageRepository;

    @Mock
    private OperationLogRepository operationLogRepository;

    @Mock
    private CacheService cacheService;

    private UserMessageListener listener;

    @BeforeEach
    void setUp() {
        listener = new UserMessageListener(
                mqConsumerService,
                userRepository,
                systemMessageRepository,
                operationLogRepository,
                cacheService);
    }

    @Test
    @DisplayName("应声明顺序消费模式")
    void shouldDeclareOrderlyConsumption() {
        RocketMQMessageListener annotation = UserMessageListener.class
                .getAnnotation(RocketMQMessageListener.class);

        org.assertj.core.api.Assertions.assertThat(annotation).isNotNull();
        org.assertj.core.api.Assertions.assertThat(annotation.consumeMode())
                .isEqualTo(ConsumeMode.ORDERLY);
    }

    @Test
    @DisplayName("注册消息应完成欢迎消息和操作日志后确认")
    void shouldHandleUserRegistrationBeforeAcknowledgement() throws Exception {
        Map<String, Object> message = message(MqConstant.TAG_USER_REGISTER);
        when(systemMessageRepository.save(any(SystemMessageEntity.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(operationLogRepository.insert(any(OperationLogEntity.class))).thenReturn(Mono.just(1));
        when(mqConsumerService.processMessage(eq("msg-1"), eq(message), any()))
                .thenAnswer(invocation -> {
                    MqConsumerService.MessageHandler<Map<String, Object>> handler = invocation.getArgument(2);
                    return handler.handle(message);
                });

        listener.onMessage(message);

        verify(systemMessageRepository).save(any(SystemMessageEntity.class));
        verify(operationLogRepository).insert(any(OperationLogEntity.class));
    }

    @Test
    @DisplayName("幂等服务拒绝消息时应抛出异常触发重消费")
    void shouldThrowWhenIdempotentProcessingFails() {
        Map<String, Object> message = message(MqConstant.TAG_USER_LOGIN);
        when(mqConsumerService.processMessage(eq("msg-1"), eq(message), any())).thenReturn(false);

        assertThatThrownBy(() -> listener.onMessage(message))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("msg-1");

        verify(userRepository, never()).findById(any());
    }

    @Test
    @DisplayName("未知标签应作为消费失败处理")
    void shouldRejectUnknownTag() throws Exception {
        Map<String, Object> message = message("UNKNOWN");
        when(mqConsumerService.processMessage(eq("msg-1"), eq(message), any()))
                .thenAnswer(invocation -> {
                    MqConsumerService.MessageHandler<Map<String, Object>> handler = invocation.getArgument(2);
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
        message.put("userId", 42L);
        message.put("nickName", "Apex");
        return message;
    }
}
