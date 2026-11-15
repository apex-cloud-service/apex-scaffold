package com.apex.scaffold.knowledgeservice.interfaces.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.knowledge.application.service.KnowledgeChatAppService;
import com.apex.scaffold.knowledgeservice.interfaces.router.KnowledgeChatRouter;
import com.apex.scaffold.knowledgeservice.interfaces.security.AuthenticatedUser;
import com.apex.scaffold.knowledgeservice.interfaces.util.KnowledgeRequestHandlerUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ChatSseErrorTest {

    @Mock
    private KnowledgeChatAppService chatAppService;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        var util = new KnowledgeRequestHandlerUtil(
                Validation.buildDefaultValidatorFactory().getValidator());
        var handler = new KnowledgeChatHandler(chatAppService, util);
        client = WebTestClient.bindToRouterFunction(new KnowledgeChatRouter().knowledgeChatRoutes(handler))
                .webFilter((exchange, chain) -> chain.filter(exchange)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                                new UsernamePasswordAuthenticationToken(
                                        new AuthenticatedUser(7L, "t1"), null, List.of()))))
                .build();
    }

    @Test
    void validationFailureKeepsHttp200AndReturnsJsonError() {
        StepVerifier.create(ask("{\"question\":\" \"}"))
                .assertNext(event -> assertError(event, "400", "提问内容不能为空"))
                .verifyComplete();
        verifyNoInteractions(chatAppService);
    }

    @Test
    void conversationBusinessFailurePreservesSafeMessage() {
        when(chatAppService.ask(eq(7L), any()))
                .thenReturn(Flux.error(new BusinessException(ResultCode.NOT_FOUND, "问答会话不存在")));
        StepVerifier.create(ask("{\"question\":\"提问\"}"))
                .assertNext(event -> assertError(event, "404", "问答会话不存在"))
                .verifyComplete();
    }

    @Test
    void technicalFailureDoesNotExposeThrowableDetails() {
        when(chatAppService.ask(eq(7L), any()))
                .thenReturn(Flux.error(new IllegalStateException("upstream-secret /private/model")));
        StepVerifier.create(ask("{\"question\":\"提问\"}"))
                .assertNext(event -> assertError(event, "-1", "问答服务暂时不可用，请稍后重试"))
                .verifyComplete();
    }

    @Test
    void errorAfterDeltaPreservesDeltaAndReturnsOneJsonError() {
        when(chatAppService.ask(eq(7L), any()))
                .thenReturn(
                        Flux.just(ServerSentEvent.builder("回答片段").event("delta").build())
                                .concatWith(Flux.error(new AssertionError("private upstream details"))));
        StepVerifier.create(ask("{\"question\":\"提问\"}"))
                .assertNext(event -> {
                    assertThat(event.event()).isEqualTo("delta");
                    assertThat(event.data()).isEqualTo("回答片段");
                })
                .assertNext(event -> assertError(event, "-1", "问答服务暂时不可用，请稍后重试"))
                .verifyComplete();
    }

    @Test
    void malformedJsonDoesNotExposeRequestContent() {
        StepVerifier.create(ask("{\"question\":\"private question\""))
                .assertNext(event -> assertError(event, "-1", "问答服务暂时不可用，请稍后重试"))
                .verifyComplete();
        verifyNoInteractions(chatAppService);
    }

    private Flux<ServerSentEvent<String>> ask(String body) {
        return client.post()
                .uri("/api/v1/kb/chat/ask")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(body)
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .returnResult(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .getResponseBody();
    }

    private void assertError(ServerSentEvent<String> event, String code, String message) {
        assertThat(event.event()).isEqualTo("error");
        try {
            var json = new ObjectMapper().readTree(event.data());
            assertThat(json.isObject()).isTrue();
            assertThat(json.size()).isEqualTo(2);
            assertThat(json.get("code").isTextual()).isTrue();
            assertThat(json.get("code").textValue()).isEqualTo(code);
            assertThat(json.get("message").isTextual()).isTrue();
            assertThat(json.get("message").textValue()).isEqualTo(message);
        } catch (JsonProcessingException error) {
            throw new AssertionError("error SSE data must be JSON", error);
        }
    }
}
