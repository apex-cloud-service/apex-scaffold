package com.apex.scaffold.officeservice.interfaces.handler.office;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.apex.scaffold.office.application.OfficeMessageService;
import com.apex.scaffold.office.domain.OfficePlatform;
import com.apex.scaffold.office.domain.message.MessageType;
import com.apex.scaffold.office.domain.message.OfficeMessage;
import com.apex.scaffold.officeservice.interfaces.dto.office.message.SendMessageRequest;
import com.apex.scaffold.officeservice.interfaces.util.RequestHandlerUtil;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("OfficeMessageHandler 单元测试")
class OfficeMessageHandlerTest {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();
    private final RequestHandlerUtil requestHandlerUtil = new RequestHandlerUtil(validator);

    private OfficeMessageService messageService;
    private OfficeMessageHandler handler;

    @BeforeEach
    void setUp() {
        messageService = mock(OfficeMessageService.class);
        handler = new OfficeMessageHandler(messageService, requestHandlerUtil);
    }

    private SendMessageRequest textRequest() {
        return SendMessageRequest.builder()
                .platform("FEISHU")
                .targetType("USER")
                .userIds(List.of("u1"))
                .messageType("TEXT")
                .content("hello")
                .build();
    }

    private OfficeMessage message() {
        return OfficeMessage.builder()
                .id(1L)
                .platform(OfficePlatform.FEISHU)
                .messageType(MessageType.TEXT)
                .sendStatus("SUCCESS")
                .platformMessageId("pm_1")
                .failureReason(null)
                .retryCount(0)
                .createdAt(LocalDateTime.now())
                .sentAt(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("sendMessage")
    class SendMessageTest {
        @Test
        @DisplayName("TEXT 消息 happy path 返回 HTTP 200")
        void textHappyPath() {
            when(messageService.sendMessage(eq(OfficePlatform.FEISHU), any(), any()))
                    .thenReturn(Mono.just(1L));
            when(messageService.getMessageStatus(1L)).thenReturn(Mono.just(message()));

            StepVerifier.create(handler.sendMessage(MockServerRequest.builder().body(Mono.just(textRequest()))))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("MARKDOWN 消息 happy path 返回 HTTP 200")
        void markdownHappyPath() {
            SendMessageRequest body = SendMessageRequest.builder()
                    .platform("DINGTALK")
                    .targetType("GROUP")
                    .groupIds(List.of("g1"))
                    .messageType("MARKDOWN")
                    .content("**bold**")
                    .title("标题")
                    .build();
            when(messageService.sendMessage(eq(OfficePlatform.DINGTALK), any(), any()))
                    .thenReturn(Mono.just(1L));
            when(messageService.getMessageStatus(1L)).thenReturn(Mono.just(message()));

            StepVerifier.create(handler.sendMessage(MockServerRequest.builder().body(Mono.just(body))))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("CARD 消息 cardJson 为空时回退 content，返回 HTTP 200")
        void cardHappyPath() {
            SendMessageRequest body = SendMessageRequest.builder()
                    .platform("WECOM")
                    .targetType("USER")
                    .userIds(List.of("u1"))
                    .messageType("CARD")
                    .content("{\"a\":1}")
                    .title("卡片")
                    .build();
            when(messageService.sendMessage(eq(OfficePlatform.WECOM), any(), any()))
                    .thenReturn(Mono.just(1L));
            when(messageService.getMessageStatus(1L)).thenReturn(Mono.just(message()));

            StepVerifier.create(handler.sendMessage(MockServerRequest.builder().body(Mono.just(body))))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("服务异常时经 handleError 返回 HTTP 400")
        void serviceError() {
            when(messageService.sendMessage(eq(OfficePlatform.FEISHU), any(), any()))
                    .thenReturn(Mono.error(new IllegalStateException("boom")));

            StepVerifier.create(handler.sendMessage(MockServerRequest.builder().body(Mono.just(textRequest()))))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }

        @Test
        @DisplayName("不支持的平台名抛出异常并返回 HTTP 400")
        void unsupportedPlatform() {
            SendMessageRequest body = SendMessageRequest.builder()
                    .platform("SLACK")
                    .targetType("USER")
                    .userIds(List.of("u1"))
                    .messageType("TEXT")
                    .content("hello")
                    .build();

            StepVerifier.create(handler.sendMessage(MockServerRequest.builder().body(Mono.just(body))))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }

        @Test
        @DisplayName("不支持的消息类型抛出异常并返回 HTTP 400")
        void unsupportedMessageType() {
            SendMessageRequest body = SendMessageRequest.builder()
                    .platform("FEISHU")
                    .targetType("USER")
                    .userIds(List.of("u1"))
                    .messageType("VIDEO")
                    .content("hello")
                    .build();

            StepVerifier.create(handler.sendMessage(MockServerRequest.builder().body(Mono.just(body))))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("getMessageStatus")
    class GetMessageStatusTest {
        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            when(messageService.getMessageStatus(9L)).thenReturn(Mono.just(message()));

            StepVerifier.create(handler.getMessageStatus(MockServerRequest.builder()
                            .pathVariable("messageId", "9")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("消息不存在（空 Mono）返回空响应")
        void notFound() {
            when(messageService.getMessageStatus(9L)).thenReturn(Mono.empty());

            StepVerifier.create(handler.getMessageStatus(MockServerRequest.builder()
                            .pathVariable("messageId", "9")
                            .build()))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("resendMessage")
    class ResendMessageTest {
        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            when(messageService.resendMessage(9L)).thenReturn(Mono.just(true));

            StepVerifier.create(handler.resendMessage(MockServerRequest.builder()
                            .pathVariable("messageId", "9")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("服务异常时降级返回 HTTP 200 错误体")
        void serviceError() {
            when(messageService.resendMessage(9L)).thenReturn(Mono.error(new IllegalStateException("状态不允许重发")));

            StepVerifier.create(handler.resendMessage(MockServerRequest.builder()
                            .pathVariable("messageId", "9")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("recallMessage")
    class RecallMessageTest {
        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            when(messageService.recallMessage(9L)).thenReturn(Mono.just(true));

            StepVerifier.create(handler.recallMessage(MockServerRequest.builder()
                            .pathVariable("messageId", "9")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("服务异常时降级返回 HTTP 200 错误体")
        void serviceError() {
            when(messageService.recallMessage(9L)).thenReturn(Mono.error(new IllegalStateException("状态不允许撤回")));

            StepVerifier.create(handler.recallMessage(MockServerRequest.builder()
                            .pathVariable("messageId", "9")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }
    }
}
