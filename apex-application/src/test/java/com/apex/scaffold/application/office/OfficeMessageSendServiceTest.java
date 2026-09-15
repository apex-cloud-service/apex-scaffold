package com.apex.scaffold.application.office;

import com.apex.scaffold.domain.office.message.Message;
import com.apex.scaffold.domain.office.message.MessageRepository;
import com.apex.scaffold.domain.office.message.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Arrays;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 消息发送服务测试
 *
 * @author Apex Team
 * @since 1.3.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("消息发送服务测试")
class OfficeMessageSendServiceTest {

    @Mock
    private MessageService feishuService;

    @Mock
    private MessageService dingtalkService;

    @Mock
    private MessageService wecomService;

    @Mock
    private MessageRepository messageRepository;

    @InjectMocks
    private OfficeMessageSendService sendService;

    private Message mockMessage;

    @BeforeEach
    void setUp() {
        mockMessage = Message.builder()
                .id(1L)
                .tenantId(1L)
                .platform("FEISHU")
                .messageType("TEXT")
                .content("测试消息")
                .receiverType("USER")
                .receiverIds(Arrays.asList("user_001"))
                .platformMessageId("msg_001")
                .status("SENT")
                .build();
    }

    @Test
    @DisplayName("发送文本消息 - 飞书 - 成功")
    void testSendTextMessage_Feishu_Success() {
        // Given
        Long tenantId = 1L;
        Long appId = 100L;
        String platform = "FEISHU";

        when(feishuService.sendMessage(eq(tenantId), eq(appId), any(Message.class)))
                .thenReturn(Mono.just("msg_001"));
        when(messageRepository.save(any(Message.class)))
                .thenReturn(Mono.just(mockMessage));

        // When & Then
        StepVerifier.create(sendService.sendMessage(tenantId, appId, platform, mockMessage))
                .expectNext("msg_001")
                .verifyComplete();

        verify(feishuService, times(1)).sendMessage(eq(tenantId), eq(appId), any(Message.class));
        verify(messageRepository, times(1)).save(any(Message.class));
    }

    @Test
    @DisplayName("发送图片消息 - 钉钉 - 成功")
    void testSendImageMessage_Dingtalk_Success() {
        // Given
        Long tenantId = 1L;
        Long appId = 100L;
        String platform = "DINGTALK";
        Message imageMessage = mockMessage.toBuilder()
                .messageType("IMAGE")
                .platform("DINGTALK")
                .content("https://example.com/image.png")
                .build();

        when(dingtalkService.sendMessage(eq(tenantId), eq(appId), any(Message.class)))
                .thenReturn(Mono.just("msg_002"));
        when(messageRepository.save(any(Message.class)))
                .thenReturn(Mono.just(imageMessage));

        // When & Then
        StepVerifier.create(sendService.sendMessage(tenantId, appId, platform, imageMessage))
                .expectNext("msg_002")
                .verifyComplete();

        verify(dingtalkService, times(1)).sendMessage(eq(tenantId), eq(appId), any(Message.class));
    }

    @Test
    @DisplayName("发送卡片消息 - 企业微信 - 成功")
    void testSendCardMessage_Wecom_Success() {
        // Given
        Long tenantId = 1L;
        Long appId = 100L;
        String platform = "WECOM";
        Message cardMessage = mockMessage.toBuilder()
                .messageType("CARD")
                .platform("WECOM")
                .content("{\"title\":\"通知\",\"description\":\"这是一条通知\"}")
                .build();

        when(wecomService.sendMessage(eq(tenantId), eq(appId), any(Message.class)))
                .thenReturn(Mono.just("msg_003"));
        when(messageRepository.save(any(Message.class)))
                .thenReturn(Mono.just(cardMessage));

        // When & Then
        StepVerifier.create(sendService.sendMessage(tenantId, appId, platform, cardMessage))
                .expectNext("msg_003")
                .verifyComplete();

        verify(wecomService, times(1)).sendMessage(eq(tenantId), eq(appId), any(Message.class));
    }

    @Test
    @DisplayName("发送消息 - 平台服务异常")
    void testSendMessage_PlatformServiceError() {
        // Given
        Long tenantId = 1L;
        Long appId = 100L;
        String platform = "FEISHU";

        when(feishuService.sendMessage(eq(tenantId), eq(appId), any(Message.class)))
                .thenReturn(Mono.error(new RuntimeException("平台服务异常")));

        // When & Then
        StepVerifier.create(sendService.sendMessage(tenantId, appId, platform, mockMessage))
                .expectError(RuntimeException.class)
                .verify();

        verify(messageRepository, never()).save(any(Message.class));
    }

