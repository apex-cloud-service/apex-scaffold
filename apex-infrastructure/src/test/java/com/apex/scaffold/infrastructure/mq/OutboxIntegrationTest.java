package com.apex.scaffold.infrastructure.mq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.infrastructure.mq.constant.MqConstant;
import com.apex.scaffold.infrastructure.mq.service.MqProducerService;
import com.apex.scaffold.infrastructure.outbox.entity.MessageOutboxEntity;
import com.apex.scaffold.infrastructure.outbox.mapper.MessageOutboxMapper;
import com.apex.scaffold.infrastructure.outbox.service.impl.MessageOutboxServiceImpl;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 消息发送 Outbox 模式行为测试。
 *
 * <p>验证 {@link MqProducerService} 的 Outbox 集成行为：同步发送先落箱后投递并标记
 * DELIVERED；异步发送失败时兜底落箱（PENDING）等待发件箱调度器重试。
 *
 * <p>infrastructure 模块没有 Spring 测试上下文（{@code @SpringBootApplication} 位于
 * apex-interfaces），故采用与 {@code MqProducerServiceTest} 一致的 Mockito 风格，
 * 通过真实 {@link MessageOutboxServiceImpl} 与捕获 {@code Mapper} 写入参数完成断言。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.2.0
 */
@DisplayName("MqProducerService Outbox 集成行为")
@ExtendWith(MockitoExtension.class)
class OutboxIntegrationTest {

    @Mock
    private RocketMQTemplate rocketMQTemplate;

    @Mock
    private MessageOutboxMapper messageOutboxMapper;

    private MqProducerService mqProducerService;

    /**
     * 构造被测服务与真实发件箱服务（Mapper 使用 Mockito 捕获写入参数）。
     *
     * <p>无 MyBatis 运行环境的纯单元测试需手动初始化实体的 Lambda 列缓存，
     * 否则 {@code LambdaUpdateWrapper} 在 markAsDelivered 内构建时会抛出
     * "can not find lambda cache" 异常。
     */
    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), MessageOutboxEntity.class);
        MessageOutboxServiceImpl messageOutboxService = new MessageOutboxServiceImpl(messageOutboxMapper);
        mqProducerService = new MqProducerService(rocketMQTemplate, new ObjectMapper(), messageOutboxService);
    }

    /**
     * 同步发送 + Outbox：应先以 PENDING 状态落箱，随后投递并标记 DELIVERED。
     */
    @Test
    @DisplayName("sendSyncWithOutbox 应先持久化 PENDING 消息到发件箱")
    void testSendSyncWithOutbox_shouldPersistToOutbox() {
        Map<String, Object> message = new HashMap<>();
        message.put("userId", "test-user-123");
        message.put("action", "register");
        when(rocketMQTemplate.syncSend(anyString(), any(org.springframework.messaging.Message.class)))
                .thenReturn(new SendResult());

        mqProducerService.sendSyncWithOutbox(MqConstant.TOPIC_USER, MqConstant.TAG_USER_REGISTER, message);

        ArgumentCaptor<MessageOutboxEntity> insertCaptor = ArgumentCaptor.forClass(MessageOutboxEntity.class);
        verify(messageOutboxMapper).insert(insertCaptor.capture());
        MessageOutboxEntity persisted = insertCaptor.getValue();
        assertThat(persisted.getTopic()).isEqualTo(MqConstant.TOPIC_USER);
        assertThat(persisted.getTag()).isEqualTo(MqConstant.TAG_USER_REGISTER);
        assertThat(persisted.getStatus()).isEqualTo(MessageOutboxEntity.Status.PENDING);
        assertThat(persisted.getPayload()).contains("test-user-123");
        assertThat(persisted.getMessageId()).isNotBlank();

        verify(messageOutboxMapper).update(isNull(), any());
    }

    /**
     * 异步发送失败 + Outbox 兜底：应将消息以 PENDING 状态落箱等待重试。
     */
    @Test
    @DisplayName("sendAsyncWithFallback 失败时应兜底持久化到发件箱")
    void testSendAsyncWithFallback_shouldPersistToOutboxOnFailure() {
        Map<String, Object> message = new HashMap<>();
        message.put("userId", "test-user-456");
        message.put("action", "login");
        doThrow(new RuntimeException("invalid topic"))
                .when(rocketMQTemplate)
                .asyncSend(
                        anyString(),
                        any(org.springframework.messaging.Message.class),
                        any(org.apache.rocketmq.client.producer.SendCallback.class));

        mqProducerService.sendAsyncWithFallback("INVALID_TOPIC", MqConstant.TAG_USER_LOGIN, message);

        ArgumentCaptor<MessageOutboxEntity> insertCaptor = ArgumentCaptor.forClass(MessageOutboxEntity.class);
        verify(messageOutboxMapper).insert(insertCaptor.capture());
        MessageOutboxEntity persisted = insertCaptor.getValue();
        assertThat(persisted.getTopic()).isEqualTo("INVALID_TOPIC");
        assertThat(persisted.getStatus()).isEqualTo(MessageOutboxEntity.Status.PENDING);
        assertThat(persisted.getPayload()).contains("test-user-456");
    }
}
