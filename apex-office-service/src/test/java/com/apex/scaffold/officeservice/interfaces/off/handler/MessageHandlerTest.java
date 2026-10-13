package com.apex.scaffold.officeservice.interfaces.off.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.apex.scaffold.office.off.application.dto.MessageDTO;
import com.apex.scaffold.office.off.application.dto.SendMessageRequest;
import com.apex.scaffold.office.off.application.service.OffMessageService;
import com.apex.scaffold.officeservice.interfaces.security.AuthenticatedUser;
import com.apex.scaffold.officeservice.interfaces.util.RequestHandlerUtil;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("MessageHandler 单元测试")
class MessageHandlerTest {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();
    private final RequestHandlerUtil requestHandlerUtil = new RequestHandlerUtil(validator);

    private OffMessageService messageService;
    private MessageHandler handler;

    @BeforeEach
    void setUp() {
        messageService = mock(OffMessageService.class);
        handler = new MessageHandler(messageService, requestHandlerUtil);
    }

    private UsernamePasswordAuthenticationToken auth() {
        return new UsernamePasswordAuthenticationToken(new AuthenticatedUser(100L, "openid", "1"), null, List.of());
    }

    private MessageDTO message() {
        return MessageDTO.builder().id(1L).subject("主题").content("内容").build();
    }

    @Nested
    @DisplayName("sendMessage")
    class SendMessageTest {
        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            SendMessageRequest body = SendMessageRequest.builder()
                    .subject("主题")
                    .content("内容")
                    .recipientIds(List.of(2L))
                    .build();
            when(messageService.sendMessage(any(SendMessageRequest.class), eq(100L)))
                    .thenReturn(Mono.just(message()));

            StepVerifier.create(handler.sendMessage(MockServerRequest.builder().body(Mono.just(body)))
                            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth())))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("服务异常时返回 HTTP 400")
        void serviceError() {
            SendMessageRequest body = SendMessageRequest.builder()
                    .subject("主题")
                    .content("内容")
                    .recipientIds(List.of(2L))
                    .build();
            when(messageService.sendMessage(any(SendMessageRequest.class), eq(100L)))
                    .thenReturn(Mono.error(new IllegalStateException("boom")));

            StepVerifier.create(handler.sendMessage(MockServerRequest.builder().body(Mono.just(body)))
                            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth())))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }

        @Test
        @DisplayName("未认证时返回 HTTP 400")
        void noAuth() {
            SendMessageRequest body = SendMessageRequest.builder()
                    .subject("主题")
                    .content("内容")
                    .recipientIds(List.of(2L))
                    .build();

            StepVerifier.create(handler.sendMessage(MockServerRequest.builder().body(Mono.just(body))))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("getInbox")
    class GetInboxTest {
        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            when(messageService.getInbox(eq(100L), anyInt(), anyInt())).thenReturn(Flux.just(message()));

            StepVerifier.create(handler.getInbox(MockServerRequest.builder()
                                    .queryParam("pageNum", "1")
                                    .queryParam("pageSize", "10")
                                    .build())
                            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth())))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("空列表返回 HTTP 200")
        void emptyList() {
            when(messageService.getInbox(eq(100L), anyInt(), anyInt())).thenReturn(Flux.empty());

            StepVerifier.create(handler.getInbox(MockServerRequest.builder().build())
                            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth())))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("未认证时返回 HTTP 400")
        void noAuth() {
            StepVerifier.create(handler.getInbox(MockServerRequest.builder().build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("getSentMessages")
    class GetSentMessagesTest {
        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            when(messageService.getSentMessages(eq(100L), anyInt(), anyInt())).thenReturn(Flux.just(message()));

            StepVerifier.create(
                            handler.getSentMessages(MockServerRequest.builder().build())
                                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth())))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("未认证时返回 HTTP 400")
        void noAuth() {
            StepVerifier.create(
                            handler.getSentMessages(MockServerRequest.builder().build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("getMessageDetail")
    class GetMessageDetailTest {
        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            when(messageService.getMessageDetail(9L)).thenReturn(Mono.just(message()));

            StepVerifier.create(handler.getMessageDetail(MockServerRequest.builder()
                            .pathVariable("messageId", "9")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("消息不存在返回 HTTP 400")
        void notFound() {
            when(messageService.getMessageDetail(9L)).thenReturn(Mono.empty());

            StepVerifier.create(handler.getMessageDetail(MockServerRequest.builder()
                            .pathVariable("messageId", "9")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }

        @Test
        @DisplayName("消息ID非法返回 HTTP 400")
        void invalidId() {
            StepVerifier.create(handler.getMessageDetail(MockServerRequest.builder()
                            .pathVariable("messageId", "abc")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("markAsRead")
    class MarkAsReadTest {
        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            when(messageService.markAsRead(9L, 100L)).thenReturn(Mono.empty());

            StepVerifier.create(handler.markAsRead(MockServerRequest.builder()
                                    .pathVariable("messageId", "9")
                                    .build())
                            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth())))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("消息ID非法返回 HTTP 400")
        void invalidId() {
            StepVerifier.create(handler.markAsRead(MockServerRequest.builder()
                            .pathVariable("messageId", "abc")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }

        @Test
        @DisplayName("未认证时返回 HTTP 400")
        void noAuth() {
            StepVerifier.create(handler.markAsRead(MockServerRequest.builder()
                            .pathVariable("messageId", "9")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("markAllAsRead")
    class MarkAllAsReadTest {
        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            when(messageService.markAllAsRead(100L)).thenReturn(Mono.empty());

            StepVerifier.create(
                            handler.markAllAsRead(MockServerRequest.builder().build())
                                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth())))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("未认证时返回 HTTP 400")
        void noAuth() {
            StepVerifier.create(
                            handler.markAllAsRead(MockServerRequest.builder().build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("getUnreadCount")
    class GetUnreadCountTest {
        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            when(messageService.countUnread(100L)).thenReturn(Mono.just(5L));

            StepVerifier.create(
                            handler.getUnreadCount(MockServerRequest.builder().build())
                                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth())))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("未认证时返回 HTTP 400")
        void noAuth() {
            StepVerifier.create(
                            handler.getUnreadCount(MockServerRequest.builder().build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }
    }
}