    @Test
    @DisplayName("发送消息 - 数据库保存异常")
    void testSendMessage_RepositoryError() {
        // Given
        Long tenantId = 1L;
        Long appId = 100L;
        String platform = "FEISHU";

        when(feishuService.sendMessage(eq(tenantId), eq(appId), any(Message.class)))
                .thenReturn(Mono.just("msg_001"));
        when(messageRepository.save(any(Message.class)))
                .thenReturn(Mono.error(new RuntimeException("数据库异常")));

        // When & Then
        StepVerifier.create(sendService.sendMessage(tenantId, appId, platform, mockMessage))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    @DisplayName("群发消息 - 多个接收人")
    void testSendMessageToMultipleReceivers_Success() {
        // Given
        Long tenantId = 1L;
        Long appId = 100L;
        String platform = "FEISHU";
        Message multiReceiverMessage = mockMessage.toBuilder()
                .receiverIds(Arrays.asList("user_001", "user_002", "user_003"))
                .build();

        when(feishuService.sendMessage(eq(tenantId), eq(appId), any(Message.class)))
                .thenReturn(Mono.just("msg_batch_001"));
        when(messageRepository.save(any(Message.class)))
                .thenReturn(Mono.just(multiReceiverMessage));

        // When & Then
        StepVerifier.create(sendService.sendMessage(tenantId, appId, platform, multiReceiverMessage))
                .expectNext("msg_batch_001")
                .verifyComplete();

        verify(feishuService, times(1)).sendMessage(eq(tenantId), eq(appId), any(Message.class));
    }

    @Test
    @DisplayName("发送部门消息 - 成功")
    void testSendDepartmentMessage_Success() {
        // Given
        Long tenantId = 1L;
        Long appId = 100L;
        String platform = "FEISHU";
        Message deptMessage = mockMessage.toBuilder()
                .receiverType("DEPARTMENT")
                .receiverIds(Arrays.asList("dept_001"))
                .build();

        when(feishuService.sendMessage(eq(tenantId), eq(appId), any(Message.class)))
                .thenReturn(Mono.just("msg_dept_001"));
        when(messageRepository.save(any(Message.class)))
                .thenReturn(Mono.just(deptMessage));

        // When & Then
        StepVerifier.create(sendService.sendMessage(tenantId, appId, platform, deptMessage))
                .expectNext("msg_dept_001")
                .verifyComplete();
    }

    @Test
    @DisplayName("发送群组消息 - 成功")
    void testSendGroupMessage_Success() {
        // Given
        Long tenantId = 1L;
        Long appId = 100L;
        String platform = "FEISHU";
        Message groupMessage = mockMessage.toBuilder()
                .receiverType("GROUP")
                .receiverIds(Arrays.asList("group_001"))
                .build();

        when(feishuService.sendMessage(eq(tenantId), eq(appId), any(Message.class)))
                .thenReturn(Mono.just("msg_group_001"));
        when(messageRepository.save(any(Message.class)))
                .thenReturn(Mono.just(groupMessage));

        // When & Then
        StepVerifier.create(sendService.sendMessage(tenantId, appId, platform, groupMessage))
                .expectNext("msg_group_001")
                .verifyComplete();
    }

    @Test
    @DisplayName("查询消息状态 - 成功")
    void testGetMessageStatus_Success() {
        // Given
        String messageId = "msg_001";

        when(messageRepository.findByPlatformMessageId(eq(messageId)))
                .thenReturn(Mono.just(mockMessage));

        // When & Then
        StepVerifier.create(sendService.getMessageStatus(messageId))
                .expectNext(mockMessage)
                .verifyComplete();

        verify(messageRepository, times(1)).findByPlatformMessageId(eq(messageId));
    }

    @Test
    @DisplayName("查询消息状态 - 消息不存在")
    void testGetMessageStatus_NotFound() {
        // Given
        String messageId = "msg_not_exist";

        when(messageRepository.findByPlatformMessageId(eq(messageId)))
                .thenReturn(Mono.empty());

        // When & Then
        StepVerifier.create(sendService.getMessageStatus(messageId))
                .expectComplete()
                .verify();
    }
}
