package com.apex.scaffold.office.application;

import static org.mockito.Mockito.*;

import com.apex.scaffold.office.domain.OfficePlatform;
import com.apex.scaffold.office.domain.message.MessageTarget;
import com.apex.scaffold.office.domain.message.OfficeMessage;
import com.apex.scaffold.office.domain.message.OfficeMessageRepository;
import com.apex.scaffold.office.domain.message.TextMessageContent;
import com.apex.scaffold.office.domain.port.MessagePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class OfficeMessageSendServiceTest {
    @Mock
    private OfficeMessageRepository repository;

    @Mock
    private MessagePort feishuMessageClient;

    @Mock
    private MessagePort dingTalkMessageClient;

    @Mock
    private MessagePort wecomMessageClient;

    private OfficeMessageService service;

    @BeforeEach
    void setUp() {
        service = new OfficeMessageService(repository, feishuMessageClient, dingTalkMessageClient, wecomMessageClient);
    }

    @Test
    void getMessageStatus_missingMessageCompletesEmpty() {
        when(repository.findById(1L)).thenReturn(Mono.empty());
        StepVerifier.create(service.getMessageStatus(1L)).verifyComplete();
    }

    @Test
    void resendMessage_rejectsMessageThatReachedRetryLimit() {
        OfficeMessage message = OfficeMessage.builder()
                .id(1L)
                .platform(OfficePlatform.FEISHU)
                .target(MessageTarget.builder()
                        .targetType("USER")
                        .userIds(java.util.List.of("user-1"))
                        .build())
                .content(TextMessageContent.builder().text("hello").build())
                .sendStatus("FAILED")
                .retryCount(3)
                .build();
        when(repository.findById(1L)).thenReturn(Mono.just(message));

        StepVerifier.create(service.resendMessage(1L))
                .expectErrorSatisfies(error -> org.assertj.core.api.Assertions.assertThat(error)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("消息重试次数已达上限"))
                .verify();
        verifyNoInteractions(feishuMessageClient, dingTalkMessageClient, wecomMessageClient);
    }

    @Test
    void recallMessage_rejectsMessageThatWasNotSentSuccessfully() {
        OfficeMessage message = OfficeMessage.builder()
                .id(1L)
                .platform(OfficePlatform.FEISHU)
                .target(MessageTarget.builder()
                        .targetType("USER")
                        .userIds(java.util.List.of("user-1"))
                        .build())
                .content(TextMessageContent.builder().text("hello").build())
                .sendStatus("FAILED")
                .build();
        when(repository.findById(1L)).thenReturn(Mono.just(message));

        StepVerifier.create(service.recallMessage(1L))
                .expectErrorSatisfies(error -> org.assertj.core.api.Assertions.assertThat(error)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("消息状态不支持撤回"))
                .verify();
        verifyNoInteractions(feishuMessageClient, dingTalkMessageClient, wecomMessageClient);
    }

    @Test
    void recallMessage_routesToPlatformClientAndMarksRecalled() {
        OfficeMessage message = OfficeMessage.builder()
                .id(1L)
                .platform(OfficePlatform.FEISHU)
                .target(MessageTarget.builder()
                        .targetType("USER")
                        .userIds(java.util.List.of("user-1"))
                        .build())
                .content(TextMessageContent.builder().text("hello").build())
                .sendStatus("SUCCESS")
                .platformMessageId("om_123")
                .build();
        when(repository.findById(1L)).thenReturn(Mono.just(message));
        when(feishuMessageClient.recallMessage("om_123")).thenReturn(Mono.just(true));
        when(repository.updateStatus(message)).thenReturn(Mono.just(message));

        StepVerifier.create(service.recallMessage(1L)).expectNext(true).verifyComplete();

        org.assertj.core.api.Assertions.assertThat(message.getSendStatus()).isEqualTo("RECALLED");
        verifyNoInteractions(dingTalkMessageClient, wecomMessageClient);
    }
}
